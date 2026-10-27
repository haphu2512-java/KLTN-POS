package com.pos.forecast;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ForecastCalculatorTest {

    private final ForecastCalculator calc = new ForecastCalculator(ForecastCalculator.Config.defaults());

    @Test
    @DisplayName("SS = Z × σ × √L")
    void safetyStock() {
        assertEquals(1.65 * 4 * Math.sqrt(2), ForecastCalculator.safetyStock(4, 2, 1.65), 1e-9);
    }

    @Test
    @DisplayName("ROP = d̄ × L + SS")
    void reorderPoint() {
        assertEquals(29.33, ForecastCalculator.reorderPoint(10, 2, 9.33), 1e-9);
    }

    @Test
    @DisplayName("Q_order làm tròn lên, không âm và không bị sai số dấu phẩy động cộng thêm 1")
    void orderQuantity() {
        assertEquals(50, ForecastCalculator.orderQuantity(29.33, 10, 7, 50));
        assertEquals(0, ForecastCalculator.orderQuantity(20, 5, 7, 500));
        assertEquals(0, ForecastCalculator.orderQuantity(0.1 + 0.2, 0, 7, 0.3));
    }

    @Test
    @DisplayName("Mức khẩn cấp: hết hàng / dưới d̄×L / dưới ROP / bình thường")
    void classifyUrgency() {
        assertEquals("HIGH", ForecastCalculator.classifyUrgency(true, 0, 10, 2, 30));
        assertEquals("HIGH", ForecastCalculator.classifyUrgency(true, 15, 10, 2, 30));
        assertEquals("MEDIUM", ForecastCalculator.classifyUrgency(true, 25, 10, 2, 30));
        assertEquals("LOW", ForecastCalculator.classifyUrgency(true, 31, 10, 2, 30));
        assertEquals("LOW", ForecastCalculator.classifyUrgency(false, 10, 0, 2, 0));
    }

    @Test
    @DisplayName("σ tính cả ngày không bán (độ lệch chuẩn mẫu)")
    void stdDevCountsZeroDays() {
        assertEquals(3.0, ForecastCalculator.stdDev(List.of(0.0, 0.0, 6.0, 0.0)), 1e-9);
        assertEquals(0.0, ForecastCalculator.stdDev(List.of(5.0)));
    }

    @Test
    @DisplayName("Backtest: MAE/RMSE tính trên 7 ngày cuối mà mô hình chưa thấy")
    void backtestUsesHeldOutDays() {
        List<Double> sales = new java.util.ArrayList<>(Collections.nCopies(23, 10.0));
        sales.addAll(Collections.nCopies(7, 12.0));
        ForecastCalculator.Model model = calc.model(sales, "MOVING_AVG");
        // Dự báo từ 23 ngày đầu = 10, thực tế 7 ngày cuối = 12
        assertEquals(2.0, model.mae());
        assertEquals(2.0, model.rmse());
        assertEquals("GOOD", model.quality());
    }

    @Test
    @DisplayName("Lịch sử dưới 14 ngày: không đủ dữ liệu để đánh giá")
    void shortHistoryHasNoEvaluation() {
        ForecastCalculator.Model model = calc.model(Collections.nCopies(10, 5.0), "EXP_SMOOTH");
        assertNull(model.mae());
        assertEquals("INSUFFICIENT_DATA", model.quality());
    }
}
