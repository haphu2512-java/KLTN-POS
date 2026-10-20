"""Chỉ số đánh giá mô hình (AI_for_POS.md, mục 6) và backtest tách tập huấn luyện / kiểm định."""

import math
from collections.abc import Callable, Sequence


def mae(actual: Sequence[float], predicted: Sequence[float]) -> float:
    """MAE = (1/n) Σ |y_t − ŷ_t|"""
    _check(actual, predicted)
    return sum(abs(a - p) for a, p in zip(actual, predicted)) / len(actual)


def rmse(actual: Sequence[float], predicted: Sequence[float]) -> float:
    """RMSE = √((1/n) Σ (y_t − ŷ_t)²)"""
    _check(actual, predicted)
    return math.sqrt(sum((a - p) ** 2 for a, p in zip(actual, predicted)) / len(actual))


def backtest(sales: Sequence[float], predict: Callable[[Sequence[float]], float],
             validation_days: int) -> dict | None:
    """Huấn luyện trên phần đầu chuỗi, dự báo `validation_days` ngày cuối rồi so với thực tế.

    Mô hình không bao giờ thấy dữ liệu kiểm định khi dự báo. Trả về None khi chuỗi quá ngắn
    (cần ít nhất gấp đôi số ngày kiểm định).
    """
    if validation_days <= 0 or len(sales) < 2 * validation_days:
        return None
    train, test = list(sales[:-validation_days]), list(sales[-validation_days:])
    forecast = predict(train)
    predicted = [forecast] * len(test)
    return {
        "mae": round(mae(test, predicted), 3),
        "rmse": round(rmse(test, predicted), 3),
        "trainDays": len(train),
        "validationDays": len(test),
        "validationMean": round(sum(test) / len(test), 3),
    }


def _check(actual: Sequence[float], predicted: Sequence[float]) -> None:
    if len(actual) != len(predicted) or not actual:
        raise ValueError("actual và predicted phải cùng độ dài và không rỗng")
