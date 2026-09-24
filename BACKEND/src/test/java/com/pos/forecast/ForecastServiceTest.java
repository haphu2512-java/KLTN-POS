package com.pos.forecast;

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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
    @DisplayName("Đề xuất nhập hàng tự động: phát hiện thiếu hàng và gắn cờ HIGH urgency")
    void testGetDemandForecastAndReplenishment() {
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

        when(productRepository.findByActiveTrue()).thenReturn(List.of(product));
        when(invoiceRepository.getDailyProductSales(eq(1L), any(LocalDateTime.class)))
                .thenReturn(new ArrayList<>()); // fallback default sales
        when(inventoryRepository.findByProductIdAndBranchId(1L, 1L))
                .thenReturn(Optional.of(Inventory.builder().quantity(0).build())); // Tồn = 0

        List<ForecastResponse> responses = forecastService.getDemandForecastAndReplenishment(1L, 7, "EXP_SMOOTHING");

        assertNotNull(responses);
        assertEquals(1, responses.size());
        ForecastResponse resp = responses.get(0);
        assertEquals(1L, resp.getProductId());
        assertEquals("HIGH", resp.getUrgency(), "Tồn kho = 0 phải có mức độ ưu tiên HIGH");
        assertTrue(resp.getSuggestedRestockQty() > 0, "Số lượng đề xuất nhập hàng phải lớn hơn 0");
    }
}
