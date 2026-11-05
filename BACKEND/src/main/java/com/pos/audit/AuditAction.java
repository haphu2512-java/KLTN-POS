package com.pos.audit;

/** Các thao tác cần ghi nhật ký (kiểm soát nội bộ: tiền, hàng, giá và quyền) */
public enum AuditAction {
    LOGIN_FAILED("Đăng nhập sai"),
    LOGIN_LOCKED("Tài khoản bị khóa tạm do sai mật khẩu"),
    PRODUCT_CREATE("Thêm sản phẩm"),
    PRICE_CHANGE("Đổi giá sản phẩm"),
    PRODUCT_DELETE("Ngừng bán sản phẩm"),
    DISCOUNT_APPROVED("Quản lý duyệt giảm giá vượt hạn mức"),
    DISCOUNT_PIN_FAILED("Nhập sai PIN duyệt"),
    INVOICE_DISCOUNT("Hóa đơn có giảm giá tay"),
    REFUND("Hoàn trả"),
    SHIFT_OPEN("Mở ca"),
    SHIFT_CLOSE("Đóng ca"),
    SHIFT_CASH_VARIANCE("Chênh lệch két khi đóng ca"),
    SHIFT_REVIEW("Duyệt ca"),
    CASH_MOVEMENT("Thu/chi tiền mặt trong ca"),
    STOCK_IN("Nhập kho"),
    STOCK_TRANSFER("Điều chuyển kho"),
    STOCK_ADJUSTMENT("Điều chỉnh tồn kho"),
    PURCHASE_ORDER("Đơn đặt hàng nhà cung cấp"),
    USER_CREATE("Tạo tài khoản"),
    USER_UPDATE("Sửa tài khoản / phân quyền"),
    USER_STATUS("Khóa / mở tài khoản"),
    PASSWORD_RESET("Đặt lại mật khẩu"),
    PROMOTION_CHANGE("Thay đổi khuyến mãi"),
    POINTS_ADJUST("Điều chỉnh điểm thành viên");

    private final String label;

    AuditAction(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
