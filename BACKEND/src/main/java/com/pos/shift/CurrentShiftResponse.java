package com.pos.shift;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Trạng thái ca của người đang đăng nhập. Khi chưa mở ca, kèm gợi ý từ ca trước để bàn giao:
 * ca nên mở theo giờ hiện tại, tiền mặt ca trước để lại và ghi chú bàn giao của ca trước.
 */
@Data
@Builder
public class CurrentShiftResponse {
    private ShiftSessionResponse session;
    private Long suggestedWorkShiftId;
    private BigDecimal suggestedOpeningCash;
    private ShiftSessionResponse previousShift;
    private boolean shiftRequired;
}
