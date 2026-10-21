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

    @NotNull(message = "Tiền đầu ca không được để trống")
    @DecimalMin(value = "0", message = "Tiền đầu ca không được âm")
    private BigDecimal openingCash;

    @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
    private String note;
}
