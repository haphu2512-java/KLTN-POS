"""Module AI dự báo nhu cầu và đề xuất nhập hàng cho hệ thống POS (theo AI_for_POS.md)."""

from .config import ForecastConfig
from .service import ForecastService, forecast_series

__all__ = ["ForecastConfig", "ForecastService", "forecast_series"]
