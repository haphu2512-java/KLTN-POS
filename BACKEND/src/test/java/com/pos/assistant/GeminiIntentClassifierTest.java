package com.pos.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeminiIntentClassifierTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private GeminiIntentClassifier classifier(boolean enabled, String key) {
        return new GeminiIntentClassifier(enabled, key, "gemini-2.5-flash", 3000, mapper);
    }

    private String wrap(String json) throws Exception {
        return "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":" + mapper.writeValueAsString(json) + "}]}}]}";
    }

    @Test
    @DisplayName("Tắt hoặc thiếu key thì không gọi mạng, trả rỗng")
    void inactive() {
        assertFalse(classifier(false, "k").isActive());
        assertFalse(classifier(true, " ").isActive());
        assertTrue(classifier(true, " ").classify("doanh thu").isEmpty());
    }

    @Test
    @DisplayName("Đọc được loại câu hỏi, số ngày và từ khóa sản phẩm")
    void parseStockLookup() throws Exception {
        Optional<ParsedQuestion> q = classifier(true, "k").parseResponse(
                wrap("{\"intent\":\"STOCK_LOOKUP\",\"days\":0,\"yesterday\":false,\"keyword\":\"Sữa Vinamilk\"}"));
        assertEquals(new ParsedQuestion(ChatIntent.STOCK_LOOKUP, 7, false, "Sữa Vinamilk"), q.orElseThrow());
    }

    @Test
    @DisplayName("Hôm qua thành 2 ngày, số ngày bị giới hạn 1..90, bỏ khối ```json")
    void parseDays() throws Exception {
        GeminiIntentClassifier c = classifier(true, "k");
        assertEquals(2, c.parseResponse(wrap("{\"intent\":\"REVENUE\",\"yesterday\":true}")).orElseThrow().days());
        assertEquals(90, c.parseResponse(wrap("```json\n{\"intent\":\"TOP_PRODUCTS\",\"days\":400}\n```"))
                .orElseThrow().days());
        assertEquals(1, c.parseResponse(wrap("{\"intent\":\"revenue\"}")).orElseThrow().days());
    }

    @Test
    @DisplayName("Loại lạ, UNKNOWN, tra cứu thiếu từ khóa hoặc phản hồi hỏng thì trả rỗng")
    void parseInvalid() throws Exception {
        GeminiIntentClassifier c = classifier(true, "k");
        assertTrue(c.parseResponse(wrap("{\"intent\":\"DELETE_ALL\"}")).isEmpty());
        assertTrue(c.parseResponse(wrap("{\"intent\":\"UNKNOWN\"}")).isEmpty());
        assertTrue(c.parseResponse(wrap("{\"intent\":\"STOCK_LOOKUP\",\"keyword\":\"\"}")).isEmpty());
        assertTrue(c.parseResponse("{\"error\":{}}").isEmpty());
        assertTrue(c.parseResponse(wrap("khong phai json")).isEmpty());
    }
}
