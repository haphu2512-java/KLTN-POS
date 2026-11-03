# Báo cáo dự báo nhu cầu và tồn kho

> **Lưu ý: toàn bộ số liệu trong báo cáo này là DỮ LIỆU MẪU tự sinh** để trình diễn quy trình (tên và giá sản phẩm lấy từ danh mục Bách Hóa Xanh của dự án, còn số bán và tồn kho là mô phỏng). Không dùng các con số này để ra quyết định thật. Khi có dữ liệu thật từ hệ thống POS, chạy lại đúng quy trình này là ra báo cáo tương ứng.

Phạm vi: 80 sản phẩm, 2 chi nhánh, dữ liệu theo ngày từ 28/10/2024 đến 25/10/2026 (104 tuần). Dự báo 12 tuần tới (26/10/2026 – 17/01/2027).

## 1. Tóm tắt cho quản lý

- **Tuần này cần đặt 104 mặt hàng, tổng giá trị khoảng 260,2 triệu đồng** (theo giá vốn). Danh sách chi tiết ở sheet *Đề xuất đặt hàng* của file Excel.
- **55 mặt hàng–chi nhánh có nguy cơ hết hàng**, trong đó 14 đang hết hàng ngay lúc này. Đây là việc cần xử lý trước tiên.
- **8 mặt hàng–chi nhánh tồn kho quá mức** (đủ bán trên 8 tuần), đang giữ khoảng 151,8 triệu đồng vốn không cần thiết.
- Trong 2 năm qua cửa hàng hết hàng khoảng **6,7% số ngày bán**, ước tính mất khoảng **1,68 tỷ đồng doanh thu** vì khách muốn mua mà không có hàng.
- Mô hình dự báo được chọn sai lệch trung bình **28,7%** mỗi tuần trên từng mặt hàng, tốt hơn cách đang làm (lấy trung bình 8 tuần gần nhất, sai 32,6%).

## 2. Phát hiện chính

**Xu hướng.** Doanh thu 52 tuần gần nhất đạt 19,88 tỷ đồng, tăng 6,3% so với 52 tuần trước đó. Có 37 mặt hàng đang tăng trưởng trên 10%/năm và 19 mặt hàng giảm trên 10%/năm.

**Mùa vụ.** Tết là đỉnh lớn nhất trong năm: tuần cao điểm trước Tết bán gấp khoảng 1,3 lần tuần bình thường, rồi giảm mạnh trong tuần nghỉ Tết. Bánh kẹo, bia, nước ngọt, dầu ăn, nước mắm tăng mạnh nhất. Nước giải khát bán chạy hơn vào mùa nóng (tháng 3–5), mì gói và đồ ăn liền bán tốt hơn vào mùa mưa. Cuối tuần (thứ Bảy, Chủ nhật) bán nhiều hơn ngày thường khoảng 22,7%. Các đợt 11/11 và 12/12 có khuyến mãi làm số bán tăng rõ rệt.

**Hết hàng làm số liệu bán bị thấp hơn nhu cầu thật.** Vào những tuần có hết hàng, số bán ghi nhận thấp hơn nhu cầu thật khoảng 35,0%. Sau khi làm sạch (ước lượng lại phần khách muốn mua nhưng không có hàng), sai lệch này còn khoảng 14,2%. Nếu dùng thẳng số bán để dự báo, cửa hàng sẽ tiếp tục nhập thiếu và lại hết hàng.

**Dữ liệu.** Đã xử lý 1.169 ngày mất bản ghi, 553 ô số lượng bị trống và 213 giá trị nhập sai (ví dụ bán nhiều hơn số hàng đang có). Các ngày nghỉ Tết được ghi nhận là nghỉ chứ không phải thiếu dữ liệu.

**Phân nhóm sản phẩm (ABC-XYZ).** Chỉ 22 mặt hàng (nhóm A) đã tạo ra 80% doanh thu. Phần lớn mặt hàng có nhu cầu khá ổn định (nhóm X); 9 mặt hàng bán rất thất thường hoặc lâu lâu mới bán (nhóm Z) – những mặt hàng này khó dự báo, nên giữ tồn kho tối thiểu và đặt theo đơn khi có thể.

| Nhóm | Ý nghĩa | Số mặt hàng |
|---|---|---|
| AX | doanh thu cao, bán đều | 21 |
| AY | doanh thu cao, dao động vừa | 1 |
| BX | doanh thu trung bình, bán đều | 20 |
| BY | doanh thu trung bình, dao động vừa | 1 |
| CX | doanh thu thấp, bán đều | 24 |
| CY | doanh thu thấp, dao động vừa | 4 |
| CZ | doanh thu thấp, thất thường | 9 |

## 3. Độ chính xác dự báo

Cách kiểm tra: giả vờ đang ở 3 thời điểm trong quá khứ, chỉ cho mô hình xem dữ liệu trước thời điểm đó, bắt dự báo 12 tuần tiếp theo rồi so với những gì đã thực sự xảy ra. **WAPE** là tổng sai lệch chia tổng số bán (WAPE 30% nghĩa là sai trung bình 30 đơn vị trên mỗi 100 đơn vị bán). **Bias** dương nghĩa là dự báo thường cao hơn thực tế (thừa hàng), âm là thấp hơn (thiếu hàng).

| Mô hình | Giải thích ngắn | WAPE | Bias |
|---|---|---|---|
| MA8 | Trung bình 8 tuần gần nhất (cách làm thủ công phổ biến) | 35,3% | +5,1% |
| SNaive | Lấy số bán cùng tuần năm trước | 43,8% | -4,0% |
| ETS | Làm trơn hàm mũ có xu hướng | 35,4% | +5,6% |
| TSB | Croston cải tiến, dành cho hàng bán không đều | 33,8% | +3,6% |
| LightGBM | Học máy, hiểu được Tết, khuyến mãi, giá, mùa vụ | 29,5% | +3,5% |

**LightGBM** chính xác nhất, giảm sai số khoảng 16,3% so với cách lấy trung bình 8 tuần. Cách "lấy cùng kỳ năm trước" kém nhất vì Tết mỗi năm rơi vào một tuần khác nhau. Mô hình được chọn riêng cho từng nhóm ABC-XYZ (xem sheet *So sánh mô hình*); khi kiểm tra trung thực trên giai đoạn cuối (không dùng để chọn mô hình), cách chọn theo nhóm đạt WAPE 28,7%.

Độ chính xác khác nhau nhiều giữa các nhóm: nhóm A/X sai khoảng 28,0%, còn nhóm C/Z sai trên 141,3% – hàng bán lẻ tẻ thì không mô hình nào đoán chính xác được từng tuần, nên với nhóm này phải dựa vào tồn kho an toàn chứ không dựa vào con số dự báo.

## 4. Khuyến nghị hành động

**Ngay trong tuần này**

1. Đặt hàng theo sheet *Đề xuất đặt hàng* (104 dòng, khoảng 260,2 triệu đồng). Số lượng đã tính sẵn tồn kho an toàn cho mức phục vụ 95% và làm tròn theo thùng.
2. Xử lý các mặt hàng nguy cơ hết hàng cao nhất (bảng dưới). Với hàng đang hết, xin nhà cung cấp giao gấp hoặc điều chuyển từ chi nhánh còn dư.

| Chi nhánh | Mã | Sản phẩm | Cảnh báo | Tồn | Dự báo tuần tới | Nên đặt |
|---|---|---|---|---|---|---|
| Chi nhánh 2 | SP0034 | Tắm gội 3 trong 1 X-Men Everest Coffee 610g | Đang hết hàng | 0 | 29,1 | 78 |
| Chi nhánh 1 | SP0053 | Dầu gội Head & Shoulders gàu dai dẳng và da đ | Đang hết hàng | 0 | 7,1 | 24 |
| Chi nhánh 2 | SP0050 | Dầu gội Dove Biotin ngăn tóc gãy rụng 340ml | Đang hết hàng | 0 | 136,8 | 420 |
| Chi nhánh 1 | SP0069 | Lốc 4 hộp sữa lúa mạch socola Lof Kun có thạc | Đang hết hàng | 0 | 9,1 | 48 |
| Chi nhánh 2 | SP0073 | 6 lon nước tăng lực Redbull Thái kẽm và vitam | Nguy cơ hết hàng rất cao | 17 | 56,6 | 192 |
| Chi nhánh 1 | SP0064 | Thùng 10 lốc mì cay Koreno Jumbo 7 cấp độ vị  | Đang hết hàng | 0 | 2,5 | 8 |
| Chi nhánh 1 | SP0058 | Lốc 4 chai sữa chua uống men sống không đườn | Nguy cơ hết hàng rất cao | 4 | 34,3 | 72 |
| Chi nhánh 2 | SP0071 | Combo 6 lon nước trái cây lên men Chill Rose  | Nguy cơ hết hàng rất cao | 1 | 1,7 | 6 |
| Chi nhánh 1 | SP0045 | Bánh bouchee Lotte Chocolat vị phô mai hộp 1 | Nguy cơ hết hàng rất cao | 0 | 0,3 | 3 |
| Chi nhánh 2 | SP0041 | 6 lon nước ngọt Coca Cola Plus 320ml | Nguy cơ hết hàng rất cao | 0 | 0,1 | 2 |
| Chi nhánh 1 | SP0021 | Bánh bông lan tầng kem phô mai Solite gói 115 | Nguy cơ hết hàng rất cao | 9 | 11,2 | 30 |
| Chi nhánh 2 | SP0062 | Snack khoai tây vị thơm cay Slide lon 60g | Nguy cơ hết hàng rất cao | 4 | 4,7 | 18 |

3. Ngừng nhập và cân nhắc khuyến mãi hoặc điều chuyển các mặt hàng tồn dư:

| Chi nhánh | Mã | Sản phẩm | Số tuần tồn | Vốn tồn dư |
|---|---|---|---|---|
| Chi nhánh 1 | SP0049 | Thùng 30 gói mì Mama hương tôm sốt kem Tom Yu | 15,7 | 127,9 triệu đồng |
| Chi nhánh 1 | SP0075 | Trà Cozy Ice Tea hương đào hộp 180g (12 gói x | 14,9 | 17,6 triệu đồng |
| Chi nhánh 1 | SP0071 | Combo 6 lon nước trái cây lên men Chill Rose  | 19,8 | 3,7 triệu đồng |
| Chi nhánh 2 | SP0042 | Thùng 24 lon nước tăng lực Redbull Thái 250ml | 9,1 | 1,0 triệu đồng |
| Chi nhánh 1 | SP0072 | Nước tăng lực Lipovitan mật ong lon 250ml | 12,1 | 829 nghìn đồng |
| Chi nhánh 1 | SP0055 | Snack socola Oishi Pillows gói 80g | 12,2 | 720 nghìn đồng |
| Chi nhánh 1 | SP0067 | Sữa chua uống tiệt trùng hương kem dâu Lof Ku | 11,8 | 5 nghìn đồng |
| Chi nhánh 2 | SP0067 | Sữa chua uống tiệt trùng hương kem dâu Lof Ku | 11,3 | 3 nghìn đồng |

**Trong 1–3 tháng tới**

4. Chạy lại dự báo mỗi tuần (trước buổi đặt hàng thứ Hai) để số đề xuất luôn theo kịp tình hình mới.
5. Chuẩn bị Tết 2027 (mùng 1 ngày 06/02/2027): nhu cầu bắt đầu tăng từ khoảng giữa tháng 1. Kỳ dự báo này mới phủ đến giữa tháng 1, nên đầu tháng 1 cần chạy lại để có số chính xác cho mùa Tết, và làm việc sớm với nhà cung cấp bánh kẹo, bia, nước ngọt.
6. Kiểm tra với nhà cung cấp: 6 mặt hàng–chi nhánh đang hết hàng mà không có đơn nào đang trên đường về, cần hỏi lý do (nhà cung cấp hết hàng, giao trễ hay quên đặt).
7. Với nhóm A (ít mặt hàng nhưng mang lại phần lớn doanh thu): ưu tiên không để hết hàng, xem xét đặt 2 lần/tuần. Với nhóm C/Z: giảm tồn, đặt ít và thường xuyên hơn.
8. Ghi nhận đầy đủ ngày hết hàng và kế hoạch khuyến mãi trong POS, vì đây là hai thông tin giúp dự báo chính xác hơn nhiều.

## 5. Giả định và giới hạn

- **Dữ liệu:** DỮ LIỆU MẪU tự sinh, có chủ đích chứa xu hướng, mùa vụ, Tết, 11/11, 12/12, khuyến mãi, hết hàng, hàng bán chậm và lỗi nhập liệu. Tên/giá sản phẩm lấy từ danh mục Bách Hóa Xanh của dự án.
- **Ngành hàng:** Bán lẻ tạp hóa / bách hóa (cửa hàng dùng KLTN POS); bỏ qua hàng tươi sống (rau, thịt, cá, trái cây) vì hạn dùng ngắn.
- **Quy mô:** 80 SKU x 2 chi nhánh; dữ liệu theo ngày 28/10/2024–25/10/2026 (104 tuần), dự báo theo tuần.
- **Lead time:** 7 ngày từ lúc đặt đến lúc hàng về; đặt hàng mỗi thứ Hai (chu kỳ xem xét 7 ngày).
- **Mức phục vụ:** 95% (xác suất không hết hàng trong một chu kỳ), hệ số z = 1,645.
- **Tầm dự báo:** 12 tuần, khoảng tin cậy 95% lấy từ sai số backtest của chính mô hình (giả định sai số phân phối chuẩn, tăng dần theo tầm dự báo).
- **Mùa vụ:** Tết Nguyên Đán (3 tuần trước Tết tăng mạnh, nghỉ mùng 1–3), 11/11, 12/12, 30/4–1/5, 2/9, mùa nóng tháng 3–5, mùa mưa tháng 5–11.
- **Khuyến mãi & giá:** Lịch khuyến mãi và giá bán 12 tuần tới được coi là đã biết trước (kế hoạch marketing).
- **Làm sạch:** Ngày hết hàng: nhu cầu = max(số bán, nhu cầu kỳ vọng từ các ngày sạch quanh đó). Ngày mất dữ liệu, ô trống, giá trị vô lý (bán > tồn) hoặc ngoại lai thống kê: thay bằng nhu cầu kỳ vọng.
- **ABC-XYZ:** ABC theo doanh thu 52 tuần (A: 80% đầu, B: 15% tiếp, C: 5% cuối); XYZ theo hệ số biến thiên nhu cầu tuần (X < 0,5 ≤ Y < 1,0 ≤ Z).
- **Backtest:** 3 gốc thời gian (tuần 68, 80, 92), mỗi gốc dự báo 12 tuần, không xáo trộn dữ liệu. Đánh giá trên nhu cầu đã làm sạch.
- **Tồn kho:** SS = z·σ·√L; ROP = dự báo trong L + SS; mức tồn mục tiêu S = dự báo trong (L+R) + z·σ·√(L+R); SL đặt = S − (tồn + hàng đang về), làm tròn lên theo quy cách thùng. σ là độ lệch chuẩn sai số dự báo 1 tuần.
- **Cảnh báo:** Nguy cơ hết hàng: tồn + hàng đang về < ROP (rất cao nếu < nhu cầu trong lead time). Tồn kho quá mức: đủ bán > 8 tuần.
- **Giới hạn:** Chưa tính hạn sử dụng, sức chứa kho, chiết khấu theo số lượng, chi phí đặt hàng (EOQ) và ảnh hưởng thay thế giữa các sản phẩm.

## 6. Các file kèm theo

- `outputs/ket_qua_du_bao_ton_kho.xlsx`: dự báo theo SKU, tồn kho an toàn, ROP, đề xuất đặt hàng, cảnh báo.
- `outputs/charts/top10_thuc_te_vs_du_bao.png`: thực tế và dự báo cho 10 mặt hàng doanh thu cao nhất.
- `outputs/charts/tong_quan_nhu_cau.png`, `outputs/charts/so_sanh_mo_hinh.png`.
