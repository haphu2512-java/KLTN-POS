package com.pos.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Gọi Gemini để đoán ý câu hỏi khi bộ luật không hiểu (hỏi tự nhiên, sai chính tả, viết tắt...).
 * Chỉ gửi câu hỏi của người dùng, không gửi dữ liệu cửa hàng. Gemini trả về JSON gồm loại câu hỏi, số ngày,
 * từ khóa sản phẩm; backend vẫn tự truy vấn và tính số liệu như khi bộ luật hiểu câu hỏi.
 * Bật bằng ENABLE_AI_ASSISTANT=true và GEMINI_API_KEY; thiếu một trong hai thì lớp này không làm gì.
 */
@Slf4j
@Component
public class GeminiIntentClassifier implements LlmIntentClassifier {

    static final String SOURCE = "GEMINI";
    private static final String ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";

    static final String PROMPT = """
            Bạn phân loại câu hỏi của nhân viên một cửa hàng bán lẻ (hệ thống POS) vào đúng một loại:
            - REVENUE: doanh thu, lợi nhuận, số hóa đơn, bán được bao nhiêu tiền
            - TOP_PRODUCTS: mặt hàng bán chạy / bán nhiều nhất
            - LOW_STOCK: hàng sắp hết, tồn kho thấp, cần bổ sung
            - RESTOCK: nên nhập thêm gì, gợi ý nhập hàng, dự báo nhu cầu
            - STOCK_LOOKUP: tra tồn kho hoặc giá của một sản phẩm cụ thể (theo tên hoặc mã vạch)
            - HELP: hỏi trợ lý làm được gì
            - GREETING: chỉ chào hỏi
            - UNKNOWN: không thuộc các loại trên
            Trả về JSON: {"intent": <loại>, "days": <số ngày của khoảng thời gian, hôm nay = 1, tuần = 7, tháng = 30,
            không nói thì 0>, "yesterday": <true nếu hỏi riêng ngày hôm qua>, "keyword": <tên hoặc mã vạch sản phẩm
            giữ nguyên dấu, chỉ dùng cho STOCK_LOOKUP, còn lại để rỗng>}.
            Câu hỏi: """;

    private final boolean enabled;
    private final String apiKey;
    private final String model;
    private final Duration timeout;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public GeminiIntentClassifier(
            @Value("${ai.assistant.enabled:false}") boolean enabled,
            @Value("${ai.assistant.gemini-api-key:}") String apiKey,
            @Value("${ai.assistant.model:gemini-2.5-flash}") String model,
            @Value("${ai.assistant.timeout-ms:6000}") long timeoutMs,
            ObjectMapper objectMapper) {
        this.enabled = enabled;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null || model.isBlank() ? "gemini-2.5-flash" : model.trim();
        this.timeout = Duration.ofMillis(Math.max(1000, timeoutMs));
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(this.timeout).build();
        if (enabled && this.apiKey.isEmpty()) {
            log.warn("ENABLE_AI_ASSISTANT=true nhưng chưa có GEMINI_API_KEY, trợ lý chỉ chạy bằng luật");
        }
    }

    public boolean isActive() {
        return enabled && !apiKey.isEmpty();
    }

    @Override
    public String sourceName() {
        return SOURCE;
    }

    @Override
    public Optional<ParsedQuestion> classify(String message) {
        if (!isActive() || message == null || message.isBlank()) return Optional.empty();
        try {
            Map<String, Object> body = Map.of(
                    "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", PROMPT + message.trim())))),
                    "generationConfig", Map.of("temperature", 0, "responseMimeType", "application/json"));
            HttpRequest request = HttpRequest.newBuilder(URI.create(String.format(ENDPOINT, model)))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                log.warn("Gemini trả mã {}, bỏ qua và dùng câu trả lời theo luật", response.statusCode());
                return Optional.empty();
            }
            return parseResponse(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Không gọi được Gemini ({}), dùng câu trả lời theo luật", e.getMessage());
            return Optional.empty();
        }
    }

    /** Đọc phản hồi generateContent; chỉ nhận loại câu hỏi hợp lệ, giới hạn số ngày 1..90 */
    Optional<ParsedQuestion> parseResponse(String responseBody) {
        try {
            JsonNode text = objectMapper.readTree(responseBody)
                    .path("candidates").path(0).path("content").path("parts").path(0).path("text");
            if (!text.isTextual()) return Optional.empty();
            JsonNode json = objectMapper.readTree(stripCodeFence(text.asText()));

            ChatIntent intent;
            try {
                intent = ChatIntent.valueOf(json.path("intent").asText("").trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
            if (intent == ChatIntent.UNKNOWN) return Optional.empty();

            boolean yesterday = json.path("yesterday").asBoolean(false);
            int days = json.path("days").asInt(0);
            if (yesterday) days = 2;
            else if (days <= 0) days = intent == ChatIntent.REVENUE ? 1 : 7;
            days = Math.max(1, Math.min(90, days));

            String keyword = intent == ChatIntent.STOCK_LOOKUP ? json.path("keyword").asText("").trim() : "";
            if (keyword.length() > 100) keyword = keyword.substring(0, 100);
            if (intent == ChatIntent.STOCK_LOOKUP && keyword.isEmpty()) return Optional.empty();
            return Optional.of(new ParsedQuestion(intent, days, yesterday, keyword));
        } catch (Exception e) {
            log.warn("Phản hồi Gemini không đúng định dạng: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static String stripCodeFence(String s) {
        String t = s.trim();
        if (t.startsWith("```")) {
            t = t.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("\\s*```$", "");
        }
        return t;
    }
}
