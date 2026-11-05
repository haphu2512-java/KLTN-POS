package com.pos.purchaseorder;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.pos.product.CostPriceSerializer;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "purchase_order_line")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    @JsonBackReference
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    private PurchaseOrder purchaseOrder;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", length = 255)
    private String productName;

    @Column(length = 50)
    private String barcode;

    @Column(name = "ordered_qty", nullable = false)
    private Integer orderedQty;

    @Column(name = "received_qty", nullable = false)
    @Builder.Default
    private Integer receivedQty = 0;

    @Column(name = "unit_cost", precision = 14, scale = 2)
    @JsonSerialize(using = CostPriceSerializer.class)
    private BigDecimal unitCost;

    /** Số lượng AI đề xuất lúc lập đơn (để so với số người dùng sửa) */
    @Column(name = "suggested_qty")
    private Integer suggestedQty;

    /** Lý do đề xuất của AI (tồn hiện tại, nhu cầu dự báo, SS/ROP) */
    @Column(name = "forecast_note", length = 255)
    private String forecastNote;

    @Transient
    public int getRemainingQty() {
        return Math.max(0, (orderedQty != null ? orderedQty : 0) - (receivedQty != null ? receivedQty : 0));
    }
}
