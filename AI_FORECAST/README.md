# AI_FORECAST: Dự báo nhu cầu & đề xuất nhập hàng (Python)

Bản cài đặt Python của module dự báo mô tả trong [`AI_for_POS.md`](../AI_for_POS.md). Chạy độc lập,
không thay đổi backend Java hay frontend.

## Công thức (theo AI_for_POS.md mục 3)

| Đại lượng | Công thức | Mặc định |
|---|---|---|
| Dự báo nhu cầu/ngày d̄ | Single Exponential Smoothing ŷₜ₊₁ = α·yₜ + (1−α)·ŷₜ | α = 0.35 |
| Trung bình trượt | ȳ = (1/7) Σ yₜ₋ᵢ (thuật toán thay thế `MOVING_AVG`, luôn trả kèm `movingAverage7`) | 7 ngày |
| Tồn kho an toàn | SS = Z × σ_d × √L | Z = 1.65, L = 2 |
| Điểm đặt hàng lại | ROP = d̄ × L + SS | |
| Số lượng đề xuất | Q = max(0, ⌈ROP + d̄ × T − tồn kho⌉) | T = số ngày dự báo (7) |
| Đánh giá | MAE, RMSE trên 7 ngày cuối, mô hình chỉ học từ các ngày trước đó | |

Mức khẩn cấp (giữ mã giống `ForecastResponse.java` để frontend dùng lại):

- `HIGH` / Khẩn cấp: hết hàng, hoặc tồn kho ≤ d̄ × L (sẽ hết trước khi hàng về).
- `MEDIUM` / Cao: tồn kho ≤ ROP.
- `LOW` / Bình thường: còn lại.

Trường hợp đặc biệt (mục 4):

- Sản phẩm chưa bán ngày nào trong cửa sổ lịch sử: `hasHistory = false`, dự báo 0, đề xuất nhập 0.
- Nhu cầu ngắt quãng: ngày không bán được điền 0 và vẫn tính vào σ_d (không bỏ qua), sản phẩm có
  trên 50% số ngày không bán được gắn cờ `intermittent = true` và ghi chú trong `reason`.

## Cài đặt & chạy

```bash
cd AI_FORECAST
python -m venv .venv && source .venv/bin/activate
pip install -r requirements-dev.txt

# Kiểm thử
python -m pytest

# Dự báo từ dòng lệnh với dữ liệu mẫu
python -m pos_forecast.cli --branch 1
python -m pos_forecast.cli --all-branches --only-restock --out forecast.json

# REST API (http://localhost:8000/docs)
uvicorn pos_forecast.api:app --port 8000
```

Đọc thẳng CSDL MySQL của hệ thống: chép `.env.example` thành `.env`, đặt `FORECAST_DATA_SOURCE=mysql`
và các biến `DB_*`, rồi `export $(cat .env | xargs)` trước khi chạy. Khi đọc MySQL, cửa sổ lịch sử kết
thúc ở hôm qua (chỉ dùng ngày đã bán trọn) và bỏ các hóa đơn `REFUNDED`.

Job ban đêm (mục 5, 02:00 mỗi ngày) bằng cron:

```cron
0 2 * * * cd /path/to/AI_FORECAST && FORECAST_DATA_SOURCE=mysql .venv/bin/python -m pos_forecast.cli --all-branches --out /var/pos/forecast.json
```

## API

| Phương thức | Đường dẫn | Mô tả |
|---|---|---|
| GET | `/api/forecast/demand/{productId}?branchId=1&days=7&algorithm=EXP_SMOOTH` | Dự báo một sản phẩm |
| GET | `/api/forecast/recommendations?branchId=1&days=7&onlyRestock=false` | Danh sách đề xuất, sắp theo mức khẩn cấp |
| POST | `/api/forecast/predict` | Gửi `{"sales": [...], "currentStock": 20, "days": 7}`, nhận kết quả. Dùng khi backend Java muốn gọi sang |
| GET | `/health` | Kiểm tra dịch vụ |

`asOf=YYYY-MM-DD` (tùy chọn) đặt ngày cuối của lịch sử. Ví dụ một phần tử kết quả:

```json
{
  "productId": 3, "productName": "Sữa tươi Vinamilk 180ml", "currentStock": 0,
  "dailyDemandForecast": 17.32, "movingAverage7": 16.29, "totalPredictedDemand": 121,
  "stdDev": 4.95, "safetyStock": 11.54, "reorderPoint": 46.18, "suggestedRestockQty": 168,
  "urgency": "HIGH", "urgencyLabel": "Khẩn cấp", "hasHistory": true, "intermittent": false,
  "evaluation": {"mae": 4.745, "rmse": 5.285, "trainDays": 23, "validationDays": 7, "validationMean": 16.286},
  "quality": "GOOD",
  "reason": "Tồn kho 0, nhu cầu dự báo 17.3/ngày, lead time 2 ngày, ...",
  "historicalDailySales": [...], "dailyForecast": [{"date": "2026-10-20", "quantity": 17.32}, ...]
}
```

`quality` dựa trên MAE chia trung bình bán thực tế ở tập kiểm định: ≤ 30% `GOOD`, ≤ 60% `FAIR`,
còn lại `LOW`; dưới 14 ngày dữ liệu là `INSUFFICIENT_DATA`. Đây là gợi ý để quản lý quyết định,
hệ thống không tự tạo đơn nhập.

## Định dạng CSV (`sample_data/`)

- `sales.csv`: `date,branch_id,product_id,quantity_sold,status` (mỗi dòng một lần bán hoặc tổng theo ngày;
  `status = REFUNDED` bị bỏ; dòng lỗi hoặc số lượng âm được ghi log rồi bỏ qua).
- `products.csv`: `product_id,barcode,name,category,unit,active`
- `stock.csv`: `branch_id,product_id,quantity`

Dữ liệu mẫu là dữ liệu giả lập 30 ngày (20/09 đến 19/10/2026) cho 2 chi nhánh, gồm một sản phẩm bán
ngắt quãng (id 4) và một sản phẩm mới chưa bán (id 5).

## Tham số

Đặt qua biến môi trường (xem `.env.example`): `AI_FORECAST_ALGORITHM`, `AI_FORECAST_ALPHA`,
`AI_FORECAST_MA_WINDOW`, `AI_SERVICE_LEVEL_Z`, `AI_LEAD_TIME_DAYS`, `AI_REVIEW_PERIOD_DAYS`,
`AI_HISTORY_DAYS`, `AI_VALIDATION_DAYS`, `AI_INTERMITTENT_ZERO_RATIO`.

## Khác biệt so với backend Java hiện tại

`ForecastService.java` đang dùng 14 ngày lịch sử và `nhu cầu × hệ số an toàn 1.10 − tồn kho`. Bản Python
theo đúng tài liệu: 30 ngày lịch sử, SS/ROP/Q như bảng trên, L = 2 ngày (file `application.properties`
đang để `AI_LEAD_TIME_DAYS=3`, có thể đổi bằng biến môi trường cùng tên).
