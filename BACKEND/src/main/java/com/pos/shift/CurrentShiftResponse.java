package com.pos.shift;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Ca đang mở tại chi nhánh của phiên đăng nhập. Khi chưa mở ca, kèm gợi ý: ca nên mở theo giờ hiện tại,
 * người được xếp vào ca đó, tiền mặt ca trước để lại và ghi chú bàn giao của ca trước.
 */
@Data
@Builder
public class CurrentShiftResponse {
    private ShiftSessionResponse session;
    private Long suggestedWorkShiftId;
    /** Người quản lý đã xếp vào ca gợi ý hôm nay (null: chưa xếp ai) */
    private Long assignedStaffId;
    private String assignedStaffName;
    private BigDecimal suggestedOpeningCash;
    private ShiftSessionResponse previousShift;
    private boolean shiftRequired;

    /** Số tiền ca trước để lại; tiền đầu ca khác số này phải ghi lý do */
    private BigDecimal handoverExpected;
    /** Chênh lệch từ mức này trở lên phải ghi lý do khi đóng ca */
    private BigDecimal differenceThreshold;
    /** Tiền lẻ tiêu chuẩn nên để lại trong két cho ca sau */
    private BigDecimal standardFloat;
    /** Hạn mức tiền mặt trong két, vượt thì nhắc nộp quỹ (0 = không nhắc) */
    private BigDecimal drawerLimit;
    /** Số phút được mở ca sớm trước giờ bắt đầu */
    private int openEarlyMinutes;
}
