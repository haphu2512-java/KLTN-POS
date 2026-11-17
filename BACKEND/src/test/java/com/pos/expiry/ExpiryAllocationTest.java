package com.pos.expiry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExpiryAllocationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);

    private static Object[] lot(String no, LocalDate expiry, int qty, long stockInId) {
        return new Object[]{1L, "Sữa chua", "893", no, expiry, qty, new BigDecimal("5000"), stockInId,
                LocalDateTime.of(2026, 10, 1, 8, 0).plusDays(stockInId)};
    }

    @Test
    @DisplayName("FEFO: tồn hiện tại thuộc các lô hạn xa nhất, lô hạn gần đã bán hết trước")
    void stockGoesToLatestExpiryFirst() {
        List<ExpiryService.LotView> lots = ExpiryService.allocate(List.of(
                lot("A", TODAY.minusDays(2), 10, 1),
                lot("B", TODAY.plusDays(5), 10, 2),
                lot("C", TODAY.plusDays(60), 10, 3)), 14, TODAY, 30);

        assertThat(lots).extracting(ExpiryService.LotView::lotNumber).containsExactly("C", "B", "A");
        assertThat(lots).extracting(ExpiryService.LotView::remainingQty).containsExactly(10, 4, 0);
        assertThat(lots).extracting(ExpiryService.LotView::status).containsExactly(
                ExpiryService.LotStatus.OK, ExpiryService.LotStatus.NEAR_EXPIRY, ExpiryService.LotStatus.EXPIRED);
        assertThat(lots.get(1).value()).isEqualByComparingTo("20000");
        assertThat(lots.get(1).daysLeft()).isEqualTo(5);
    }

    @Test
    @DisplayName("Tồn nhiều hơn tổng các lô có hạn: lô nào cũng còn đủ, phần dư là hàng không khai hạn")
    void stockBeyondTrackedLots() {
        List<ExpiryService.LotView> lots = ExpiryService.allocate(List.of(
                lot("A", TODAY.minusDays(1), 3, 1), lot("B", TODAY, 2, 2)), 50, TODAY, 0);
        assertThat(lots).extracting(ExpiryService.LotView::remainingQty).containsExactly(2, 3);
        // Hạn đúng hôm nay vẫn còn dùng được (cận date), qua hôm nay mới là hết hạn
        assertThat(lots.get(0).status()).isEqualTo(ExpiryService.LotStatus.NEAR_EXPIRY);
        assertThat(lots.get(1).status()).isEqualTo(ExpiryService.LotStatus.EXPIRED);
    }

    @Test
    @DisplayName("Hết hàng thì không lô nào còn")
    void noStockNoRemaining() {
        assertThat(ExpiryService.allocate(List.<Object[]>of(lot("A", TODAY.plusDays(3), 5, 1)), 0, TODAY, 30))
                .extracting(ExpiryService.LotView::remainingQty).containsExactly(0);
    }
}
