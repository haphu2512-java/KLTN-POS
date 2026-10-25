package com.pos.shift;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** Danh sách ca theo bộ lọc kèm tổng hợp chênh lệch tiền mặt */
@Data
@Builder
public class ShiftSummaryResponse {
    private List<ShiftSessionResponse> shifts;
    private int totalShifts;
    private int openShifts;
    /** Số ca đã chốt có chênh lệch đang chờ quản lý duyệt */
    private int pendingReviewShifts;
    /** Số ca đã đóng mà tiền đếm được lệch với tiền dự kiến */
    private int mismatchedShifts;
    /** Tổng tiền thiếu (số dương) của các ca thiếu */
    private BigDecimal totalShortage;
    /** Tổng tiền dư của các ca dư */
    private BigDecimal totalOverage;
}
