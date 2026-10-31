# Demo dự báo nhu cầu & tối ưu tồn kho (DỮ LIỆU MẪU)

> Toàn bộ số liệu bán hàng/tồn kho trong thư mục này là **dữ liệu mẫu tự sinh**. Tên và giá sản phẩm
> lấy từ `bhx_products_scraped.json` của dự án cho sát thực tế. Không dùng kết quả để ra quyết định thật.

Thư mục này là một bài phân tích độc lập (notebook-style) cho khóa luận, **không thay thế** service
`pos_forecast` (FastAPI) ở thư mục cha. Service hiện tại dùng mô hình nhẹ theo ngày để gọi từ backend;
demo này đi sâu hơn: làm sạch hết hàng, ABC-XYZ, so sánh 5 mô hình bằng backtest, khoảng tin cậy,
chính sách tồn kho và báo cáo cho quản lý.

## Chạy

```bash
cd AI_FORECAST/inventory_demo
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
python run_all.py --regenerate     # ~30 giây: sinh dữ liệu mẫu + chạy toàn bộ quy trình
```

Kết quả nằm trong `outputs/`:

| File | Nội dung |
|---|---|
| `BAO_CAO_QUAN_LY.md` | Báo cáo tiếng Việt cho quản lý: phát hiện chính, độ chính xác, khuyến nghị |
| `ket_qua_du_bao_ton_kho.xlsx` | Tóm tắt, Giả định, Đề xuất đặt hàng (SS, ROP, S, SL đặt), Cảnh báo, Dự báo 12 tuần (tổng + chi tiết có KTC 95%), ABC-XYZ, So sánh mô hình, Chất lượng dữ liệu |
| `charts/top10_thuc_te_vs_du_bao.png` | Thực tế vs dự báo (backtest + 12 tuần tới) cho top 10 SKU, kèm 10 ảnh riêng `topNN_*.png` |
| `charts/tong_quan_nhu_cau.png` | Tổng nhu cầu theo tuần, số bán ghi nhận vs nhu cầu đã làm sạch, dự báo |
| `charts/so_sanh_mo_hinh.png` | WAPE của 5 mô hình |

## Giả định (thay cho các chỗ trống của đề bài)

| Hạng mục | Giả định |
|---|---|
| Ngành hàng | Bán lẻ tạp hóa/bách hóa dùng KLTN POS, bỏ hàng tươi sống |
| Quy mô | 80 SKU x 2 chi nhánh |
| Dữ liệu | Theo ngày 28/10/2024 – 25/10/2026 (104 tuần): ngày, chi nhánh, SKU, số bán, tồn đầu, nhập, tồn cuối, giá, khuyến mãi |
| Lead time / chu kỳ đặt | 7 ngày / đặt mỗi thứ Hai |
| Mức phục vụ | 95% |
| Tầm dự báo | 12 tuần, theo tuần |
| Mùa vụ | Tết Nguyên Đán (nghỉ mùng 1–3), 11/11, 12/12, 30/4–1/5, 2/9, mùa nóng, mùa mưa |
| Khuyến mãi tương lai | Đã biết trước (`data/promo_plan_future.csv`) |

Mọi tham số nằm trong `config.py`.

## Quy trình

| Bước | File | Cách làm |
|---|---|---|
| 0. Dữ liệu mẫu | `generate_data.py` | Nhu cầu Negative Binomial x xu hướng x ngày-trong-tuần x mùa vụ x Tết x KM (co giãn giá, hiệu ứng tích trữ sau KM). Mô phỏng chính sách nhập hàng "theo cảm tính" để có cả hết hàng lẫn tồn dư; cài lỗi mất bản ghi, ô trống, gõ sai. Nhu cầu thật lưu riêng `_true_demand.csv.gz` chỉ để kiểm chứng. |
| 1. Làm sạch | `cleaning.py` | Lấp đủ lịch; đánh dấu nghỉ Tết; phát hiện lỗi vật lý (bán > hàng có) và ngoại lai (trung vị + 8·MAD); nhu cầu kỳ vọng = mức nền trượt 29 ngày x chỉ số thứ x hệ số KM. Ngày hết hàng: nhu cầu = max(số bán, kỳ vọng). |
| 2. ABC-XYZ | `analysis.abc_xyz` | ABC theo doanh thu 52 tuần (80/15/5), XYZ theo CV nhu cầu tuần (0,5 / 1,0), thêm phân loại Syntetos-Boylan (ADI, CV²). |
| 3. Mô hình | `models.py` | MA8, Seasonal Naive (52 tuần), ETS Holt damped, TSB (Croston cải tiến), LightGBM toàn cục dự báo trực tiếp 1–12 tuần với lag/rolling/cùng kỳ năm trước/Tết/11-11/12-12/KM/giá. |
| 4. Backtest | `analysis.backtest/evaluate` | 3 gốc thời gian (tuần 68, 80, 92), mỗi gốc 12 tuần, chỉ học từ quá khứ. MAE, WAPE, Bias. Chọn mô hình theo nhóm ABC-XYZ; đánh giá trung thực: chọn trên 2 gốc đầu, chấm trên gốc cuối. |
| 5. Dự báo | `analysis.final_forecast` | Học lại trên 104 tuần, dự báo 12 tuần; KTC 95% = dự báo ± 1,96·σ (σ = RMSE backtest của chuỗi x hệ số theo tầm dự báo). |
| 6. Tồn kho | `analysis.inventory_plan` | SS = z·σ·√L, ROP = D_L + SS, S = D_(L+R) + z·σ·√(L+R), Q = S − (tồn + đang về) làm tròn theo thùng. |
| 7. Cảnh báo | `analysis._alert` | Đang hết hàng / nguy cơ rất cao (vị thế tồn < nhu cầu trong lead time) / nguy cơ (< ROP) / tồn quá mức (> 8 tuần) / không luân chuyển. |
| 8. Xuất | `outputs.py` | Excel định dạng sẵn, biểu đồ, báo cáo. |

## Dùng với dữ liệu thật

Xuất từ CSDL POS một file `data/sales_daily.csv.gz` cùng cột với file mẫu
(`date, store_id, sku, qty_sold, opening_stock, received_qty, closing_stock, unit_price, list_price, promo_flag, discount_pct`),
`data/products.csv`, `data/promo_plan_future.csv`, `data/on_order.csv`, sửa `START_DATE/END_DATE/TET_DATES` trong
`config.py`, rồi chạy `python run_all.py` (không có `--regenerate`). Bước kiểm chứng với nhu cầu thật tự bỏ qua khi không có `_true_demand.csv.gz`.
