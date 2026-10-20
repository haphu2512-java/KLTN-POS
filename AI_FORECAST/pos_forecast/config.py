"""Tham số dự báo (theo AI_for_POS.md, mục 3). Mọi giá trị đều đổi được qua biến môi trường."""

import os
from dataclasses import dataclass, field

ALGORITHM_EXP_SMOOTH = "EXP_SMOOTH"
ALGORITHM_MOVING_AVG = "MOVING_AVG"


def normalize_algorithm(algorithm: str | None) -> str:
    """"MOVING_AVG" hoặc mặc định "EXP_SMOOTH" (chấp nhận "EXP_SMOOTHING" giống backend Java)."""
    if algorithm and algorithm.strip().upper() == ALGORITHM_MOVING_AVG:
        return ALGORITHM_MOVING_AVG
    return ALGORITHM_EXP_SMOOTH


def _env_float(name: str, default: float) -> float:
    value = os.getenv(name)
    return float(value) if value not in (None, "") else default


def _env_int(name: str, default: int) -> int:
    value = os.getenv(name)
    return int(value) if value not in (None, "") else default


@dataclass(frozen=True)
class ForecastConfig:
    # Hệ số làm mịn α của Single Exponential Smoothing (mục 3.1)
    alpha: float = 0.35
    # Cửa sổ trung bình trượt (mục 3.2)
    ma_window: int = 7
    # Hệ số Z cho mức phục vụ 95% (mục 3.3)
    z: float = 1.65
    # Lead time giao hàng L, đơn vị ngày (mục 3.3, 3.4)
    lead_time_days: int = 2
    # Chu kỳ đặt hàng T, đơn vị ngày (mục 3.5)
    review_period_days: int = 7
    # Số ngày lịch sử bán hàng dùng để dự báo (mục 5: getSalesHistory(days=30))
    history_days: int = 30
    # Số ngày cuối giữ lại để đánh giá MAE/RMSE (không dùng để huấn luyện)
    validation_days: int = 7
    # Tỉ lệ ngày không bán vượt ngưỡng này thì coi là nhu cầu ngắt quãng (mục 4.2)
    intermittent_zero_ratio: float = 0.5
    algorithm: str = field(default=ALGORITHM_EXP_SMOOTH)

    @classmethod
    def from_env(cls) -> "ForecastConfig":
        return cls(
            alpha=_env_float("AI_FORECAST_ALPHA", cls.alpha),
            ma_window=_env_int("AI_FORECAST_MA_WINDOW", cls.ma_window),
            z=_env_float("AI_FORECAST_Z", cls.z),
            lead_time_days=_env_int("AI_LEAD_TIME_DAYS", cls.lead_time_days),
            review_period_days=_env_int("AI_REVIEW_PERIOD_DAYS", cls.review_period_days),
            history_days=_env_int("AI_HISTORY_DAYS", cls.history_days),
            validation_days=_env_int("AI_VALIDATION_DAYS", cls.validation_days),
            intermittent_zero_ratio=_env_float("AI_INTERMITTENT_ZERO_RATIO", cls.intermittent_zero_ratio),
            algorithm=normalize_algorithm(os.getenv("AI_FORECAST_ALGORITHM", cls.algorithm)),
        )
