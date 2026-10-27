package com.pos.shift;

/** Các khoản tiền mặt ra vào két ngoài hóa đơn bán và hoàn trả */
public enum CashMovementType {
    EXPENSE,   // Chi trong ca (mua đá, phí ship, ứng tiền...): trừ két
    DROP,      // Nộp quỹ giữa ca khi két nhiều tiền: trừ két, phải ghi người nhận
    FLOAT_IN   // Bổ sung tiền lẻ vào két: cộng két, phải ghi người đưa
}
