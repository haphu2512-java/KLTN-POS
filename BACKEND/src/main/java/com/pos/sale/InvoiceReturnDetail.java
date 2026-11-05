package com.pos.sale;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Một dòng của phiếu hoàn trả: món nào, trả bao nhiêu, hoàn bao nhiêu tiền */
@Entity
@Table(name = "invoice_return_detail")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceReturnDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "return_id", nullable = false)
    @JsonBackReference
    private InvoiceReturn invoiceReturn;

    @Column(name = "invoice_detail_id", nullable = false)
    private Long invoiceDetailId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", length = 255)
    private String productName;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal unitPrice;

    /** Tiền hoàn cho dòng này (đã phân bổ khuyến mãi, giảm giá, điểm theo tỷ lệ) */
    @Column(name = "refund_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal refundAmount;
}
