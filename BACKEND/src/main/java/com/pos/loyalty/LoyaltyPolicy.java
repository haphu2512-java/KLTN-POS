package com.pos.loyalty;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Quy tắc tích và đổi điểm, đọc từ cấu hình (pos.loyalty.*).
 * Mặc định: mỗi 10.000đ thanh toán được 1 điểm; 1 điểm đổi được 1.000đ;
 * mỗi hóa đơn dùng điểm trả tối đa 50% số tiền sau giảm giá; mỗi lần đổi tối thiểu 10 điểm.
 */
@Component
public class LoyaltyPolicy {

    private final BigDecimal earnPerAmount;
    private final BigDecimal pointValue;
    private final BigDecimal redeemMaxPercent;
    private final int minRedeemPoints;

    public LoyaltyPolicy(@Value("${pos.loyalty.earn-per-amount:10000}") BigDecimal earnPerAmount,
                         @Value("${pos.loyalty.point-value:1000}") BigDecimal pointValue,
                         @Value("${pos.loyalty.redeem-max-percent:50}") BigDecimal redeemMaxPercent,
                         @Value("${pos.loyalty.min-redeem-points:10}") int minRedeemPoints) {
        if (earnPerAmount.signum() <= 0 || pointValue.signum() <= 0) {
            throw new IllegalArgumentException("pos.loyalty.earn-per-amount và pos.loyalty.point-value phải lớn hơn 0");
        }
        this.earnPerAmount = earnPerAmount;
        this.pointValue = pointValue;
        this.redeemMaxPercent = redeemMaxPercent.max(BigDecimal.ZERO).min(BigDecimal.valueOf(100));
        this.minRedeemPoints = Math.max(1, minRedeemPoints);
    }

    public BigDecimal getEarnPerAmount() { return earnPerAmount; }
    public BigDecimal getPointValue() { return pointValue; }
    public BigDecimal getRedeemMaxPercent() { return redeemMaxPercent; }
    public int getMinRedeemPoints() { return minRedeemPoints; }

    /** Số điểm được cộng cho số tiền khách thực trả (làm tròn xuống) */
    public int pointsEarnedFor(BigDecimal paidAmount) {
        if (paidAmount == null || paidAmount.signum() <= 0) return 0;
        return paidAmount.divide(earnPerAmount, 0, RoundingMode.FLOOR).intValue();
    }

    /** Số tiền được giảm khi dùng số điểm này */
    public BigDecimal valueOf(int points) {
        return pointValue.multiply(BigDecimal.valueOf(points));
    }

    /** Số điểm tối đa được dùng cho hóa đơn có số tiền (sau giảm giá) là amount, không vượt số dư */
    public int maxRedeemable(BigDecimal amount, int balance) {
        if (amount == null || amount.signum() <= 0 || balance <= 0) return 0;
        BigDecimal cap = amount.multiply(redeemMaxPercent).divide(BigDecimal.valueOf(100), 0, RoundingMode.FLOOR);
        int byAmount = cap.divide(pointValue, 0, RoundingMode.FLOOR).intValue();
        return Math.min(byAmount, balance);
    }
}
