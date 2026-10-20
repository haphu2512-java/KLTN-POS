"""Tồn kho an toàn, điểm đặt hàng lại và số lượng đề xuất (AI_for_POS.md, mục 3.3 đến 3.5)."""

import math

# Mức độ khẩn cấp: giữ mã HIGH/MEDIUM/LOW giống backend Java để frontend dùng lại được
URGENCY_HIGH = "HIGH"
URGENCY_MEDIUM = "MEDIUM"
URGENCY_LOW = "LOW"

URGENCY_LABELS = {
    URGENCY_HIGH: "Khẩn cấp",
    URGENCY_MEDIUM: "Cao",
    URGENCY_LOW: "Bình thường",
}


def safety_stock(std_dev: float, lead_time_days: float, z: float = 1.65) -> float:
    """SS = Z × σ_d × √L"""
    return z * std_dev * math.sqrt(lead_time_days)


def reorder_point(avg_daily_demand: float, lead_time_days: float, safety: float) -> float:
    """ROP = (d̄ × L) + SS"""
    return avg_daily_demand * lead_time_days + safety


def order_quantity(rop: float, avg_daily_demand: float, review_period_days: float, current_stock: float) -> int:
    """Q_order = max(0, ⌈ROP + (d̄ × T) − Q_current⌉)"""
    # Làm tròn trước khi lấy trần để sai số dấu phẩy động (vd. 19.000000000004) không cộng thêm 1
    return max(0, math.ceil(round(rop + avg_daily_demand * review_period_days - current_stock, 6)))


def classify_urgency(has_history: bool, current_stock: float, avg_daily_demand: float,
                     lead_time_days: float, rop: float) -> str:
    """Phân loại mức độ khẩn cấp.

    - Khẩn cấp (HIGH): đã hết hàng, hoặc tồn kho không đủ bán trong thời gian chờ hàng về (≤ d̄ × L).
    - Cao (MEDIUM): tồn kho đã xuống tới điểm đặt hàng lại (≤ ROP).
    - Bình thường (LOW): còn lại.
    """
    if current_stock <= 0:
        return URGENCY_HIGH
    if not has_history:
        return URGENCY_LOW
    if current_stock <= avg_daily_demand * lead_time_days:
        return URGENCY_HIGH
    if current_stock <= rop:
        return URGENCY_MEDIUM
    return URGENCY_LOW
