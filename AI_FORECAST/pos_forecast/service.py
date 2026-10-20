"""Ghép mô hình, chỉ số đánh giá và công thức tồn kho thành kết quả dự báo cho từng sản phẩm.

Tên trường kết quả dùng camelCase và giữ các trường của ForecastResponse.java
(productId, dailyDemandForecast, suggestedRestockQty, urgency, ...) để backend/frontend dùng lại được.
"""

from collections.abc import Sequence
from datetime import date, timedelta

from .config import ALGORITHM_MOVING_AVG, ForecastConfig, normalize_algorithm
from .data import DataSource, Product, fill_daily_series
from .inventory import (URGENCY_HIGH, URGENCY_LABELS, URGENCY_LOW, URGENCY_MEDIUM, classify_urgency,
                        order_quantity, reorder_point, safety_stock)
from .metrics import backtest
from .models import exponential_smoothing, moving_average, std_dev, zero_ratio

URGENCY_ORDER = {URGENCY_HIGH: 0, URGENCY_MEDIUM: 1, URGENCY_LOW: 2}

QUALITY_GOOD = "GOOD"
QUALITY_FAIR = "FAIR"
QUALITY_LOW = "LOW"
QUALITY_INSUFFICIENT = "INSUFFICIENT_DATA"


def _predictor(algorithm: str, config: ForecastConfig):
    if algorithm == ALGORITHM_MOVING_AVG:
        return lambda sales: moving_average(sales, config.ma_window)
    return lambda sales: exponential_smoothing(sales, config.alpha, config.ma_window)


def _quality(evaluation: dict | None) -> str:
    """Đánh giá độ tin cậy từ sai số tương đối MAE / trung bình bán thực tế ở tập kiểm định."""
    if evaluation is None:
        return QUALITY_INSUFFICIENT
    mean = evaluation["validationMean"]
    relative_error = evaluation["mae"] / mean if mean > 0 else (0.0 if evaluation["mae"] == 0 else float("inf"))
    if relative_error <= 0.3:
        return QUALITY_GOOD
    if relative_error <= 0.6:
        return QUALITY_FAIR
    return QUALITY_LOW


def forecast_series(sales: Sequence[float], current_stock: float, forecast_days: int | None = None,
                    algorithm: str | None = None, config: ForecastConfig | None = None) -> dict:
    """Dự báo từ một chuỗi bán hàng theo ngày đã điền 0 (ngày cũ nhất trước).

    Chuỗi rỗng hoặc toàn 0 được coi là sản phẩm chưa có lịch sử: không dự báo, không đề xuất nhập.
    """
    config = config or ForecastConfig()
    algorithm = normalize_algorithm(algorithm or config.algorithm)
    days = forecast_days or config.review_period_days
    lead_time = config.lead_time_days
    sales = [float(x) for x in sales]
    has_history = any(x > 0 for x in sales)

    if not has_history:
        urgency = classify_urgency(False, current_stock, 0.0, lead_time, 0.0)
        return {
            "algorithm": algorithm,
            "forecastDays": days,
            "hasHistory": False,
            "currentStock": current_stock,
            "dailyDemandForecast": 0.0,
            "movingAverage7": 0.0,
            "totalPredictedDemand": 0,
            "stdDev": 0.0,
            "safetyStock": 0.0,
            "reorderPoint": 0.0,
            "suggestedRestockQty": 0,
            "urgency": urgency,
            "urgencyLabel": URGENCY_LABELS[urgency],
            "intermittent": False,
            "evaluation": None,
            "quality": QUALITY_INSUFFICIENT,
            "historicalDailySales": sales,
            "reason": (f"Chưa có lịch sử bán trong {len(sales)} ngày gần nhất nên chưa đủ thông tin "
                       f"để dự báo. Không đề xuất nhập hàng."),
        }

    predict = _predictor(algorithm, config)
    daily = predict(sales)
    sigma = std_dev(sales)
    ss = safety_stock(sigma, lead_time, config.z)
    rop = reorder_point(daily, lead_time, ss)
    qty = order_quantity(rop, daily, days, current_stock)
    urgency = classify_urgency(True, current_stock, daily, lead_time, rop)
    evaluation = backtest(sales, predict, config.validation_days)
    intermittent = zero_ratio(sales) > config.intermittent_zero_ratio

    reason = (f"Tồn kho {current_stock:g}, nhu cầu dự báo {daily:.1f}/ngày, lead time {lead_time} ngày, "
              f"tồn kho an toàn {ss:.1f}, điểm đặt hàng lại {rop:.1f}. ")
    reason += (f"Đề xuất nhập {qty} để đủ bán {days} ngày tới mà vẫn giữ trên điểm đặt hàng lại." if qty > 0
               else f"Tồn kho đủ cho {days} ngày tới, chưa cần nhập.")
    if intermittent:
        reason += " Sản phẩm bán ngắt quãng nên dự báo kém chắc chắn hơn."

    return {
        "algorithm": algorithm,
        "forecastDays": days,
        "hasHistory": True,
        "currentStock": current_stock,
        "dailyDemandForecast": round(daily, 2),
        "movingAverage7": round(moving_average(sales, config.ma_window), 2),
        "totalPredictedDemand": round(daily * days),
        "stdDev": round(sigma, 2),
        "safetyStock": round(ss, 2),
        "reorderPoint": round(rop, 2),
        "suggestedRestockQty": qty,
        "urgency": urgency,
        "urgencyLabel": URGENCY_LABELS[urgency],
        "intermittent": intermittent,
        "evaluation": evaluation,
        "quality": _quality(evaluation),
        "historicalDailySales": sales,
        "reason": reason,
    }


def forecast_product(product: Product, sales_by_day: dict[date, float], current_stock: int, as_of: date,
                     forecast_days: int | None = None, algorithm: str | None = None,
                     config: ForecastConfig | None = None) -> dict:
    """Dự báo cho một sản phẩm, kèm thông tin sản phẩm và dự báo theo từng ngày cho biểu đồ."""
    config = config or ForecastConfig()
    start = as_of - timedelta(days=config.history_days - 1)
    result = forecast_series(fill_daily_series(sales_by_day, start, as_of), current_stock,
                             forecast_days, algorithm, config)
    daily = result["dailyDemandForecast"]
    return {
        "productId": product.id,
        "barcode": product.barcode,
        "productName": product.name,
        "category": product.category,
        "unit": product.unit,
        "historyStart": start.isoformat(),
        "historyEnd": as_of.isoformat(),
        **result,
        "dailyForecast": [
            {"date": (as_of + timedelta(days=i)).isoformat(), "quantity": daily}
            for i in range(1, result["forecastDays"] + 1)
        ],
    }


class ForecastService:
    def __init__(self, source: DataSource, config: ForecastConfig | None = None):
        self.source = source
        self.config = config or ForecastConfig()

    def _window(self, as_of: date | None) -> tuple[date, date]:
        end = as_of or self.source.default_as_of()
        return end - timedelta(days=self.config.history_days - 1), end

    def demand(self, product_id: int, branch_id: int, forecast_days: int | None = None,
               algorithm: str | None = None, as_of: date | None = None) -> dict | None:
        """Dự báo cho một sản phẩm tại một chi nhánh. None nếu không tìm thấy sản phẩm."""
        product = next((p for p in self.source.products() if p.id == product_id), None)
        if product is None:
            return None
        start, end = self._window(as_of)
        sales = self.source.daily_sales(branch_id, start, end).get(product_id, {})
        stock = self.source.stock(branch_id).get(product_id, 0)
        result = forecast_product(product, sales, stock, end, forecast_days, algorithm, self.config)
        return {"branchId": branch_id, **result}

    def recommendations(self, branch_id: int, forecast_days: int | None = None, algorithm: str | None = None,
                        as_of: date | None = None, only_restock: bool = False) -> list[dict]:
        """Danh sách dự báo cho mọi sản phẩm đang bán, sắp theo mức khẩn cấp rồi số lượng đề xuất."""
        start, end = self._window(as_of)
        sales = self.source.daily_sales(branch_id, start, end)
        stock = self.source.stock(branch_id)
        results = [
            {"branchId": branch_id,
             **forecast_product(p, sales.get(p.id, {}), stock.get(p.id, 0), end,
                                forecast_days, algorithm, self.config)}
            for p in self.source.products() if p.active
        ]
        if only_restock:
            results = [r for r in results if r["suggestedRestockQty"] > 0]
        results.sort(key=lambda r: (URGENCY_ORDER[r["urgency"]], -r["suggestedRestockQty"], r["productName"] or ""))
        return results
