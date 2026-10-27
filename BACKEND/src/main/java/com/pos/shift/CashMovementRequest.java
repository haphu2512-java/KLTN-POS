package com.pos.shift;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CashMovementRequest {

    @NotNull(message = "Chọn loại thu/chi")
    private CashMovementType type;

    @Size(max = 50, message = "Danh mục tối đa 50 ký tự")
    private String category;

    @NotNull(message = "Số tiền không được để trống")
    @DecimalMin(value = "1", message = "Số tiền phải lớn hơn 0")
    private BigDecimal amount;

    @NotBlank(message = "Ghi lý do thu/chi")
    @Size(max = 255, message = "Lý do tối đa 255 ký tự")
    private String reason;

    @Size(max = 100, message = "Tên người nhận/đưa tối đa 100 ký tự")
    private String counterpart;
}
