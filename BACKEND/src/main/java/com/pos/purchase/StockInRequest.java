package com.pos.purchase;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class StockInRequest {

    /** Không bắt buộc: luôn là chi nhánh của phiên đăng nhập; nếu gửi thì phải trùng */
    private Long branchId;

    private Long supplierId;

    private Long employeeId;

    private String note;

    /** Mã chống trùng do màn hình nhập kho tạo cho mỗi phiếu (UUID); tùy chọn */
    @Size(max = 64, message = "Mã yêu cầu tối đa 64 ký tự")
    private String clientRequestId;

    @NotEmpty(message = "Phiếu nhập phải có ít nhất 1 sản phẩm")
    private List<Item> items;

    @Data
    public static class Item {
        @NotNull(message = "Mã sản phẩm không được để trống")
        private Long productId;

        @NotNull(message = "Số lượng không được để trống")
        @Min(value = 1, message = "Số lượng nhập phải lớn hơn 0")
        private Integer quantity;

        private BigDecimal unitCost;
    }
}
