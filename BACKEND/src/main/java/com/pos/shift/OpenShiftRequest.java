package com.pos.shift;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class OpenShiftRequest {

    /** Ca muốn mở; để trống thì lấy ca theo giờ hiện tại */
    private Long workShiftId;

    /**
     * Nhân viên trực. Để trống là người quản lý đã xếp vào ca này. Chỉ quản lý/quản trị được chọn người khác
     * (trực thay, hoặc ca chưa xếp ai) và phải ghi overrideReason.
     */
    private Long staffMemberId;

    @NotNull(message = "Tiền đầu ca không được để trống")
    @DecimalMin(value = "0", message = "Tiền đầu ca không được âm")
    private BigDecimal openingCash;

    @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
    private String note;

    /** Bắt buộc khi tiền đếm được lúc nhận két khác số ca trước để lại */
    @Size(max = 255, message = "Lý do chênh lệch nhận ca tối đa 255 ký tự")
    private String handoverReason;

    /** Quản lý/quản trị mở ca ngoài khung giờ hoặc cho người không được xếp phải ghi lý do (trực thay, tăng ca...) */
    @Size(max = 255, message = "Lý do mở ca tối đa 255 ký tự")
    private String overrideReason;
}
