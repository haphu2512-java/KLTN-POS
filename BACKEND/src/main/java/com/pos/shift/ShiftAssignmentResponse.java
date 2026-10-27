package com.pos.shift;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class ShiftAssignmentResponse {
    private Long id;
    private Long branchId;
    private Long workShiftId;
    private LocalDate shiftDate;
    private Long staffMemberId;
    private String staffName;
}
