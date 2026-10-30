package com.pos.loyalty;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Một dòng trong sổ điểm: mỗi lần cộng, trừ, hoàn hay điều chỉnh điểm đều ghi lại ở đây.
 * Cột customer.points chỉ là số dư hiện tại; sổ điểm cho biết số dư đó đến từ đâu.
 */
@Entity
@Table(name = "point_transaction", indexes = {
        @Index(name = "idx_point_tx_customer", columnList = "customer_id, created_at"),
        @Index(name = "idx_point_tx_invoice", columnList = "invoice_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PointTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    /** Hóa đơn gây ra biến động (null với điều chỉnh tay) */
    @Column(name = "invoice_id")
    private Long invoiceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PointType type;

    /** Dương là cộng, âm là trừ */
    @Column(nullable = false)
    private Integer points;

    @Column(name = "balance_after", nullable = false)
    private Integer balanceAfter;

    @Column(length = 255)
    private String note;

    @Column(name = "created_by")
    private Long createdBy;

    @Builder.Default
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
