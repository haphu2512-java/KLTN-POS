package com.pos.shift;

public enum ShiftStatus {
    OPEN,            // Đang trực ca, hóa đơn và thu/chi két được gắn vào ca này
    PENDING_REVIEW,  // Đã chốt số nhưng có chênh lệch, chờ quản lý duyệt
    CLOSED           // Đã khóa sổ: khớp tiền hoặc quản lý đã duyệt
}
