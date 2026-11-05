package com.pos.stockcount;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.pos.product.CostPriceSerializer;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Một mặt hàng trong phiếu kiểm kê. systemQty là tồn hệ thống lúc đếm; khi duyệt, tồn được cộng/trừ
 * (countedQty - systemQty) nên hàng bán ra trong lúc đang kiểm kê không bị ghi đè.
 */
@Entity
@Table(name = "stock_count_line", uniqueConstraints = @UniqueConstraint(columnNames = {"stock_count_id", "product_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockCountLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_count_id", nullable = false)
    @JsonBackReference
    private StockCount stockCount;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", length = 255)
    private String productName;

    @Column(length = 50)
    private String barcode;

    @Column(name = "system_qty", nullable = false)
    private Integer systemQty;

    @Column(name = "counted_qty", nullable = false)
    private Integer countedQty;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AdjustmentReason reason;

    /** Giá vốn lúc đếm, để tính giá trị hao hụt; ẩn với thu ngân như giá vốn ở chỗ khác */
    @Column(name = "unit_cost", precision = 14, scale = 2)
    @JsonSerialize(using = CostPriceSerializer.class)
    private BigDecimal unitCost;

    @Transient
    public int getDifference() {
        return (countedQty != null ? countedQty : 0) - (systemQty != null ? systemQty : 0);
    }
}
