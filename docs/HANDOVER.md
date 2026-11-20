# Ghi chú bàn giao KLTN POS

Cập nhật: **20/11/2026**, theo nhánh `dev` tại commit `578b365` (sau PR #65).
Đọc file này trước tiên nếu bạn là dev hoặc trợ lý AI (Codex, Copilot...) mới tiếp quản dự án.

---

## 1. Mốc thời gian quan trọng

| Ngày | Việc |
|---|---|
| **06/12/2026** | Hạn nộp báo cáo khóa luận cho GVHD |
| 07/12 – 18/12/2026 | Bộ môn phản biện đánh giá đề tài |
| trước 20/12/2026 | Khoa công bố danh sách nhóm bảo vệ trước Hội đồng / báo cáo Poster |
| **Sáng thứ Tư 23/12/2026** | Bảo vệ chính thức (trực tiếp, trước Hội đồng hoặc Poster) |

Từ giờ đến 06/12 nên **ưu tiên tài liệu và ổn định demo**, hạn chế thêm tính năng lớn.

---

## 2. Dự án là gì

Hệ thống POS + quản lý kho cho cửa hàng bán lẻ nhỏ, nhiều chi nhánh, có dự báo nhu cầu (AI) và gợi ý nhập hàng.

| Phần | Thư mục | Công nghệ |
|---|---|---|
| Backend | `BACKEND/` | Java 21, Spring Boot 3.3.3, JPA, Spring Security (JWT), WebSocket STOMP, Flyway, Apache POI |
| Ứng dụng quầy + quản trị | `FRONTEND/` | React 19 + Vite 8, chạy web (`npm run dev`) hoặc desktop Electron 44 |
| App điện thoại | `MOBILE/` | Expo 58 / React Native (JS thuần, không Expo Router): quét mã gửi vào giỏ của quầy, tra giá/tồn |
| Kiểm chứng dự báo | `AI_FORECAST/` | Python 3.12 + pytest; **không** cần để hệ thống chạy |
| Database | Docker `pos-mysql` | MySQL 8.0, database `pos_inventory` |
| Dữ liệu mẫu | `data/bhx/`, `data/demo/` | 1.949 sản phẩm Bách Hóa Xanh, 18 NCC, lịch sử bán 90 ngày |

Tài liệu đã có trong repo, đọc theo thứ tự:
1. `README.md`: tổng quan, kiến trúc, cấu trúc thư mục, **danh sách API đầy đủ**.
2. `setup.md`: dựng hệ thống từ máy trắng, từng bước (Windows/PowerShell có ghi chú riêng).
3. `AI_for_POS.md`: công thức dự báo (ES α=0.35, MA7, SS, ROP, MAE/RMSE).
4. `docs/tich-diem-doi-diem.md`: nghiệp vụ điểm thành viên.
5. `MOBILE/README.md`, `MOBILE/AGENTS.md`: app điện thoại và hợp đồng API ghép cặp.
6. `.agents/*`: hướng dẫn chuyên đề cho trợ lý AI (backend, frontend, AI, bảo mật...).

---

## 3. Chạy nhanh (máy dev)

```bash
# 0. Tạo file cấu hình (KHÔNG commit các file .env)
cp .env.example .env                    # điền DB_PASSWORD, DB_ROOT_PASSWORD, JWT_SECRET (>=32 ký tự), SEED_DEFAULT_PASSWORD
cp FRONTEND/.env.example FRONTEND/.env  # dev thì để trống VITE_API_BASE_URL / VITE_WS_URL
cp MOBILE/.env.example MOBILE/.env      # EXPO_PUBLIC_SERVER_URL=http://<IP_wifi_máy_tính>:8080

# 1. MySQL + backend (+ container sao lưu)
docker compose up -d                    # hoặc: cd BACKEND && mvn spring-boot:run
# Swagger: http://localhost:8080/swagger-ui/index.html

# 2. Nạp dữ liệu mẫu SAU khi backend chạy lần đầu (backend tạo bảng trước)
docker exec -i pos-mysql mysql -uroot -p"$DB_ROOT_PASSWORD" --default-character-set=utf8mb4 pos_inventory < data/bhx/import_bhx_data.sql   # XÓA và nạp lại product/inventory
docker exec -i pos-mysql mysql -uroot -p"$DB_ROOT_PASSWORD" --default-character-set=utf8mb4 pos_inventory < data/demo/lich_su_ban_90_ngay.sql # hóa đơn [DEMO], chạy lại được

# 3. Frontend
cd FRONTEND && npm install && npm run dev        # http://localhost:5173
npm run dev:electron                             # bản desktop
npm run electron:build                           # đóng gói .exe (build trên Windows, phải đặt VITE_API_BASE_URL + VITE_WS_URL)

# 4. Mobile
cd MOBILE && npm install && npx expo start       # điện thoại phải cùng Wi-Fi với máy chạy backend
```

Windows PowerShell không hỗ trợ `<`: dùng `cmd /c "docker exec -i pos-mysql mysql ... < data\bhx\import_bhx_data.sql"`.

Tài khoản mẫu: `admin` (ADMIN) và `cashier` (STAFF, Chi nhánh Quận 1), mật khẩu = `SEED_DEFAULT_PASSWORD`. Ba vai trò: `ADMIN`, `MANAGER`, `STAFF`.

### Kiểm tra trước khi mở PR (giống CI `.github/workflows/ci.yml`)

```bash
cd BACKEND && mvn -B test                                  # ~181 test JUnit
cd FRONTEND && npm run lint && npm test && npm run build   # ESLint + Vitest + build
cd MOBILE && npx expo export --platform android --output-dir dist
cd AI_FORECAST && pip install -r requirements-dev.txt && python -m pytest -q
```

---

## 4. Cấu hình môi trường cần nhớ

Mọi bí mật lấy từ `.env`, không viết cứng trong code. Chi tiết từng biến có chú thích trong `.env.example`.

| Biến | Ghi chú |
|---|---|
| `DB_PASSWORD`, `DB_ROOT_PASSWORD`, `JWT_SECRET`, `SEED_DEFAULT_PASSWORD` | Bắt buộc. Thiếu là backend / compose dừng ngay và báo tên biến |
| `JPA_DDL_AUTO=update`, `FLYWAY_ENABLED=false` | Mặc định dev. Môi trường thật: `FLYWAY_ENABLED=true` + `JPA_DDL_AUTO=validate` |
| `POS_REQUIRE_OPEN_SHIFT=true` | Thu ngân phải mở ca mới bán được |
| `POS_CASHIER_MAX_DISCOUNT_PERCENT`, `POS_RETURN_DAYS` | Hạn mức giảm giá, số ngày đổi trả (vượt thì cần PIN quản lý) |
| `ENABLE_AI_ASSISTANT`, `GEMINI_API_KEY` | Tùy chọn. Gemini chỉ đoán loại câu hỏi cho trợ lý; số liệu luôn do backend tính |
| `SCANNER_REQUIRE_SAME_NETWORK` | Điện thoại phải cùng mạng với máy POS. Chạy backend trong Docker trên chính máy POS sẽ bị chặn (IP 172.x): chạy `mvn spring-boot:run` hoặc tạm đặt `false` |
| `VITE_VIETQR_*` (FRONTEND/.env) | Tài khoản nhận tiền VietQR (PR #64). Để trống dùng tài khoản mẫu |
| `VITE_DEMO_PASSWORD` | Điền sẵn mật khẩu ở màn đăng nhập, chỉ dùng khi demo |

Mọi biến `VITE_*` và `EXPO_PUBLIC_*` bị nhúng vào bundle, ai cũng đọc được: không đặt bí mật vào đó.

---

## 5. Các module nghiệp vụ chính

Backend chia theo tính năng trong `BACKEND/src/main/java/com/pos/<module>/`; frontend tương ứng ở `FRONTEND/src/pages/<module>/` và `FRONTEND/src/services/`.

| Nghiệp vụ | Backend | Điểm cần biết |
|---|---|---|
| Bán hàng (POS) | `sale/`, `approval/` | Trừ kho nguyên tử, chống gửi trùng bằng `clientRequestId`; giảm giá vượt hạn mức và hoàn trả cần PIN quản lý; trả hàng từng món |
| Ca làm, két | `shift/`, `staff/` | Lịch ca theo chi nhánh, mở/đóng ca, thu chi, đối soát, duyệt ca lệch tiền |
| Kho | `inventory/`, `purchase/`, `expiry/` | Phiếu nhập (số lô, hạn dùng), điều chuyển chi nhánh, cảnh báo cận date |
| Nhà cung cấp + danh mục | `purchase/` (`SupplierCategoryRule`) | **Quy tắc chung**: NCC chỉ được nhập/đặt đúng danh mục mình bán. Dùng lại `SupplierCategoryRule`, không viết quy tắc riêng |
| Đơn đặt hàng NCC | `purchaseorder/` | Nháp → Gửi → Nhận (đủ hoặc từng phần, mỗi lần nhận tạo phiếu nhập có `purchase_order_id`) → Hoàn tất/Hủy. Lập từ đề xuất AI, tách đơn theo danh mục, xóa dòng, bắt buộc ngày giao khi gửi (PR #65). Không cho nhận vượt số đặt |
| Kiểm kê | `stockcount/` | Quét đếm, lý do chênh lệch, quản lý duyệt mới điều chỉnh tồn; báo cáo hao hụt |
| Dự báo AI | `forecast/` | `ForecastCalculator` (công thức), job 02:00 hằng đêm lưu `forecast_result`. Bản Python ở `AI_FORECAST/` phải cho cùng kết quả (`ForecastParityTest`) |
| Trợ lý hỏi đáp | `assistant/` | Luật từ khóa trước, Gemini dự phòng chỉ phân loại câu hỏi |
| Khách hàng, điểm, khuyến mãi | `customer/`, `loyalty/`, `promotion/` | Xem `docs/tich-diem-doi-diem.md` |
| Báo cáo, nhật ký | `report/`, `audit/` | Excel nhiều trang (Apache POI); audit log cho thao tác nhạy cảm |
| Ghép điện thoại | `scanner/` | Ghép bằng QR, kênh `/topic/terminals/{id}/scans` |
| Bảo mật, chi nhánh | `security/`, `branch/`, `user/`, `auth/` | Chi nhánh nằm trong JWT; đổi chi nhánh phải đăng nhập lại |

Database: schema theo Flyway ở `BACKEND/src/main/resources/db/migration/` (V1 baseline → **V4** `stock_in.purchase_order_id`). ERD và sơ đồ ở thư mục dự án `erd/` và `tai-lieu-kltn/so-do/` (file `.mmd` + `.png`).

---

## 6. Quy ước làm việc (bắt buộc)

1. **Mọi PR mở vào nhánh `dev`** (base = `dev`), không mở vào `main`. Chỉ merge `dev` → `main` khi chủ dự án yêu cầu.
2. Đặt tên nhánh theo việc, commit message dạng `feat(module): ...`, `fix(module): ...` (tiếng Việt được).
3. Chạy đủ các lệnh kiểm tra ở mục 3 trước khi mở PR; không tắt hay bỏ qua test để CI xanh.
4. **Frontend không dùng `window.alert/confirm/prompt`**: dùng `toast`, `confirmDialog`, `promptDialog` trong `FRONTEND/src/components/common/toastStore.js`. Electron không hỗ trợ `prompt()` nên nút sẽ không làm gì.
5. **Thêm bảng/cột** thì: thêm file Flyway mới `V5__...sql` (không sửa file cũ), cập nhật SQL dữ liệu mẫu (`data/bhx/*.sql`, `data/demo/*.sql`) để dữ liệu luôn đầy đủ. `nha_cung_cap.sql` cũng được chép ở cuối `import_bhx_data.sql`: sửa thì sửa cả hai.
6. **Sửa code thì sửa luôn tài liệu liên quan**: `README.md`, `setup.md`, `.env.example`, và file khóa luận (`tai-lieu-kltn/KLTN_Nhom047_POS.docx`) nếu thay đổi chạm tới use case, màn hình hay mô hình dữ liệu.
7. Không commit `.env`, bản sao lưu DB, hay khóa API.
8. Giao diện và thông báo viết tiếng Việt có dấu, câu ngắn, dễ hiểu cho nhân viên cửa hàng.

---

## 7. Trạng thái hiện tại

### Đã xong (đã merge vào `dev`)
- Toàn bộ Giai đoạn 0–6b trong README: POS, kho, đa chi nhánh realtime, ca làm và két, app quét mã, phân quyền 3 vai trò, dự báo AI + đặt hàng từ đề xuất, trả hàng một phần, kiểm kê, audit log, lô/hạn dùng, Excel, Flyway, sao lưu tự động, test frontend Vitest, CI 4 phần.
- Gần đây: danh mục NCC (#59), tách đơn theo danh mục (#60), khuyến mãi cập nhật ngay ở POS (#61), nhập kho đồng bộ đơn đặt hàng (#63), cấu hình VietQR qua `.env` (#64), xóa dòng và bắt buộc ngày giao trong đơn đặt hàng (#65, merge 20/11).

### Nhánh và PR
- **Không còn PR nào đang mở** (kiểm tra 20/11/2026).
- `dev` đi trước `main` **9 commit** (PR #63, #64, #65 chưa lên `main`). `main` mới nhất là PR #62.

### Đang dở / cần lưu ý
- Đang có một luồng việc làm **file SQL dữ liệu mẫu đầy đủ tới ngày báo cáo** (để báo cáo và dự báo nhìn đẹp). Kiểm tra PR mới nhất trên GitHub trước khi sửa `data/demo/`.
- File khóa luận đã cập nhật tới PR #63. **Chưa chắc đã phản ánh PR #64 (VietQR) và #65 (xóa dòng, ngày giao bắt buộc)**: rà lại mục màn hình Đặt hàng và bảng test case.
- Mục "Trạng thái dự án" trong `README.md` ghi ngày 05/11 và "174 test"; thực tế khoảng 181 test backend. Cập nhật khi chốt.

---

## 8. Vấn đề đã biết

| Vấn đề | Ảnh hưởng | Gợi ý |
|---|---|---|
| Topic WebSocket tồn kho dùng chung mọi chi nhánh, client tự lọc | Tốn băng thông khi nhiều chi nhánh | Ghi vào "hướng phát triển" |
| Z = 1,65 trên dữ liệu thật chỉ đạt mức phục vụ ~89,7% (mục tiêu 95%); α = 0,1 tốt hơn α = 0,35 | Có thể bị hội đồng hỏi | Xem `du-bao-du-lieu-that/KET_QUA.md` trong thư mục dự án; trả lời bằng số liệu này |
| Nhãn độ tin cậy dự báo quá chặt (66% bị LOW) | Giao diện dự báo trông "kém tin cậy" | Nới ngưỡng nếu còn thời gian |
| Backend chạy trong Docker trên chính máy POS thì điện thoại bị chặn quét (khác dải IP) | Lỗi khi demo | Demo bằng `mvn spring-boot:run` hoặc `network_mode: host`, mang Wi-Fi riêng (mạng trường hay chặn LAN) |
| Chưa có giá theo chi nhánh, hóa đơn điện tử, bán offline thật | Thiếu so với POS thương mại | Để ở "hướng phát triển" |
| Chưa thử nghiệm tại cửa hàng thật (Giai đoạn 7) | Chuẩn đầu ra ghi "thử nghiệm ≥1 cửa hàng" | Nếu không kịp, ghi rõ là mô phỏng với dữ liệu 1.949 sản phẩm BHX, nêu trong phần hạn chế |

---

## 9. Việc nên làm tiếp (theo thứ tự)

1. **Trước 06/12 – báo cáo GVHD**
   - Rà file khóa luận theo PR #64, #65 và file SQL dữ liệu mẫu mới; xuất lại PDF.
   - Cập nhật mục Trạng thái trong `README.md`.
2. **Chốt bản nộp**: merge `dev` → `main` (khi chủ dự án đồng ý) và gắn tag, ví dụ `v1.0-baove`.
3. **Chuẩn bị demo trên đúng máy sẽ mang đi**: dựng từ đầu theo `setup.md`, nạp dữ liệu mẫu, build Electron, thử app điện thoại cùng mạng, máy in K80 hoặc xem trước bill.
4. **Slide 15–20 trang + kịch bản demo 8–10 phút**, tập ít nhất 2 lần. Kịch bản và câu hỏi hội đồng hay gặp có sẵn ở `review/san-sang-bao-ve-khoa-luan.md` (thư mục dự án).
5. Nếu còn thời gian: sửa nhãn độ tin cậy dự báo, xem xét α nhỏ hơn hoặc Z cao hơn có số liệu chứng minh.

---

## 10. Tài liệu ngoài repo (thư mục dự án dùng chung)

Các file này nằm ở thư mục dự án (bản sao trên máy: `E:\1.KLTN`), không ở trong Git:

| Thư mục | Nội dung |
|---|---|
| `tai-lieu-kltn/` | Quyển khóa luận `KLTN_Nhom047_POS.docx/.pdf`, các bản trước, `so-do/` (sơ đồ mermaid + ảnh) |
| `review/` | Các bản review: `tong-hop-can-sua-logic-nghiep-vu.md`, `san-sang-bao-ve-khoa-luan.md`, `kltn-pos-full-review.md`... (một số đã cũ, đối chiếu với code) |
| `erd/` | ERD (`.mmd`, `.png`, `.svg`) |
| `du-bao-ton-kho/`, `du-bao-du-lieu-that/` | Báo cáo kết quả dự báo trên dữ liệu mẫu và dữ liệu thật |
| `demo-data/`, các thư mục ảnh khác | Ảnh chụp màn hình từng tính năng, dùng cho báo cáo và slide |

Sơ đồ mermaid được vẽ lại bằng Playwright + gói `mermaid` (theme neutral, deviceScaleFactor 2), vì `mmdc` có thể bị chặn mạng.

---

## 11. Gợi ý khi dùng trợ lý AI khác (Codex...)

- Codex tự đọc file `AGENTS.md` ở gốc repo; file đó trỏ tới tài liệu này. Thư mục `MOBILE/` có `AGENTS.md` riêng.
- Khi giao việc, nói rõ: "mở PR vào nhánh `dev`", "chạy test trước khi xong", "cập nhật tài liệu liên quan".
- Giao từng việc nhỏ, mỗi việc một nhánh và một PR, để dễ review và quay lại khi lỗi.
