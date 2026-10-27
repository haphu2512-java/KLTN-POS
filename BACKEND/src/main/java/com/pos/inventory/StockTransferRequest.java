package com.pos.inventory;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

    /** Không nhận từ client: server gán theo tài khoản đang đăng nhập */
    private Long employeeId;

    /** Mã chống trùng do màn hình điều chuyển tạo cho mỗi lần gửi (UUID); tùy chọn */
    @Size(max = 64, message = "Mã yêu cầu tối đa 64 ký tự")
    private String clientRequestId;
}
