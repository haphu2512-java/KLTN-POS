package com.pos.loyalty;

/** Loại biến động trong sổ điểm của khách hàng */
public enum PointType {
    /** Cộng điểm khi mua hàng */
    EARN,
    /** Dùng điểm để giảm tiền hóa đơn */
    REDEEM,
    /** Thu hồi điểm đã cộng khi hóa đơn bị hoàn trả */
    REFUND_EARN,
    /** Trả lại điểm đã dùng khi hóa đơn bị hoàn trả */
    REFUND_REDEEM,
    /** Quản lý điều chỉnh tay (có ghi lý do) */
    ADJUST
}
