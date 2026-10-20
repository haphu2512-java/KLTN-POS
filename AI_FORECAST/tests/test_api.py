from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from pos_forecast.api import app, get_service
from pos_forecast.data import CsvDataSource
from pos_forecast.service import ForecastService

SAMPLE_DIR = Path(__file__).resolve().parent.parent / "sample_data"


@pytest.fixture
def client():
    app.dependency_overrides[get_service] = lambda: ForecastService(CsvDataSource(SAMPLE_DIR))
    yield TestClient(app)
    app.dependency_overrides.clear()


def test_health(client):
    assert client.get("/health").json() == {"status": "UP"}


def test_demand(client):
    res = client.get("/api/forecast/demand/1", params={"branchId": 1, "days": 14})
    assert res.status_code == 200
    body = res.json()
    assert body["productId"] == 1
    assert body["forecastDays"] == 14
    assert len(body["dailyForecast"]) == 14


def test_demand_not_found(client):
    assert client.get("/api/forecast/demand/999").status_code == 404


def test_demand_rejects_bad_days(client):
    assert client.get("/api/forecast/demand/1", params={"days": 0}).status_code == 422


def test_recommendations(client):
    res = client.get("/api/forecast/recommendations", params={"branchId": 2, "onlyRestock": True})
    assert res.status_code == 200
    assert all(r["branchId"] == 2 and r["suggestedRestockQty"] > 0 for r in res.json())


def test_predict(client):
    res = client.post("/api/forecast/predict", json={"sales": [10] * 30, "currentStock": 20, "days": 7})
    assert res.status_code == 200
    assert res.json()["suggestedRestockQty"] == 70
