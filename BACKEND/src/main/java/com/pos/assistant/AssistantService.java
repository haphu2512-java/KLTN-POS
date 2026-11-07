package com.pos.assistant;

import com.pos.forecast.ForecastResponse;
import com.pos.forecast.ForecastService;
import com.pos.inventory.Inventory;
import com.pos.inventory.InventoryRepository;
import com.pos.product.Product;
import com.pos.product.ProductRepository;
import com.pos.report.ReportService;
import com.pos.report.RevenueSummaryResponse;
import com.pos.report.TopProductResponse;
import com.pos.security.CurrentUserService;
import com.pos.user.Role;
import com.pos.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Trợ lý hỏi đáp cho nhân viên cửa hàng. Chỉ dùng luật (từ khóa) và dữ liệu sẵn có, không gọi LLM.
 * Phân quyền giống các API gốc: doanh thu / bán chạy cho ADMIN, MANAGER; gợi ý nhập cho ADMIN, MANAGER;
 * tồn kho và hàng sắp hết cho mọi vai trò. Luôn trả lời theo chi nhánh của phiên đăng nhập.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssistantService {

    static final String SOURCE_RULE = "RULE";
    private static final int MAX_ROWS = 5;

    private static final Set<Role> REPORT_ROLES = Set.of(Role.ADMIN, Role.MANAGER);
    private static final Set<Role> FORECAST_ROLES = Set.of(Role.ADMIN, Role.MANAGER);

    private final IntentParser intentParser;
    private final CurrentUserService currentUserService;
    private final ReportService reportService;
    private final ForecastService forecastService;
    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;

    public ChatResponse chat(String message) {
        User user = currentUserService.getCurrentUser();
        ParsedQuestion q = intentParser.parse(message);
        Long branchId = user.getCurrentBranchId();

        String reply = switch (q.intent()) {
            case GREETING -> "Chào " + displayName(user) + "! Mình là trợ lý của cửa hàng. "
                    + "Bạn có thể hỏi về tồn kho, hàng sắp hết" + (canReport(user) ? ", doanh thu, hàng bán chạy" : "")
                    + (canForecast(user) ? ", gợi ý nhập hàng" : "") + ".";
            case HELP -> helpText(user);
            case UNKNOWN -> "Mình chưa hiểu câu hỏi này. Mình chỉ trả lời được các câu hỏi về số liệu cửa hàng.\n"
                    + helpText(user);
            default -> branchId == null
                    ? "Phiên đăng nhập chưa chọn chi nhánh, hãy đăng nhập lại và chọn chi nhánh."
                    : answerData(q, user, branchId);
        };

        return ChatResponse.builder()
                .reply(reply)
                .intent(q.intent().name())
                .source(SOURCE_RULE)
                .suggestions(suggestionsFor(user))
                .build();
    }

    private String answerData(ParsedQuestion q, User user, Long branchId) {
        return switch (q.intent()) {
            case REVENUE -> canReport(user) ? revenue(q, branchId) : noPermission("doanh thu");
            case TOP_PRODUCTS -> canReport(user) ? topProducts(q, branchId) : noPermission("hàng bán chạy");
            case RESTOCK -> canForecast(user) ? restock(branchId) : noPermission("gợi ý nhập hàng");
            case LOW_STOCK -> lowStock(branchId);
            case STOCK_LOOKUP -> stockLookup(q.keyword(), branchId);
            default -> helpText(user);
        };
    }

    private String revenue(ParsedQuestion q, Long branchId) {
        RevenueSummaryResponse s = reportService.getRevenueSummary(branchId, q.days());
        if (q.yesterday()) {
            List<RevenueSummaryResponse.DailyRevenueItem> trend = s.getDailyTrend();
            RevenueSummaryResponse.DailyRevenueItem day = trend == null || trend.isEmpty() ? null : trend.get(0);
            if (day == null || day.getOrderCount() == 0) {
                return "Hôm qua chi nhánh chưa có hóa đơn nào.";
            }
            return "Doanh thu hôm qua (" + day.getDate() + "): " + money(day.getRevenue())
                    + "\nSố hóa đơn: " + day.getOrderCount()
                    + "\nLợi nhuận gộp: " + money(day.getProfit());
        }
        String period = periodLabel(q.days());
        if (s.getTotalOrders() == 0) {
            return "Chi nhánh chưa có hóa đơn nào " + period + ".";
        }
        return "Doanh thu " + period + ": " + money(s.getTotalRevenue())
                + "\nSố hóa đơn: " + s.getTotalOrders()
                + "\nGiá trị trung bình mỗi đơn: " + money(s.getAverageOrderValue())
                + "\nLợi nhuận gộp: " + money(s.getGrossProfit()) + " (biên " + s.getProfitMarginPercent() + "%)";
    }

    private String topProducts(ParsedQuestion q, Long branchId) {
        List<TopProductResponse> top = reportService.getTopSellingProducts(branchId, q.days(), MAX_ROWS);
        String period = periodLabel(q.days());
        if (top.isEmpty()) {
            return "Chưa bán được sản phẩm nào " + period + ".";
        }
        StringBuilder sb = new StringBuilder("Top " + top.size() + " sản phẩm bán chạy " + period + ":");
        int i = 1;
        for (TopProductResponse p : top) {
            sb.append("\n").append(i++).append(". ").append(p.getProductName())
                    .append(": ").append(p.getQuantitySold()).append(" sp, ").append(money(p.getTotalRevenue()));
        }
        return sb.toString();
    }

    private String lowStock(Long branchId) {
        List<Inventory> low = new ArrayList<>(inventoryRepository.findLowStockByBranchId(branchId));
        low.removeIf(inv -> inv.getProduct() == null || Boolean.FALSE.equals(inv.getProduct().getActive()));
        if (low.isEmpty()) {
            return "Không có sản phẩm nào dưới mức tồn tối thiểu. Kho đang ổn.";
        }
        low.sort(Comparator.comparingInt(inv -> qty(inv.getQuantity())));
        StringBuilder sb = new StringBuilder("Có " + low.size() + " sản phẩm dưới mức tồn tối thiểu");
        sb.append(low.size() > MAX_ROWS ? ", " + MAX_ROWS + " sản phẩm ít nhất:" : ":");
        low.stream().limit(MAX_ROWS).forEach(inv -> sb.append("\n- ").append(inv.getProduct().getName())
                .append(": còn ").append(qty(inv.getQuantity())).append(" ").append(unit(inv.getProduct()))
                .append(" (tối thiểu ").append(qty(inv.getMinThreshold())).append(")"));
        if (low.size() > MAX_ROWS) {
            sb.append("\nXem đủ danh sách ở trang Kho hàng.");
        }
        return sb.toString();
    }

    private String restock(Long branchId) {
        List<ForecastResponse> need = forecastService.getCachedOrComputeForecast(branchId, 7, "EXP_SMOOTH").stream()
                .filter(r -> r.getSuggestedRestockQty() != null && r.getSuggestedRestockQty() > 0)
                .toList();
        if (need.isEmpty()) {
            return "Theo dự báo 7 ngày tới, chưa có sản phẩm nào cần nhập thêm.";
        }
        StringBuilder sb = new StringBuilder("Theo dự báo 7 ngày tới, có " + need.size() + " sản phẩm nên nhập thêm");
        sb.append(need.size() > MAX_ROWS ? ", ưu tiên:" : ":");
        need.stream().limit(MAX_ROWS).forEach(r -> sb.append("\n- ").append(r.getProductName())
                .append(": nhập ").append(r.getSuggestedRestockQty()).append(" ").append(r.getUnit() != null ? r.getUnit() : "")
                .append(" (còn ").append(qty(r.getCurrentStock()))
                .append(r.getUrgencyLabel() != null ? ", " + r.getUrgencyLabel().toLowerCase(Locale.ROOT) : "")
                .append(")"));
        sb.append("\nXem chi tiết ở trang Dự báo.");
        return sb.toString();
    }

    private String stockLookup(String keyword, Long branchId) {
        if (keyword == null || keyword.isBlank()) {
            return "Bạn muốn tra sản phẩm nào? Hãy gõ tên hoặc mã vạch, ví dụ: \"Sữa Vinamilk còn bao nhiêu\".";
        }
        List<Product> matches;
        Optional<Product> byBarcode = keyword.matches("\\d{8,14}") ? productRepository.findByBarcode(keyword) : Optional.empty();
        if (byBarcode.isPresent()) {
            matches = byBarcode.filter(p -> !Boolean.FALSE.equals(p.getActive())).map(List::of).orElse(List.of());
        } else {
            matches = productRepository.searchProducts(keyword);
        }
        if (matches.isEmpty()) {
            return "Không tìm thấy sản phẩm nào khớp \"" + keyword + "\".";
        }
        StringBuilder sb = new StringBuilder();
        if (matches.size() > 1) {
            sb.append("Tìm thấy ").append(matches.size()).append(" sản phẩm khớp \"").append(keyword).append("\"");
            sb.append(matches.size() > MAX_ROWS ? ", " + MAX_ROWS + " sản phẩm đầu:" : ":");
        }
        matches.stream().limit(MAX_ROWS).forEach(p -> {
            int stock = inventoryRepository.findByProductIdAndBranchId(p.getId(), branchId)
                    .map(inv -> qty(inv.getQuantity())).orElse(0);
            if (!sb.isEmpty()) sb.append("\n- ");
            sb.append(p.getName()).append(": còn ").append(stock).append(" ").append(unit(p))
                    .append(", giá ").append(money(p.getSalePrice()));
            if (p.getBarcode() != null) sb.append(" (mã ").append(p.getBarcode()).append(")");
        });
        if (matches.size() > MAX_ROWS) {
            sb.append("\nGõ tên cụ thể hơn để thu hẹp kết quả.");
        }
        return sb.toString();
    }

    private String helpText(User user) {
        StringBuilder sb = new StringBuilder("Bạn có thể hỏi:");
        sb.append("\n- Tồn kho, giá một sản phẩm: \"Mì Hảo Hảo còn bao nhiêu?\" hoặc gõ mã vạch");
        sb.append("\n- Hàng sắp hết: \"Hàng nào sắp hết?\"");
        if (canReport(user)) {
            sb.append("\n- Doanh thu: \"Doanh thu hôm nay\", \"doanh thu hôm qua\", \"doanh thu 7 ngày\"");
            sb.append("\n- Hàng bán chạy: \"Top bán chạy tuần này\"");
        }
        if (canForecast(user)) {
            sb.append("\n- Gợi ý nhập hàng: \"Nên nhập thêm gì?\"");
        }
        return sb.toString();
    }

    private List<String> suggestionsFor(User user) {
        List<String> s = new ArrayList<>();
        if (canReport(user)) s.add("Doanh thu hôm nay");
        s.add("Hàng nào sắp hết?");
        if (canForecast(user)) s.add("Nên nhập thêm gì?");
        if (canReport(user)) s.add("Top bán chạy tuần này");
        return s;
    }

    private static String noPermission(String what) {
        return "Tài khoản của bạn không có quyền xem " + what + ". Hãy hỏi quản lý cửa hàng.";
    }

    private static boolean canReport(User user) {
        return REPORT_ROLES.contains(user.getRole());
    }

    private static boolean canForecast(User user) {
        return FORECAST_ROLES.contains(user.getRole());
    }

    private static String displayName(User user) {
        return user.getFullName() != null && !user.getFullName().isBlank() ? user.getFullName() : user.getUsername();
    }

    static String periodLabel(int days) {
        if (days <= 1) return "hôm nay";
        return days + " ngày gần đây";
    }

    static String money(BigDecimal value) {
        NumberFormat nf = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN"));
        return nf.format(value != null ? value : BigDecimal.ZERO) + " đ";
    }

    private static int qty(Integer value) {
        return value != null ? value : 0;
    }

    private static String unit(Product p) {
        return p.getUnit() != null ? p.getUnit() : "";
    }
}
