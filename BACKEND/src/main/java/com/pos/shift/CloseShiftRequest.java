package com.pos.shift;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CloseShiftRequest {

    @NotNull(message = "Tiền mặt cuối ca không được để trống")
    @DecimalMin(value = "0", message = "Tiền mặt cuối ca không được âm")
    private BigDecimal closingCash;

    @Size(max = 500, message = "Ghi chú bàn giao tối đa 500 ký tự")
    private String handoverNote;
}
