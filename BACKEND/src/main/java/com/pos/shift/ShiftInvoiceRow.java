package com.pos.shift;

import com.pos.sale.InvoiceStatus;
import com.pos.sale.PaymentMethod;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Một dòng hóa đơn trong chi tiết ca, để thu ngân dò từng khoản tiền vào/ra két.
 * SALE: hóa đơn bán trong ca. REFUND_OTHER_SHIFT: hóa đơn của ca khác nhưng tiền hoàn trả chi từ két ca này.
 */
@Data
@Builder
public class ShiftInvoiceRow {
    public enum Kind { SALE, REFUND_OTHER_SHIFT }

    private Long id;
    private Kind kind;
    private LocalDateTime createdAt;
    private PaymentMethod paymentMethod;
    private InvoiceStatus status;
    private BigDecimal totalAmount;
    private BigDecimal receivedAmount;
    private BigDecimal changeAmount;
    private BigDecimal discount;
    private int itemCount;
    private String customerName;
    private LocalDateTime refundedAt;
    /** Tiền hoàn trả của hóa đơn này được trừ vào két của ca đang xem */
    private boolean refundCountedHere;
    private String note;
}
