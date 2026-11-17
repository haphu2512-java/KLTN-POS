package com.pos.stockcount;

/** Lý do chênh lệch giữa tồn thực tế và tồn hệ thống */
public enum AdjustmentReason {
    DAMAGED("Hư hỏng"),
    EXPIRED("Hết hạn"),
    LOST("Mất / thất thoát"),
    MISCOUNT("Đếm sai / nhập sai trước đó"),
    OTHER("Khác");

    private final String label;

    AdjustmentReason(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
