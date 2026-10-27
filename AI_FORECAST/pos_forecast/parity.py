"""Sinh bộ ca đối chiếu giữa bản Python và ForecastCalculator.java.

Chạy lại khi đổi công thức:  python -m pos_forecast.parity
File kết quả tests/fixtures/parity_cases.json được cả pytest lẫn JUnit (ForecastParityTest) đọc.
"""

import json
import random
from pathlib import Path

from .config import ForecastConfig
from .service import forecast_series

FIXTURE = Path(__file__).resolve().parent.parent / "tests" / "fixtures" / "parity_cases.json"

# Các trường được so sánh giữa hai bên
COMPARED = ["hasHistory", "dailyDemandForecast", "movingAverage7", "totalPredictedDemand", "stdDev",
            "safetyStock", "reorderPoint", "suggestedRestockQty", "urgency", "intermittent", "quality"]


def _noisy(seed: int, mean: float, days: int = 30, weekend_boost: float = 1.4) -> list[float]:
    rng = random.Random(seed)
    return [float(max(0, round(rng.gauss(mean * (weekend_boost if i % 7 in (5, 6) else 1), mean * 0.2))))
            for i in range(days)]


def build_cases() -> list[dict]:
    rng = random.Random(7)
    inputs = [
        ("Bán đều 10/ngày, tồn 20", [10.0] * 30, 20, 7, "EXP_SMOOTH"),
        ("Bán đều, tồn dư", [10.0] * 30, 500, 7, "EXP_SMOOTH"),
        ("Có chu kỳ cuối tuần", _noisy(1, 20), 150, 7, "EXP_SMOOTH"),
        ("Chu kỳ cuối tuần, trung bình trượt", _noisy(1, 20), 150, 7, "MOVING_AVG"),
        ("Chạm điểm đặt hàng lại", _noisy(1, 20), 50, 7, "EXP_SMOOTH"),
        ("Hết hàng", _noisy(2, 15), 0, 7, "EXP_SMOOTH"),
        ("Dự báo 14 ngày", _noisy(3, 35), 40, 14, "EXP_SMOOTH"),
        ("Xu hướng tăng", [float(5 + i) for i in range(30)], 60, 7, "EXP_SMOOTH"),
        ("Bán ngắt quãng", [float(rng.randint(1, 4)) if rng.random() < 0.3 else 0.0 for _ in range(30)],
         6, 7, "EXP_SMOOTH"),
        ("Sản phẩm mới, còn hàng", [0.0] * 30, 24, 7, "EXP_SMOOTH"),
        ("Sản phẩm mới, hết hàng", [0.0] * 30, 0, 7, "EXP_SMOOTH"),
        ("Lịch sử ngắn 10 ngày", [3.0, 5.0, 4.0, 6.0, 2.0, 8.0, 5.0, 4.0, 7.0, 6.0], 10, 7, "EXP_SMOOTH"),
    ]
    config = ForecastConfig()
    cases = []
    for name, sales, stock, days, algorithm in inputs:
        result = forecast_series(sales, stock, days, algorithm, config)
        cases.append({
            "name": name,
            "input": {"sales": sales, "currentStock": stock, "days": days, "algorithm": algorithm},
            "expected": {k: result[k] for k in COMPARED},
        })
    return cases


def main() -> None:
    FIXTURE.write_text(json.dumps(build_cases(), ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Đã ghi {FIXTURE}")


if __name__ == "__main__":
    main()
