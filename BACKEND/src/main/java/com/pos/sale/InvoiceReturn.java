package com.pos.sale;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Phiếu hoàn trả: một lần khách trả lại một số món của hóa đơn. Một hóa đơn có thể có nhiều phiếu
 * cho tới khi trả hết. Tiền hoàn tính theo tỷ lệ số tiền khách thực trả (đã trừ khuyến mãi, giảm giá, điểm).
 */
@Entity
@Table(name = "invoice_return", indexes = {
        @Index(name = "idx_return_invoice", columnList = "invoice_id"),
        @Index(name = "idx_return_shift", columnList = "shift_session_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceReturn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    @JsonIgnore
    private Invoice invoice;

    @Column(name = "invoice_id", insertable = false, updatable = false)
    private Long invoiceId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    /** Hoàn theo phương thức của hóa đơn gốc (tiền mặt trừ két, VietQR/thẻ chuyển lại) */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    @Column(name = "refund_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal refundAmount;

    /** Điểm đã dùng được trả lại cho khách */
    @Column(name = "points_returned")
    private Integer pointsReturned;

    /** Điểm đã cộng bị thu hồi */
    @Column(name = "points_revoked")
    private Integer pointsRevoked;

    @Column(length = 255)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by")
    private Long createdBy;

    /** Quản lý nhập PIN duyệt (thu ngân hoàn trả); null khi quản lý tự làm */
    @Column(name = "approved_by")
    private Long approvedBy;

    /** Ca có két chi tiền hoàn; null thì tính vào ca đã bán hóa đơn */
    @Column(name = "shift_session_id")
    private Long shiftSessionId;

    @Builder.Default
    @OneToMany(mappedBy = "invoiceReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<InvoiceReturnDetail> details = new ArrayList<>();

    @Transient
    private String createdByName;

    @Transient
    private String approvedByName;
}
