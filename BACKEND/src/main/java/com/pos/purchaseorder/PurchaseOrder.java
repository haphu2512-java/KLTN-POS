package com.pos.purchaseorder;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.pos.product.CostPriceSerializer;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Đơn đặt hàng nhà cung cấp. Thường được tạo từ đề xuất nhập hàng của AI dự báo (gom theo NCC lần nhập gần nhất),
 * người dùng sửa số lượng rồi gửi; khi hàng về thì nhận (đủ hoặc một phần), hệ thống tạo phiếu nhập kho.
 */
@Entity
@Table(name = "purchase_order")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "supplier_id")
    private Long supplierId;

    @Column(name = "supplier_name", length = 150)
    private String supplierName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PurchaseOrderStatus status = PurchaseOrderStatus.DRAFT;

    /** FORECAST: tạo từ đề xuất AI; MANUAL: lập tay */
    @Column(length = 20)
    private String source;

    @Column(length = 255)
    private String note;

    @Column(name = "expected_date")
    private LocalDate expectedDate;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Builder.Default
    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @JsonManagedReference
    private List<PurchaseOrderLine> lines = new ArrayList<>();

    /** Tổng tiền dự kiến theo giá vốn (ẩn với thu ngân) */
    @Transient
    @JsonSerialize(using = CostPriceSerializer.class)
    public BigDecimal getTotalAmount() {
        return lines.stream()
                .map(l -> (l.getUnitCost() != null ? l.getUnitCost() : BigDecimal.ZERO).multiply(BigDecimal.valueOf(l.getOrderedQty())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
