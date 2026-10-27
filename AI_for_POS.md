# Hệ thống Dự báo Nhu cầu & Tối ưu Tồn kho Bán lẻ bằng AI (Demand Forecasting & Inventory Optimization)

Tài liệu kỹ thuật giải trình chi tiết về bài toán, công thức toán học, thuật toán và triển khai thực tế của module **Dự báo AI** trong đề tài Khóa luận tốt nghiệp Hệ thống POS và Quản lý Kho.

---

## 1. Giới thiệu bài toán & Mục tiêu

Đối với cửa hàng bán lẻ nhỏ và vừa:
- **Tồn kho quá mức (Overstocking):** Làm ứ đọng vốn lưu động, tăng chi phí mặt bằng và rủi ro hết hạn sử dụng (đặc biệt ngành FMCG).
- **Thiếu hụt hàng (Stockout):** Mất doanh thu trực tiếp, giảm trải nghiệm khách hàng và mất thị phần vào tay đối thủ.

**Mục tiêu của module AI:**
1. Dự báo nhu cầu tiêu thụ (Demand Forecasting) cho từng sản phẩm trong chu kỳ 7 đến 14 ngày tới.
2. Tự động tính toán điểm đặt hàng lại (Reorder Point - ROP) và mức tồn kho an toàn (Safety Stock).
3. Đưa ra danh sách đề xuất số lượng cần nhập hàng (Suggested Restock Quantity) theo mức độ khẩn cấp (Khẩn cấp / Cao / Bình thường).
4. Vận hành tự động qua scheduled background job hàng đêm mà không cần thao tác thủ công.

---

## 2. Lựa chọn giải pháp kỹ thuật

| Tiêu chí | Mô hình Ngôn ngữ lớn (LLM) | Dịch vụ AI Cloud bên ngoài | Thuật toán Thống kê/Time-Series nhúng (Lựa chọn) |
|---|---|---|---|
| **Bản chất bài toán** | Xử lý ngôn ngữ, không tối ưu cho chuỗi số | Xử lý chuỗi số | Chuỗi thời gian (Time-series Forecasting) thuần túy |
| **Chi phí** | Phí API token theo tháng | Phí hạ tầng cloud cao | **0 VNĐ (Chạy nhúng trên máy chủ)** |
| **Độ trễ & Offline** | Phụ thuộc Internet, độ trễ cao (1-3s) | Cần kết nối Internet liên tục | **Cực nhanh (< 50ms), hoạt động cả khi mất mạng** |
| **Tính minh bạch** | Hộp đen (Black-box), dễ ảo giác số liệu | Khó can thiệp | **Toán học rõ ràng, giải trình chuẩn xác trước hội đồng** |

> **Quyết định kiến trúc:** Module dự báo được triển khai dưới dạng dịch vụ cốt lõi nhúng ngay trong Spring Boot (`ForecastService.java`), chạy tự động và kết xuất dữ liệu cho giao diện người dùng qua REST API và biểu đồ SVG tương tác.

---

## 3. Thuật toán & Cơ sở Toán học

### 3.1. Exponential Smoothing (Làm mịn số mũ đơn — Single Exponential Smoothing)
Được sử dụng làm mô hình dự báo cốt lõi, gán trọng số giảm dần theo cấp số nhân cho các ngày trong quá khứ để ưu tiên xu hướng tiêu thụ gần nhất:

$$\hat{y}_{t+1} = \alpha \cdot y_t + (1 - \alpha) \cdot \hat{y}_t$$

Trong đó:
- $\hat{y}_{t+1}$: Giá trị dự báo cho ngày tiếp theo.
- $y_t$: Số lượng bán thực tế của ngày gần nhất.
- $\hat{y}_t$: Giá trị dự báo của ngày trước đó.
- $\alpha$: Hệ số làm mịn (Smoothing Factor). Sau quá trình tinh chỉnh nghiệm thu, hệ số được chọn là **$\alpha = 0.35$** (cân bằng tối ưu giữa việc phản ứng với biến động nhu cầu mới và loại trừ nhiễu giao dịch ngẫu nhiên).

### 3.2. Moving Average 7 ngày (Trung bình trượt)
Kết hợp tính trung bình trượt 7 ngày để triệt tiêu tính chu kỳ tuần (ví dụ: lượng mua tăng mạnh vào thứ 7 và Chủ nhật):

$$\bar{y}_{MA7} = \frac{1}{7} \sum_{i=0}^{6} y_{t-i}$$

### 3.3. Tồn kho an toàn (Safety Stock - SS)
Mức đệm tồn kho để đề phòng trường hợp nhu cầu tăng đột biến hoặc nhà cung cấp giao hàng trễ:

$$SS = Z \times \sigma_d \times \sqrt{L}$$

Trong đó:
- $Z = 1.65$: Hệ số mức độ phục vụ 95% (Service Level) theo phân phối chuẩn.
- $\sigma_d$: Độ lệch chuẩn của lượng bán hàng ngày.
- $L = 2$ (ngày): Thời gian giao hàng trung bình của nhà cung cấp (Lead Time).

### 3.4. Điểm đặt hàng lại (Reorder Point - ROP)
Ngưỡng tồn kho tối thiểu mà khi số lượng tồn kho giảm xuống dưới mức này, hệ thống sẽ kích hoạt cảnh báo nhập hàng:

$$ROP = (\bar{d} \times L) + SS$$

- $\bar{d}$: Nhu cầu tiêu thụ trung bình mỗi ngày.
- $L$: Lead time giao hàng.

### 3.5. Đề xuất số lượng đặt hàng ($Q_{order}$)
Số lượng hàng cần nhập đề xuất để đáp ứng nhu cầu chu kỳ tiếp theo ($T = 7$ ngày) mà không gây thừa kho:

$$Q_{order} = \max\left(0, \lceil ROP + (\bar{d} \times T) - Q_{current} \rceil\right)$$

- $Q_{current}$: Số lượng tồn kho thực tế hiện tại tại chi nhánh.

---

## 4. Xử lý dữ liệu đặc biệt (Edge Cases)

1. **Sản phẩm mới chưa có lịch sử bán hàng (`dailySales.isEmpty()`):**
   - Đặt cờ `hasHistory = false`.
   - Gán `suggestedRestockQty = 0` và `dailyDemandForecast = 0.0`.
   - Không tạo đề xuất nhập bừa bãi khi chưa đủ thông tin thị trường.

2. **Sản phẩm có chu kỳ bán ngắt quãng (Intermittent Demand):**
   - Khi các ngày không có giao dịch xen kẽ, hệ thống tính toán $\sigma_d$ hiệu chỉnh để tránh tính sai lệch phương sai.
   - Cách hiệu chỉnh: chuỗi bán hàng được điền 0 cho mọi ngày không có giao dịch trong cửa sổ 30 ngày, và $\sigma_d$ là độ lệch chuẩn mẫu tính trên cả các ngày 0 đó (nếu bỏ qua ngày 0, $\sigma_d$ sẽ thấp hơn thực tế và tồn kho an toàn bị thiếu).
   - Sản phẩm có trên 50% số ngày không bán được gắn cờ `intermittent = true` và ghi chú trong lý do đề xuất để quản lý biết dự báo kém chắc chắn hơn.

3. **Phân loại mức độ khẩn cấp:**
   - **Khẩn cấp (`HIGH`):** hết hàng, hoặc tồn kho $\le \bar{d} \times L$ (sẽ hết trước khi hàng về).
   - **Cao (`MEDIUM`):** tồn kho $\le ROP$.
   - **Bình thường (`LOW`):** còn lại.

4. **Độ tin cậy dự báo:** giữ 7 ngày cuối làm tập kiểm định, mô hình chỉ học từ các ngày trước đó rồi tính MAE/RMSE (mục 6). Sai số tương đối MAE / trung bình bán thực tế $\le 30\%$ là `GOOD`, $\le 60\%$ là `FAIR`, còn lại `LOW`; dưới 14 ngày dữ liệu là `INSUFFICIENT_DATA`.

---

## 5. Kiến trúc Triển khai trong Hệ thống

```text
[Giao dịch POS / Nhập kho]
           │
           ▼
[MySQL: invoice + invoice_detail (doanh số), inventory (tồn kho)]
           │
           ▼
[Spring Boot: ForecastService.java]
   ├── loadDailySales(branchId, days=30)          (điền 0 cho ngày không bán, bỏ hóa đơn hoàn trả)
   ├── ForecastCalculator.java                     (toàn bộ công thức mục 3, 4, 6)
   │     ├── exponentialSmoothing(sales, alpha=0.35) / movingAverage(sales, 7)
   │     ├── safetyStock(stdDev, leadTime=2, z=1.65)
   │     ├── reorderPoint(avgDaily, leadTime=2, ss)
   │     ├── orderQuantity(rop, avgDaily, T, currentStock)
   │     └── MAE / RMSE trên 7 ngày kiểm định
   └── @Scheduled(cron = "0 0 2 * * ?") runNightlyForecastJob()  → lưu bảng forecast_result
           │
           ├── REST API: /api/forecast/demand?branchId={id}&days=7      (mọi sản phẩm)
           ├── REST API: /api/forecast/demand/{id}?days=7              (1 sản phẩm, kèm lịch sử)
           └── REST API: /api/forecast/recommendations?branchId={id}
           │
           ▼
[Frontend: ForecastPage.jsx]
   ├── Biểu đồ trực quan SalesTrendChart.jsx (SVG tương tác)
   ├── Bảng đề xuất: Khẩn cấp / Cao / Bình thường, kèm ROP, tồn kho an toàn, lý do
   └── Nút "Lên phiếu nhập" / "Tạo phiếu nhập tổng hợp": mở phiếu nhập điền sẵn ở trang Kho, quản lý kiểm tra rồi mới lưu
```

### Kiểm chứng bằng Python (`AI_FORECAST/`)

Thư mục `AI_FORECAST/` là bản cài đặt Python của cùng các công thức, dùng để kiểm chứng và lấy số liệu cho báo cáo (thử α, thử dữ liệu CSV, xuất kết quả JSON). Python không chạy trong luồng bán hàng. `python -m pos_forecast.parity` sinh bộ ca `AI_FORECAST/tests/fixtures/parity_cases.json`, và JUnit `ForecastParityTest` kiểm tra `ForecastCalculator.java` cho cùng kết quả trên từng ca, nên hai bản không thể lệch nhau mà CI không báo.

Tham số cấu hình qua biến môi trường: `AI_FORECAST_ALPHA`, `AI_FORECAST_MA_WINDOW`, `AI_SERVICE_LEVEL_Z`, `AI_LEAD_TIME_DAYS`, `AI_HISTORY_DAYS`, `AI_VALIDATION_DAYS`.

---

## 6. Chỉ số Đánh giá Hiệu năng Mô hình

Mô hình được đo lường dựa trên các chỉ số chuẩn trong bài toán dự báo chuỗi thời gian:

- **MAE (Mean Absolute Error):** Sai số tuyệt đối trung bình giữa giá trị dự báo và thực tế:
  $$MAE = \frac{1}{n} \sum_{t=1}^n |y_t - \hat{y}_t|$$
- **RMSE (Root Mean Square Error):** Đo mức độ nhạy với các biến động doanh số lớn:
  $$RMSE = \sqrt{\frac{1}{n} \sum_{t=1}^n (y_t - \hat{y}_t)^2}$$

---

## 7. Hướng Phát triển Mở rộng (Future Work)

1. **Tích hợp thêm đặc trưng ngoại cảnh:** Bổ sung biến ngày lễ/Tết, khuyến mãi, thời tiết (mưa/nắng ảnh hưởng nước giải khát).
2. **Mô hình học máy LightGBM / XGBoost:** Chạy microservice Python độc lập khi lượng dữ liệu tích lũy trên 6 tháng.
3. **Chatbot tư vấn trợ lý kho (GenAI):** Tích hợp LLM tổng hợp báo cáo ngôn ngữ tự nhiên: *"Hôm nay mặt hàng nào có nguy cơ đứt hàng cao nhất?"*.
