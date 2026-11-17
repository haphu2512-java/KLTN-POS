package com.pos.promotion;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Dữ liệu ADMIN gửi khi tạo hoặc sửa chương trình khuyến mãi */
@Data
public class PromotionRequest {

    @NotBlank(message = "Tên chương trình không được để trống")
    @Size(max = 100, message = "Tên chương trình tối đa 100 ký tự")
    private String name;

    @Size(max = 255, message = "Mô tả tối đa 255 ký tự")
    private String description;

    @NotNull(message = "Chọn loại khuyến mãi")
    private PromotionType type;

    @NotNull(message = "Nhập mức giảm")
    @DecimalMin(value = "0", inclusive = false, message = "Mức giảm phải lớn hơn 0")
    private BigDecimal value;

    @DecimalMin(value = "0", inclusive = false, message = "Giảm tối đa phải lớn hơn 0")
    private BigDecimal maxDiscountAmount;

    @DecimalMin(value = "0", message = "Giá trị đơn tối thiểu không được âm")
    private BigDecimal minOrderAmount;

    @Size(max = 150, message = "Danh mục tối đa 150 ký tự")
    private String category;

    private Boolean memberOnly;

    @Min(value = 0, message = "Điểm tối thiểu không được âm")
    private Integer minCustomerPoints;

    private Long branchId;

    private LocalDateTime startAt;

    private LocalDateTime endAt;

    private Boolean active;
}
