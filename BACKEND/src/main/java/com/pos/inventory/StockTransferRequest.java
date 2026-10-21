package com.pos.inventory;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StockTransferRequest {

    /** Không bắt buộc: luôn là chi nhánh của phiên đăng nhập; nếu gửi thì phải trùng */
    private Long fromBranchId;

    @NotNull(message = "Chi nhánh nhận không được để trống")
    private Long toBranchId;

    @NotNull(message = "Sản phẩm không được để trống")
    private Long productId;

    @NotNull(message = "Số lượng không được để trống")
    @Min(value = 1, message = "Số lượng điều chuyển phải lớn hơn 0")
    private Integer quantity;

    private String note;
}
