# Kiểm chứng mô hình dự báo trên dữ liệu bán hàng thật

Hai thư mục còn lại của `AI_FORECAST` chỉ dùng dữ liệu tự sinh. Thư mục này chạy **đúng công thức đang dùng trong
hệ thống** (Exponential Smoothing, Moving Average, tồn kho an toàn, điểm đặt hàng lại, nhãn độ tin cậy) trên một
bộ dữ liệu bán tạp hóa thật, công khai, để có số đo sai số không phụ thuộc vào cách nhóm sinh dữ liệu.

Kết quả: [`outputs/KET_QUA.md`](outputs/KET_QUA.md).

## Nguồn dữ liệu

| Hạng mục | Chi tiết |
|---|---|
| Tên | The Complete Journey (84.51°), bản đóng gói trong gói R [`completejourney`](https://github.com/bradleyboehmke/completejourney) |
| Giấy phép | CC0 (theo file `DESCRIPTION` của gói) |
| Nội dung | 1.469.307 dòng giao dịch của 2.469 hộ gia đình tại một chuỗi siêu thị Mỹ, cả năm 2017 |
| Đã dùng | 300 sản phẩm có nhiều ngày bán nhất (230–364 ngày), gộp số bán theo ngày trên toàn chuỗi |
| Trong repo | `data/sales_daily.csv.gz` (87.729 dòng: ngày, sản phẩm, số bán) và `data/products.csv`; file gốc không đưa vào git |

Giới hạn cần nói rõ khi trích kết quả: đây là dữ liệu Mỹ, là mẫu theo hộ gia đình chứ không phải toàn bộ doanh số
một cửa hàng, chỉ có một năm và không có tồn kho. Nó kiểm chứng phương pháp, không thay cho dữ liệu của cửa hàng
dùng hệ thống.

## Cách đánh giá

Tại mỗi ngày gốc trong năm (329 ngày), với mỗi sản phẩm: lấy 30 ngày lịch sử, dự báo một mức nhu cầu/ngày, so với
7 ngày thật kế tiếp. Đây là cách `ForecastService` dùng dự báo, lặp lại 329 lần thay vì một lần, nên kết quả ổn
định hơn nhiều so với MAE trên một cửa sổ 7 ngày. `evaluate.py` tự kiểm tra bản tính vector hóa khớp với công
thức gốc trong `pos_forecast` (cũng là công thức của `ForecastCalculator.java`).

| File | Việc |
|---|---|
| `download.py` | Tải file gốc từ GitHub vào `data/raw/` |
| `prepare.py` | Lọc, chọn 300 sản phẩm, gộp thành doanh số theo ngày |
| `evaluate.py` | Backtest, so sánh mô hình, thử các giá trị α, mức phục vụ, nhãn độ tin cậy |
| `report.py` | Ghi `outputs/KET_QUA.md` và biểu đồ |

## Chạy

```bash
cd AI_FORECAST/real_data_eval
pip install -r requirements.txt
python evaluate.py                        # dùng dữ liệu đã chuẩn hóa có sẵn, chạy vài giây
python download.py && python prepare.py   # chỉ khi muốn dựng lại dữ liệu từ nguồn gốc
```
