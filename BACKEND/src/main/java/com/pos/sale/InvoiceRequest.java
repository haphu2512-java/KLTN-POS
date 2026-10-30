package com.pos.sale;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class InvoiceRequest {

    /** Không bắt buộc: chi nhánh lấy từ phiên đăng nhập; nếu gửi thì phải trùng chi nhánh đó */
    private Long branchId;

    private Long customerId;

    private Long employeeId;

    private BigDecimal discount;

    @NotNull(message = "Phương thức thanh toán không được để trống")
    private PaymentMethod paymentMethod;

    private BigDecimal receivedAmount;

    private String note;

    @Size(max = 255, message = "Lý do giảm giá tối đa 255 ký tự")
    private String discountReason;

    /** Mã duyệt nhận được sau khi quản lý nhập PIN (POST /api/approvals/discount) */
    @Size(max = 64)
    private String discountApprovalToken;

    /** Mã chống trùng do POS tạo cho mỗi đơn (UUID); tùy chọn */
    @Size(max = 64, message = "Mã yêu cầu tối đa 64 ký tự")
    private String clientRequestId;

    @NotEmpty(message = "Hóa đơn phải có ít nhất 1 sản phẩm")
    private List<Item> items;

    @Data
    public static class Item {
        @NotNull(message = "Mã sản phẩm không được để trống")
        private Long productId;

        @NotNull(message = "Số lượng không được để trống")
        @Min(value = 1, message = "Số lượng bán phải lớn hơn 0")
        private Integer quantity;

        private BigDecimal unitPrice;
    }
}
