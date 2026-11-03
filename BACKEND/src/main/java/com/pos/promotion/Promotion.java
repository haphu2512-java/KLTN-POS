package com.pos.promotion;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Chương trình khuyến mãi do ADMIN tạo. Áp cho cả đơn hoặc chỉ các mặt hàng thuộc một danh mục,
 * có thể giới hạn theo chi nhánh, thời gian, giá trị đơn tối thiểu và chỉ dành cho khách thành viên.
 */
@Entity
@Table(name = "promotion")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Promotion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PromotionType type;

    /** PERCENT: số % (0-100]; FIXED_AMOUNT: số tiền giảm */
    @Column(name = "discount_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal value;

    /** Trần số tiền giảm cho loại PERCENT (null: không giới hạn) */
    @Column(name = "max_discount_amount", precision = 14, scale = 2)
    private BigDecimal maxDiscountAmount;

    /** Tiền hàng được áp dụng tối thiểu để hưởng khuyến mãi (null: không yêu cầu) */
    @Column(name = "min_order_amount", precision = 14, scale = 2)
    private BigDecimal minOrderAmount;

    /** Chỉ áp cho mặt hàng thuộc danh mục này (so khớp không phân biệt hoa thường); null: cả đơn */
    @Column(length = 150)
    private String category;

    /** Chỉ dành cho khách thành viên (hóa đơn có chọn khách hàng) */
    @Builder.Default
    @Column(name = "member_only", nullable = false)
    private Boolean memberOnly = false;

    /** Khách phải có ít nhất số điểm này (khuyến mãi cho thành viên thân thiết); null: không yêu cầu */
    @Column(name = "min_customer_points")
    private Integer minCustomerPoints;

    /** Chỉ áp ở chi nhánh này; null: mọi chi nhánh */
    @Column(name = "branch_id")
    private Long branchId;

    /** Thời gian hiệu lực; null ở đầu nào là không giới hạn đầu đó */
    @Column(name = "start_at")
    private LocalDateTime startAt;

    @Column(name = "end_at")
    private LocalDateTime endAt;

    /** ADMIN bật/tắt nhanh mà không phải xóa */
    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Builder.Default
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
