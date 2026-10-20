from datetime import date

import pytest

from pos_forecast.config import ForecastConfig, normalize_algorithm
from pos_forecast.data import CsvDataSource, fill_daily_series
from pos_forecast.service import QUALITY_INSUFFICIENT, ForecastService, forecast_series

SAMPLE_DIR = __import__("pathlib").Path(__file__).resolve().parent.parent / "sample_data"


def test_new_product_without_history_gets_no_restock():
    result = forecast_series([0] * 30, current_stock=5)
    assert result["hasHistory"] is False
    assert result["suggestedRestockQty"] == 0
    assert result["dailyDemandForecast"] == 0
    assert result["quality"] == QUALITY_INSUFFICIENT


def test_forecast_series_applies_spec_formulas():
    sales = [10.0] * 30
    result = forecast_series(sales, current_stock=20, forecast_days=7)
    # σ = 0 → SS = 0, ROP = 10 × 2 = 20, Q = ⌈20 + 70 − 20⌉ = 70
    assert result["dailyDemandForecast"] == pytest.approx(10)
    assert result["safetyStock"] == 0
    assert result["reorderPoint"] == pytest.approx(20)
    assert result["suggestedRestockQty"] == 70
    assert result["urgency"] == "HIGH"
    assert result["urgencyLabel"] == "Khẩn cấp"
    assert result["evaluation"]["mae"] == 0
    assert result["quality"] == "GOOD"


def test_forecast_series_moving_average():
    sales = [0.0] * 23 + [7.0] * 7
    result = forecast_series(sales, current_stock=100, algorithm="MOVING_AVG")
    assert result["algorithm"] == "MOVING_AVG"
    assert result["dailyDemandForecast"] == pytest.approx(7)


def test_intermittent_demand_is_flagged():
    sales = [0, 0, 3, 0, 0, 0, 2] * 4 + [0, 1]
    result = forecast_series(sales, current_stock=10)
    assert result["intermittent"] is True
    assert "ngắt quãng" in result["reason"]


def test_config_is_used():
    config = ForecastConfig(lead_time_days=4, z=2.0)
    result = forecast_series([10.0] * 30, current_stock=0, config=config)
    assert result["reorderPoint"] == pytest.approx(40)


def test_normalize_algorithm():
    assert normalize_algorithm("moving_avg") == "MOVING_AVG"
    assert normalize_algorithm("EXP_SMOOTHING") == "EXP_SMOOTH"
    assert normalize_algorithm(None) == "EXP_SMOOTH"


def test_fill_daily_series_fills_missing_days_with_zero():
    series = fill_daily_series({date(2026, 1, 2): 5}, date(2026, 1, 1), date(2026, 1, 3))
    assert series == [0, 5, 0]


def test_csv_source_skips_refunds():
    source = CsvDataSource(SAMPLE_DIR)
    day = date(2026, 10, 19)
    sales = source.daily_sales(1, day, day)
    # dòng REFUNDED 10 gói mì ngày 19/10 không được cộng
    raw = sum(int(line.split(",")[3]) for line in (SAMPLE_DIR / "sales.csv").read_text().splitlines()[1:]
              if line.startswith("2026-10-19,1,2,") and line.endswith("PAID"))
    assert sales[2][day] == raw


def test_recommendations_on_sample_data():
    service = ForecastService(CsvDataSource(SAMPLE_DIR))
    results = service.recommendations(branch_id=1, forecast_days=7)
    assert len(results) == 5
    by_id = {r["productId"]: r for r in results}
    assert by_id[5]["hasHistory"] is False and by_id[5]["suggestedRestockQty"] == 0
    assert by_id[3]["currentStock"] == 0 and by_id[3]["urgency"] == "HIGH"
    assert len(by_id[1]["historicalDailySales"]) == 30
    assert len(by_id[1]["dailyForecast"]) == 7
    order = ["HIGH", "MEDIUM", "LOW"]
    assert [order.index(r["urgency"]) for r in results] == sorted(order.index(r["urgency"]) for r in results)


def test_only_restock_filter():
    service = ForecastService(CsvDataSource(SAMPLE_DIR))
    results = service.recommendations(branch_id=1, only_restock=True)
    assert results and all(r["suggestedRestockQty"] > 0 for r in results)


def test_demand_unknown_product():
    assert ForecastService(CsvDataSource(SAMPLE_DIR)).demand(999, 1) is None
