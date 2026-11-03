package com.pos.promotion;

import com.pos.customer.Customer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PromotionServiceTest {

    private final PromotionService service = new PromotionService(null, null, null, null);
    private final LocalDateTime now = LocalDateTime.of(2026, 11, 3, 12, 0);

    private static PromotionService.Line line(String category, long amount) {
        return new PromotionService.Line(category, BigDecimal.valueOf(amount));
    }

    private static Promotion percent(long value) {
        return Promotion.builder().id(1L).name("Giảm %").type(PromotionType.PERCENT)
                .value(BigDecimal.valueOf(value)).active(true).memberOnly(false).build();
    }

    @Test
    @DisplayName("Giảm % làm tròn xuống, có trần tiền giảm")
    void percentWithCap() {
        Promotion p = percent(10);
        List<PromotionService.Line> lines = List.of(line("Sữa", 36_005), line("Bánh kẹo", 24_000));
        assertEquals(new BigDecimal("6000"), PromotionService.discountFor(p, lines));
        p.setMaxDiscountAmount(BigDecimal.valueOf(5_000));
        assertEquals(new BigDecimal("5000"), PromotionService.discountFor(p, lines));
    }

    @Test
    @DisplayName("Giảm theo danh mục chỉ tính trên mặt hàng của danh mục, số tiền không vượt tiền hàng đó")
    void categoryScope() {
        Promotion p = Promotion.builder().type(PromotionType.FIXED_AMOUNT).value(BigDecimal.valueOf(50_000))
                .category(" sữa & bơ ").active(true).build();
        List<PromotionService.Line> lines = List.of(line("Sữa & Bơ", 36_000), line("Bánh kẹo", 24_000));
        assertEquals(new BigDecimal("36000"), PromotionService.discountFor(p, lines));
        assertEquals(BigDecimal.ZERO, PromotionService.discountFor(p, List.of(line("Bánh kẹo", 24_000))));
    }

    @Test
    @DisplayName("Điều kiện: bật/tắt, thời gian, chi nhánh, thành viên, điểm, đơn tối thiểu")
    void eligibility() {
        List<PromotionService.Line> lines = List.of(line("Sữa", 100_000));
        Customer member = Customer.builder().id(5L).points(120).build();

        Promotion p = percent(10);
        assertNull(service.ineligibleReason(p, 1L, null, lines, now));

        p.setActive(false);
        assertNotNull(service.ineligibleReason(p, 1L, null, lines, now));
        p.setActive(true);

        p.setStartAt(now.plusDays(1));
        assertEquals("chương trình chưa bắt đầu", service.ineligibleReason(p, 1L, null, lines, now));
        p.setStartAt(now.minusDays(1));
        p.setEndAt(now);
        assertEquals("chương trình đã kết thúc", service.ineligibleReason(p, 1L, null, lines, now));
        p.setEndAt(now.plusDays(1));
        assertNull(service.ineligibleReason(p, 1L, null, lines, now));

        p.setBranchId(2L);
        assertNotNull(service.ineligibleReason(p, 1L, null, lines, now));
        assertNull(service.ineligibleReason(p, 2L, null, lines, now));
        p.setBranchId(null);

        p.setMemberOnly(true);
        assertEquals("chỉ dành cho khách thành viên", service.ineligibleReason(p, 1L, null, lines, now));
        assertNull(service.ineligibleReason(p, 1L, member, lines, now));
        p.setMinCustomerPoints(200);
        assertNotNull(service.ineligibleReason(p, 1L, member, lines, now));
        p.setMinCustomerPoints(100);
        assertNull(service.ineligibleReason(p, 1L, member, lines, now));

        p.setMinOrderAmount(BigDecimal.valueOf(150_000));
        assertNotNull(service.ineligibleReason(p, 1L, member, lines, now));
    }
}
