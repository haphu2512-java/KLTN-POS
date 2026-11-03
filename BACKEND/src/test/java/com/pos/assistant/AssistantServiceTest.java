package com.pos.assistant;

import com.pos.forecast.ForecastResponse;
import com.pos.forecast.ForecastService;
import com.pos.inventory.Inventory;
import com.pos.inventory.InventoryRepository;
import com.pos.product.Product;
import com.pos.product.ProductRepository;
import com.pos.report.ReportService;
import com.pos.report.RevenueSummaryResponse;
import com.pos.security.CurrentUserService;
import com.pos.user.Role;
import com.pos.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssistantServiceTest {

    @Mock private CurrentUserService currentUserService;
    @Mock private ReportService reportService;
    @Mock private ForecastService forecastService;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private ProductRepository productRepository;

    private AssistantService service;

    @BeforeEach
    void setUp() {
        service = new AssistantService(new IntentParser(), currentUserService, reportService, forecastService,
                inventoryRepository, productRepository);
    }

    private void loginAs(Role role) {
        User user = new User();
        user.setUsername("u");
        user.setRole(role);
        user.setCurrentBranchId(2L);
        when(currentUserService.getCurrentUser()).thenReturn(user);
    }

    @Test
    @DisplayName("Quản lý hỏi doanh thu hôm nay: lấy báo cáo của chi nhánh đang đăng nhập")
    void managerRevenue() {
        loginAs(Role.MANAGER);
        when(reportService.getRevenueSummary(2L, 1)).thenReturn(RevenueSummaryResponse.builder()
                .totalRevenue(BigDecimal.valueOf(1250000)).totalOrders(5)
                .averageOrderValue(BigDecimal.valueOf(250000)).grossProfit(BigDecimal.valueOf(300000))
                .profitMarginPercent(24.0).build());

        ChatResponse res = service.chat("doanh thu hôm nay");

        assertEquals("REVENUE", res.getIntent());
        assertEquals("RULE", res.getSource());
        assertTrue(res.getReply().contains("1.250.000 đ"), res.getReply());
        assertTrue(res.getReply().contains("Số hóa đơn: 5"));
    }

    @Test
    @DisplayName("Thu ngân hỏi doanh thu hay gợi ý nhập: từ chối, không đọc báo cáo")
    void cashierCannotSeeReports() {
        loginAs(Role.CASHIER);

        assertTrue(service.chat("doanh thu tuần này").getReply().contains("không có quyền"));
        assertTrue(service.chat("nên nhập thêm gì").getReply().contains("không có quyền"));
        assertFalse(service.chat("giúp tôi").getSuggestions().contains("Doanh thu hôm nay"));
        verifyNoInteractions(reportService, forecastService);
    }

    @Test
    @DisplayName("Tra tồn kho theo tên: trả số lượng tại chi nhánh và giá bán")
    void stockLookup() {
        loginAs(Role.CASHIER);
        Product p = Product.builder().id(9L).name("Mì Hảo Hảo tôm chua cay").unit("Gói")
                .salePrice(BigDecimal.valueOf(4500)).barcode("8934563138165").active(true).build();
        when(productRepository.searchProducts("Mì Hảo Hảo")).thenReturn(List.of(p));
        when(inventoryRepository.findByProductIdAndBranchId(9L, 2L))
                .thenReturn(Optional.of(Inventory.builder().quantity(42).build()));

        String reply = service.chat("Mì Hảo Hảo còn bao nhiêu?").getReply();

        assertTrue(reply.contains("còn 42 Gói"), reply);
        assertTrue(reply.contains("4.500 đ"), reply);
    }

    @Test
    @DisplayName("Hàng sắp hết: liệt kê theo số lượng tăng dần, bỏ sản phẩm ngừng bán")
    void lowStock() {
        loginAs(Role.WAREHOUSE);
        Product a = Product.builder().name("Nước suối").unit("Chai").active(true).build();
        Product b = Product.builder().name("Bánh mì").unit("Cái").active(true).build();
        Product off = Product.builder().name("Ngừng bán").active(false).build();
        when(inventoryRepository.findLowStockByBranchId(2L)).thenReturn(List.of(
                Inventory.builder().product(a).quantity(4).minThreshold(5).build(),
                Inventory.builder().product(b).quantity(1).minThreshold(5).build(),
                Inventory.builder().product(off).quantity(0).minThreshold(5).build()));

        String reply = service.chat("hàng nào sắp hết").getReply();

        assertTrue(reply.startsWith("Có 2 sản phẩm"), reply);
        assertTrue(reply.indexOf("Bánh mì") < reply.indexOf("Nước suối"));
        assertFalse(reply.contains("Ngừng bán"));
    }

    @Test
    @DisplayName("Thủ kho hỏi gợi ý nhập: chỉ liệt kê sản phẩm có số lượng đề xuất > 0")
    void restock() {
        loginAs(Role.WAREHOUSE);
        when(forecastService.getCachedOrComputeForecast(eq(2L), eq(7), eq("EXP_SMOOTH"))).thenReturn(List.of(
                ForecastResponse.builder().productName("Sữa chua").unit("Hộp").currentStock(3)
                        .suggestedRestockQty(20).urgencyLabel("Khẩn cấp").build(),
                ForecastResponse.builder().productName("Kẹo").suggestedRestockQty(0).build()));

        String reply = service.chat("Nên nhập thêm gì?").getReply();

        assertTrue(reply.contains("Sữa chua: nhập 20 Hộp"), reply);
        assertFalse(reply.contains("Kẹo"));
    }

    @Test
    @DisplayName("Câu hỏi lạ: trả hướng dẫn, không truy vấn dữ liệu")
    void unknown() {
        loginAs(Role.MANAGER);
        ChatResponse res = service.chat("thời tiết ngày mai");
        assertEquals("UNKNOWN", res.getIntent());
        assertTrue(res.getReply().contains("Bạn có thể hỏi"));
        verifyNoInteractions(reportService, forecastService, inventoryRepository, productRepository);
    }
}
