package com.pos.shift;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Ca trực kèm số liệu: với ca đang mở là số tạm tính đến hiện tại, với ca đã đóng là số đã chốt.
 */
@Data
@Builder
public class ShiftSessionResponse {
    private Long id;
    private Long branchId;
    private Long workShiftId;
    private String workShiftName;
    private String workShiftTime;
    private Long employeeId;
    private String employeeName;
    private LocalDate shiftDate;
    private ShiftStatus status;
    private LocalDateTime openedAt;
    private LocalDateTime closedAt;
    private Long closedBy;
    private BigDecimal openingCash;
    private String openingNote;
    private BigDecimal cashSales;
    private BigDecimal nonCashSales;
    private Integer invoiceCount;
    private Integer refundedCount;
    private BigDecimal expectedCash;
    private BigDecimal closingCash;
    private BigDecimal cashDifference;
    private String handoverNote;
}
