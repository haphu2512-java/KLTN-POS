"""REST API dự báo (AI_for_POS.md, mục 5). Chạy: uvicorn pos_forecast.api:app --port 8000"""

from datetime import date
from functools import lru_cache

from fastapi import Depends, FastAPI, HTTPException, Query
from pydantic import BaseModel, Field

from .config import ForecastConfig
from .data import data_source_from_env
from .service import ForecastService, forecast_series

app = FastAPI(title="POS AI Forecast", version="1.0.0")


@lru_cache
def get_service() -> ForecastService:
    return ForecastService(data_source_from_env(), ForecastConfig.from_env())


class PredictRequest(BaseModel):
    sales: list[float] = Field(description="Số lượng bán theo ngày, ngày cũ nhất trước, ngày không bán = 0")
    currentStock: float = Field(ge=0)
    days: int | None = Field(default=None, ge=1, le=60)
    algorithm: str | None = None


@app.get("/health")
def health() -> dict:
    return {"status": "UP"}


@app.get("/api/forecast/demand/{product_id}")
def demand(product_id: int,
           branchId: int = 1,
           days: int = Query(7, ge=1, le=60),
           algorithm: str | None = None,
           asOf: date | None = None,
           service: ForecastService = Depends(get_service)) -> dict:
    result = service.demand(product_id, branchId, days, algorithm, asOf)
    if result is None:
        raise HTTPException(status_code=404, detail=f"Không tìm thấy sản phẩm {product_id}")
    return result


@app.get("/api/forecast/recommendations")
def recommendations(branchId: int = 1,
                    days: int = Query(7, ge=1, le=60),
                    algorithm: str | None = None,
                    asOf: date | None = None,
                    onlyRestock: bool = False,
                    service: ForecastService = Depends(get_service)) -> list[dict]:
    return service.recommendations(branchId, days, algorithm, asOf, onlyRestock)


@app.post("/api/forecast/predict")
def predict(body: PredictRequest, service: ForecastService = Depends(get_service)) -> dict:
    """Dự báo không trạng thái: backend Java gửi chuỗi bán hàng và tồn kho, nhận lại kết quả."""
    return forecast_series(body.sales, body.currentStock, body.days, body.algorithm, service.config)
