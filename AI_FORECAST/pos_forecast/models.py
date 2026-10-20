"""Mô hình dự báo chuỗi thời gian (AI_for_POS.md, mục 3.1 và 3.2)."""

import math
from collections.abc import Sequence


def moving_average(sales: Sequence[float], window: int = 7) -> float:
    """Trung bình trượt của `window` ngày gần nhất: ȳ_MA = (1/N) Σ y_{t-i}."""
    if not sales:
        return 0.0
    recent = sales[-window:] if window > 0 else sales
    return sum(recent) / len(recent)


def exponential_smoothing(sales: Sequence[float], alpha: float = 0.35, init_window: int = 7) -> float:
    """Single Exponential Smoothing: ŷ_{t+1} = α·y_t + (1 − α)·ŷ_t.

    Giá trị khởi tạo ŷ_1 là trung bình `init_window` ngày đầu, để một ngày đầu bất thường
    không kéo lệch cả chuỗi. Trả về dự báo cho ngày kế tiếp.
    """
    if not sales:
        return 0.0
    if not 0 < alpha <= 1:
        raise ValueError("alpha phải nằm trong (0, 1]")
    forecast = moving_average(sales[:init_window], init_window)
    for actual in sales:
        forecast = alpha * actual + (1 - alpha) * forecast
    return forecast


def std_dev(sales: Sequence[float]) -> float:
    """Độ lệch chuẩn mẫu σ_d của lượng bán hằng ngày.

    Chuỗi đầu vào đã được điền 0 cho ngày không bán, nên với nhu cầu ngắt quãng các ngày 0
    vẫn được tính vào phương sai thay vì bị bỏ qua (bỏ qua sẽ làm σ_d thấp hơn thực tế).
    """
    n = len(sales)
    if n < 2:
        return 0.0
    mean = sum(sales) / n
    return math.sqrt(sum((x - mean) ** 2 for x in sales) / (n - 1))


def zero_ratio(sales: Sequence[float]) -> float:
    """Tỉ lệ ngày không có giao dịch."""
    if not sales:
        return 0.0
    return sum(1 for x in sales if x == 0) / len(sales)
