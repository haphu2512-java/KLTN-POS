package com.pos.stockcount;

/** Trạng thái phiếu kiểm kê: đang đếm → chờ quản lý duyệt → đã duyệt (đã điều chỉnh tồn) / đã hủy */
public enum StockCountStatus {
    DRAFT, SUBMITTED, APPROVED, CANCELLED
}
