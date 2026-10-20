package com.pos.shift;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalTime;

@Data
public class WorkShiftRequest {

    @NotBlank(message = "Tên ca không được để trống")
    @Size(max = 50, message = "Tên ca tối đa 50 ký tự")
    private String name;

    @NotNull(message = "Giờ bắt đầu không được để trống")
    private LocalTime startTime;

    @NotNull(message = "Giờ kết thúc không được để trống")
    private LocalTime endTime;

    private Boolean active;
}
