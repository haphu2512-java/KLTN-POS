package com.pos.promotion;

/** Cách tính tiền giảm của chương trình khuyến mãi */
public enum PromotionType {
    /** Giảm theo % số tiền hàng được áp dụng (có thể giới hạn số tiền giảm tối đa) */
    PERCENT,
    /** Giảm một số tiền cố định, không vượt số tiền hàng được áp dụng */
    FIXED_AMOUNT
}
