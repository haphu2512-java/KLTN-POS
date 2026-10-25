package com.pos.shift;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Ca trực kèm số liệu: với ca đang mở là số tạm tính đến hiện tại, với ca đã đóng là số đã chốt.
 */
@Data
@Builder
public class ShiftSessionResponse {
    private Long id;
    private Long branchId;
    private String branchName;
    private Long workShiftId;
    private String workShiftName;
    private String workShiftTime;
    private Long employeeId;
    /** Nhân viên trực ca */
    private Long staffMemberId;
    private String employeeName;
    /** Tài khoản bấm mở ca (thường là tài khoản quầy dùng chung) */
    private Long openedBy;
    private String openedByName;
    private LocalDate shiftDate;
    private ShiftStatus status;
    private LocalDateTime openedAt;
    private LocalDateTime closedAt;
    private Long closedBy;
    private String closedByName;
    /** Người khác (quản lý/quản trị) đóng hộ ca này */
    private boolean closedByOther;
    private BigDecimal openingCash;
    private String openingNote;
    private BigDecimal cashSales;
    private BigDecimal nonCashSales;
    private Integer invoiceCount;
    private Integer refundedCount;
    private BigDecimal cashRefunds;
    private BigDecimal nonCashRefunds;
    private BigDecimal expectedCash;
    private BigDecimal closingCash;
    private BigDecimal cashDifference;
    private String handoverNote;

    /** Ca đang mở quá giờ kết thúc của ca hơn số giờ cấu hình: nhân viên có thể đã quên đóng */
    private boolean overdue;

    // Nhận két
    private BigDecimal handoverExpected;
    private BigDecimal handoverDifference;
    private String handoverReason;
    private String openOverrideReason;

    // Thu/chi két
    private BigDecimal expenses;
    private BigDecimal drops;
    private BigDecimal floatIn;

    // Đối soát theo phương thức: hệ thống tính = bán - hoàn
    private BigDecimal vietqrSales;
    private BigDecimal cardSales;
    private BigDecimal vietqrRefunds;
    private BigDecimal cardRefunds;
    private BigDecimal expectedVietqr;
    private BigDecimal expectedCard;
    private BigDecimal countedVietqr;
    private BigDecimal countedCard;
    private BigDecimal vietqrDifference;
    private BigDecimal cardDifference;
    private String differenceReason;
    private Map<Integer, Integer> denominations;

    // Bàn giao
    private BigDecimal leftInDrawer;
    private BigDecimal finalDrop;

    // Duyệt
    private Long reviewedBy;
    private String reviewedByName;
    private LocalDateTime reviewedAt;
    private String reviewNote;
}
