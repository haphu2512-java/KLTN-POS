# Hệ thống POS & Quản lý Kho tích hợp AI Dự báo Tồn kho cho Cửa hàng Bán lẻ nhỏ

## 1. Giới thiệu

Hệ thống giúp cửa hàng bán lẻ nhỏ:

- Bán hàng tại quầy (POS): tạo hóa đơn, thanh toán, in biên lai.
- Quản lý kho: nhập, xuất, kiểm kê, theo dõi tồn kho theo thời gian thực.
- Dự báo nhu cầu bằng AI: dự đoán số lượng bán của từng sản phẩm trong 7-30 ngày tới, từ đó gợi ý số lượng cần nhập và cảnh báo sắp hết hàng.

## 2. Hướng tiếp cận AI

Bài toán dự báo tồn kho là **dự báo chuỗi thời gian** (time series forecasting), không phải bài toán ngôn ngữ. Vì vậy:

| Phương án | Quyết định | Lý do |
|---|---|---|
| Train LLM từ đầu | Không dùng | Dữ liệu ít, tốn tài nguyên, không cần thiết |
| Chỉ gọi API LLM để dự báo số liệu | Không dùng làm lõi | Kém chính xác, khó kiểm chứng, tốn phí, phụ thuộc internet |
| **Tự train mô hình dự báo nhỏ** | **Dùng (cốt lõi)** | Nhẹ, miễn phí, chạy offline, đánh giá được bằng số liệu |
| LLM qua API key | Tùy chọn (điểm cộng) | Chỉ dùng cho chatbot hỏi đáp và tóm tắt báo cáo |

### Mô hình dự báo (theo thứ tự triển khai)

1. **Baseline:** Moving Average, Exponential Smoothing (Holt-Winters)
2. **Thống kê:** Prophet hoặc ARIMA/SARIMA
3. **Machine Learning:** XGBoost / LightGBM với đặc trưng: thứ trong tuần, ngày lễ, khuyến mãi, giá, độ trễ (lag), trung bình trượt

### Chỉ số đánh giá

- MAE, RMSE, MAPE (hoặc sMAPE khi có sản phẩm bán rất ít)
- So sánh với baseline để chứng minh mô hình có giá trị

## 3. Kiến trúc hệ thống

```
[Giao diện POS / Quản lý kho]
            |
        [Backend API]
            |
      [Database] <--- lịch sử giao dịch, tồn kho
            |
   [Module dự báo (Python)]
            |
  [Quy tắc nghiệp vụ: tồn kho an toàn, điểm đặt hàng lại]
            |
 [Cảnh báo / Gợi ý nhập hàng]
            |
 (Tùy chọn) [LLM API: chatbot, tóm tắt báo cáo]
```

## 4. Chức năng chính

### POS
- Quét mã vạch / tìm sản phẩm
- Tạo, hủy, hoàn trả hóa đơn
- Nhiều phương thức thanh toán
- Báo cáo doanh thu theo ngày/tuần/tháng

### Quản lý kho
- Danh mục sản phẩm, nhà cung cấp
- Phiếu nhập / xuất / điều chỉnh kho
- Kiểm kê, hạn sử dụng (nếu có)
- Cảnh báo dưới mức tồn kho tối thiểu

### AI dự báo
- Dự báo nhu cầu theo từng sản phẩm
- Tính điểm đặt hàng lại: `ROP = nhu cầu TB/ngày x thời gian giao hàng + tồn kho an toàn`
- Gợi ý số lượng cần nhập
- Phương án dự phòng (fallback) bằng moving average khi chưa đủ dữ liệu

### (Tùy chọn) Trợ lý hội thoại
- Hỏi đáp: "Tuần này mặt hàng nào sắp hết?"
- Tóm tắt báo cáo tồn kho bằng ngôn ngữ tự nhiên

## 5. Công nghệ đề xuất

> Điều chỉnh theo công nghệ bạn chọn.

| Thành phần | Gợi ý |
|---|---|
| Backend | Python (FastAPI/Django) hoặc Node.js |
| Frontend | React / Vue |
| Database | PostgreSQL / MySQL / SQLite |
| AI/ML | pandas, scikit-learn, Prophet, statsmodels, LightGBM |
| Trực quan hóa | Matplotlib, Plotly |
| LLM (tùy chọn) | API của nhà cung cấp LLM bất kỳ |

## 6. Dữ liệu

Cửa hàng mới thường chưa có lịch sử bán hàng. Cách xử lý:

- Dùng dataset công khai để huấn luyện/đánh giá: M5 Forecasting (Walmart), Store Item Demand Forecasting (Kaggle).
- Sinh dữ liệu giả lập có tính mùa vụ, xu hướng và nhiễu để demo.
- Khi dữ liệu thật còn ít, dùng moving average; chuyển dần sang mô hình ML khi đủ dữ liệu.

## 7. Cấu trúc thư mục (gợi ý)

```
project/
├── backend/          # API, nghiệp vụ POS và kho
├── frontend/         # Giao diện người dùng
├── forecasting/      # Huấn luyện và dự báo
│   ├── data/
│   ├── notebooks/    # Phân tích, thử nghiệm mô hình
│   ├── models/
│   └── train.py
├── database/         # Schema, migration
├── docs/             # Tài liệu, sơ đồ
└── README.md
```

## 8. Cài đặt và chạy

```bash
# 1. Clone dự án
git clone <repo-url>
cd project

# 2. Cài đặt phụ thuộc (ví dụ Python)
pip install -r requirements.txt

# 3. Khởi tạo database
# (chạy migration / script schema)

# 4. Huấn luyện mô hình dự báo
python forecasting/train.py

# 5. Chạy backend
# (lệnh chạy server tùy framework)
```

## 9. Lộ trình thực hiện

1. Khảo sát nghiệp vụ, thiết kế database
2. Xây dựng POS và quản lý kho cơ bản
3. Chuẩn bị dữ liệu, xây dựng baseline
4. Huấn luyện và so sánh các mô hình dự báo
5. Tích hợp dự báo vào hệ thống, thêm cảnh báo và gợi ý nhập hàng
6. (Tùy chọn) Thêm chatbot LLM
7. Kiểm thử, đánh giá, viết báo cáo

## 10. Hạn chế và hướng phát triển

- Độ chính xác phụ thuộc lượng dữ liệu lịch sử.
- Sản phẩm mới hoặc bán thưa (intermittent demand) cần phương pháp riêng (ví dụ Croston).
- Hướng mở rộng: dự báo theo cụm sản phẩm, tích hợp yếu tố thời tiết/sự kiện, đa cửa hàng.

## 11. Giấy phép

Cập nhật theo yêu cầu của dự án (ví dụ MIT).
