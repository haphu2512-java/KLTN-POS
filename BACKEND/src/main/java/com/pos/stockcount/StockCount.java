package com.pos.stockcount;

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
 * Phiếu kiểm kê / điều chỉnh tồn kho của một chi nhánh. Nhân viên kho quét mã và nhập số đếm được,
 * gửi duyệt; quản lý duyệt thì hệ thống cộng/trừ tồn đúng phần chênh lệch và ghi nhật ký.
 */
@Entity
@Table(name = "stock_count")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockCount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StockCountStatus status = StockCountStatus.DRAFT;

    /** Kiểm kê định kỳ cả kho / theo danh mục, hoặc điều chỉnh nhanh một vài mặt hàng */
    @Column(length = 255)
    private String note;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    /** Giá trị chênh lệch theo giá vốn (âm: hao hụt), chốt lúc duyệt */
    @Column(name = "variance_value", precision = 14, scale = 2)
    private BigDecimal varianceValue;

    @Builder.Default
    @OneToMany(mappedBy = "stockCount", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @JsonManagedReference
    private List<StockCountLine> lines = new ArrayList<>();

    @Transient
    private String createdByName;

    @Transient
    private String approvedByName;
}
