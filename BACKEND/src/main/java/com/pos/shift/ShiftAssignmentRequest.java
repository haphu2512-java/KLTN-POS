package com.pos.shift;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ShiftAssignmentRequest {

    @NotNull(message = "Chọn ca")
    private Long workShiftId;

    @NotNull(message = "Chọn ngày")
    private LocalDate shiftDate;

    /** Nhân viên được xếp; để trống là bỏ xếp ca này */
    private Long staffMemberId;
}
