package com.pos.forecast;

import com.pos.branch.BranchRepository;
import com.pos.inventory.Inventory;
import com.pos.inventory.InventoryRepository;
import com.pos.product.Product;
import com.pos.product.ProductRepository;
import com.pos.sale.InvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForecastServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private ForecastResultRepository forecastResultRepository;

    @InjectMocks
    private ForecastService forecastService;

    private List<Double> sampleSales;

    @BeforeEach
    void setUp() {
        sampleSales = List.of(10.0, 12.0, 14.0, 11.0, 13.0, 15.0, 16.0);
    }

    @Test
    @DisplayName("Moving Average: tính toán trung bình trượt 7 ngày chính xác")
    void testPredictNextDemandMovingAverage() {
        // Sum = 10+12+14+11+13+15+16 = 91. Avg = 91/7 = 13.0
        double result = forecastService.predictNextDemandMovingAverage(sampleSales, 7);
        assertEquals(13.0, result, 0.05);
    }

    @Test
    @DisplayName("Moving Average: trả về 0 nếu danh sách rỗng")
    void testPredictMovingAverageEmpty() {
        double result = forecastService.predictNextDemandMovingAverage(new ArrayList<>(), 7);
        assertEquals(0.0, result);
    }

    @Test
    @DisplayName("Exponential Smoothing: dự báo với trọng số alpha = 0.35")
    void testPredictNextDemandExponentialSmoothing() {
        double result = forecastService.predictNextDemandExponentialSmoothing(sampleSales, 0.35);
        assertTrue(result > 10.0 && result < 17.0, "Kết quả dự báo phải nằm trong khoảng hợp lý");
    }

    @Test
    @DisplayName("Đề xuất nhập hàng tự động: phát hiện thiếu hàng khi có lịch sử bán")
    void testGetDemandForecastAndReplenishmentWithHistory() {
        Product product = Product.builder()
                .id(1L)
                .barcode("8934567890123")
                .name("Sữa tươi Vinamilk")
                .category("Đồ uống")
                .unit("Hộp")
                .costPrice(BigDecimal.valueOf(25000))
                .salePrice(BigDecimal.valueOf(36000))
                .active(true)
                .build();

        LocalDate today = LocalDate.now();
        when(productRepository.findByActiveTrue()).thenReturn(List.of(product));
        when(invoiceRepository.getBranchDailyProductSales(eq(1L), any(LocalDateTime.class)))
                .thenReturn(List.of(
                        new Object[]{1L, java.sql.Date.valueOf(today.minusDays(1)), 5L},
                        new Object[]{1L, java.sql.Date.valueOf(today), 7L}));
        when(inventoryRepository.findByBranchId(1L))
                .thenReturn(List.of(Inventory.builder().product(product).quantity(0).build())); // Tồn = 0

        List<ForecastResponse> responses = forecastService.getDemandForecastAndReplenishment(1L, 7, "EXP_SMOOTHING");

        assertNotNull(responses);
        assertEquals(1, responses.size());
        ForecastResponse resp = responses.get(0);
        assertEquals(1L, resp.getProductId());
        assertTrue(resp.getHasHistory());
        assertEquals("HIGH", resp.getUrgency(), "Tồn kho = 0 phải có mức độ ưu tiên HIGH");
        assertTrue(resp.getSuggestedRestockQty() > 0, "Số lượng đề xuất nhập hàng phải lớn hơn 0");
    }

    @Test
    @DisplayName("Ngày không bán được tính là 0: chuỗi lịch sử đủ 30 ngày, trung bình không bị thổi phồng")
    void testZeroSalesDaysAreFilled() {
        Product product = Product.builder().id(3L).name("Nước mắm").active(true).build();

        LocalDate today = LocalDate.now();
        when(productRepository.findByActiveTrue()).thenReturn(List.of(product));
        // Chỉ bán 2 ngày trong 30 ngày, mỗi ngày 7 chai
        when(invoiceRepository.getBranchDailyProductSales(eq(1L), any(LocalDateTime.class)))
                .thenReturn(List.of(
                        new Object[]{3L, java.sql.Date.valueOf(today.minusDays(10)), 7L},
                        new Object[]{3L, java.sql.Date.valueOf(today.minusDays(3)), 7L}));
        when(inventoryRepository.findByBranchId(1L)).thenReturn(List.of());

        ForecastResponse resp = forecastService.getDemandForecastAndReplenishment(1L, 7, "MOVING_AVG").get(0);

        assertEquals(ForecastService.HISTORY_DAYS, resp.getHistoricalDailySales().size());
        assertEquals(14.0, resp.getHistoricalDailySales().stream().mapToDouble(Double::doubleValue).sum());
        // Trung bình 7 ngày gần nhất = 7 / 7 = 1.0 (trước đây chỉ tính ngày có bán nên ra 7.0)
        assertEquals(1.0, resp.getDailyDemandForecast(), 0.01);
    }

    @Test
    @DisplayName("Chuỗi lịch sử kết thúc ở hôm qua: hóa đơn hôm nay (chưa hết ngày) không được tính")
    void testTodayExcludedFromHistory() {
        Product product = Product.builder().id(4L).name("Bánh quy").active(true).build();

        LocalDate today = LocalDate.now();
        when(productRepository.findByActiveTrue()).thenReturn(List.of(product));
        when(invoiceRepository.getBranchDailyProductSales(eq(1L), any(LocalDateTime.class)))
                .thenReturn(List.of(
                        new Object[]{4L, java.sql.Date.valueOf(today.minusDays(1)), 6L},
                        new Object[]{4L, java.sql.Date.valueOf(today), 1L}));
        when(inventoryRepository.findByBranchId(1L)).thenReturn(List.of());

        ForecastResponse resp = forecastService.getDemandForecastAndReplenishment(1L, 7, "MOVING_AVG").get(0);

        List<Double> history = resp.getHistoricalDailySales();
        assertEquals(ForecastService.HISTORY_DAYS, history.size());
        assertEquals(6.0, history.get(history.size() - 1), "Ngày cuối của chuỗi phải là hôm qua");
        assertEquals(6.0, history.stream().mapToDouble(Double::doubleValue).sum(), "Không cộng hóa đơn hôm nay");
    }

    @Test
    @DisplayName("Sản phẩm mới chưa có lịch sử bán: không gán dữ liệu giả, restock = 0")
    void testGetDemandForecastSparseHistory() {
        Product product = Product.builder()
                .id(2L)
                .barcode("8939999999999")
                .name("Bánh mì que mới")
                .category("Bánh")
                .unit("Cái")
                .costPrice(BigDecimal.valueOf(5000))
                .salePrice(BigDecimal.valueOf(10000))
                .active(true)
                .build();

        when(productRepository.findByActiveTrue()).thenReturn(List.of(product));
        when(invoiceRepository.getBranchDailyProductSales(eq(1L), any(LocalDateTime.class)))
                .thenReturn(new ArrayList<>()); // Không có lịch sử bán
        when(inventoryRepository.findByBranchId(1L))
                .thenReturn(List.of(Inventory.builder().product(product).quantity(10).build()));

        List<ForecastResponse> responses = forecastService.getDemandForecastAndReplenishment(1L, 7, "EXP_SMOOTHING");

        assertNotNull(responses);
        assertEquals(1, responses.size());
        ForecastResponse resp = responses.get(0);
        assertFalse(resp.getHasHistory());
        assertEquals(0, resp.getSuggestedRestockQty());
        assertEquals(0.0, resp.getDailyDemandForecast());
    }

    @Test
    @DisplayName("Cache ban đêm khác thuật toán thì bỏ qua và tính realtime")
    void testCacheIgnoredWhenAlgorithmDiffers() {
        Product product = Product.builder().id(4L).name("Mì gói").active(true).build();
        ForecastResult cachedRow = ForecastResult.builder()
                .product(product).algorithm("EXP_SMOOTH").forecastDays(7)
                .dailyDemandForecast(99.0).totalPredictedDemand(700).hasHistory(true).build();

        when(forecastResultRepository.findByBranchIdOrderByUrgencyAscProductNameAsc(1L))
                .thenReturn(List.of(cachedRow));
        when(productRepository.findByActiveTrue()).thenReturn(List.of(product));
        when(invoiceRepository.getBranchDailyProductSales(eq(1L), any(LocalDateTime.class))).thenReturn(List.of());
        when(inventoryRepository.findByBranchId(1L)).thenReturn(List.of());

        ForecastResponse resp = forecastService.getCachedOrComputeForecast(1L, 7, "MOVING_AVG").get(0);

        assertEquals(0.0, resp.getDailyDemandForecast(), "Không được trả kết quả cache của thuật toán khác");
    }

    @Test
    @DisplayName("Cache ban đêm cùng thuật toán: dùng cache nhưng tồn kho lấy theo thời gian thực")
    void testCacheUsesLiveStock() {
        Product product = Product.builder().id(5L).name("Dầu ăn").active(true).build();
        // d̄ = 2, σ = 0 → SS = 0, ROP = 2 × 2 = 4
        ForecastResult cachedRow = ForecastResult.builder()
                .product(product).algorithm("EXP_SMOOTH").forecastDays(7)
                .dailyDemandForecast(2.0).totalPredictedDemand(14).currentStock(0)
                .stdDev(0.0).safetyStock(0.0).reorderPoint(4.0)
                .suggestedRestockQty(18).hasHistory(true).build();

        when(forecastResultRepository.findByBranchIdOrderByUrgencyAscProductNameAsc(1L))
                .thenReturn(List.of(cachedRow));
        when(inventoryRepository.findByBranchId(1L))
                .thenReturn(List.of(Inventory.builder().product(product).quantity(10).build()));

        ForecastResponse resp = forecastService.getCachedOrComputeForecast(1L, 7, "EXP_SMOOTHING").get(0);

        assertEquals(10, resp.getCurrentStock());
        // Q = ⌈ROP + d̄ × T − tồn⌉ = ⌈4 + 14 − 10⌉ = 8
        assertEquals(8, resp.getSuggestedRestockQty());
        assertEquals("LOW", resp.getUrgency());
        assertEquals(4.0, resp.getReorderPoint());
        assertNotNull(resp.getReason());
    }

    @Test
    @DisplayName("Cache cũ chưa có cột ROP thì bỏ qua và tính realtime")
    void testOldCacheWithoutReorderPointIsRecomputed() {
        Product product = Product.builder().id(6L).name("Đường").active(true).build();
        ForecastResult oldRow = ForecastResult.builder()
                .product(product).algorithm("EXP_SMOOTH").forecastDays(7)
                .dailyDemandForecast(50.0).totalPredictedDemand(350).hasHistory(true).build();

        when(forecastResultRepository.findByBranchIdOrderByUrgencyAscProductNameAsc(1L))
                .thenReturn(List.of(oldRow));
        when(productRepository.findByActiveTrue()).thenReturn(List.of(product));
        when(invoiceRepository.getBranchDailyProductSales(eq(1L), any(LocalDateTime.class))).thenReturn(List.of());
        when(inventoryRepository.findByBranchId(1L)).thenReturn(List.of());

        ForecastResponse resp = forecastService.getCachedOrComputeForecast(1L, 7, "EXP_SMOOTH").get(0);

        assertEquals(0.0, resp.getDailyDemandForecast());
    }

    @Test
    @DisplayName("Dự báo 1 sản phẩm: trả chuỗi lịch sử đủ số ngày và các chỉ số SS, ROP, MAE")
    void testGetProductForecast() {
        Product product = Product.builder().id(7L).name("Mì gói").active(true).build();
        LocalDate today = LocalDate.now();
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < ForecastService.HISTORY_DAYS; i++) {
            rows.add(new Object[]{7L, java.sql.Date.valueOf(today.minusDays(i + 1)), 10L});
        }
        when(productRepository.findById(7L)).thenReturn(java.util.Optional.of(product));
        when(invoiceRepository.getBranchDailyProductSales(eq(1L), any(LocalDateTime.class))).thenReturn(rows);
        when(inventoryRepository.findByBranchId(1L))
                .thenReturn(List.of(Inventory.builder().product(product).quantity(20).build()));

        ForecastResponse resp = forecastService.getProductForecast(1L, 7L, 7, "EXP_SMOOTH");

        assertEquals(ForecastService.HISTORY_DAYS, resp.getHistoricalDailySales().size());
        assertEquals(10.0, resp.getDailyDemandForecast(), 0.001);
        assertEquals(0.0, resp.getSafetyStock());
        assertEquals(20.0, resp.getReorderPoint(), 0.001);
        assertEquals(70, resp.getSuggestedRestockQty());
        assertEquals("HIGH", resp.getUrgency());
        assertEquals("Khẩn cấp", resp.getUrgencyLabel());
        assertEquals(0.0, resp.getMae());
        assertEquals("GOOD", resp.getQuality());
    }

    @Test
    @DisplayName("Dự báo 1 sản phẩm không tồn tại: lỗi 404")
    void testGetProductForecastNotFound() {
        when(productRepository.findById(99L)).thenReturn(java.util.Optional.empty());

        com.pos.common.BusinessException ex = assertThrows(com.pos.common.BusinessException.class,
                () -> forecastService.getProductForecast(1L, 99L, 7, "EXP_SMOOTH"));
        assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, ex.getStatus());
    }
}
