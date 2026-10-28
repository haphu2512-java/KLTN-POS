# Hệ thống POS và Quản lý Kho tích hợp AI Dự báo Tồn kho cho Cửa hàng Bán lẻ nhỏ

## Thông tin đề tài
- **Mô tả:** Giải pháp số hóa cho cửa hàng bán lẻ nhỏ: bán hàng tại quầy (POS), quản lý tồn kho theo thời gian thực, nhiều chi nhánh, ca làm và đối soát két, app điện thoại quét mã vạch, dự báo nhu cầu và gợi ý nhập hàng.
- **Công nghệ sử dụng:**
  - **Backend:** Java 21 + Spring Boot 3.3.3 (REST API + WebSocket STOMP), Spring Data JPA, Spring Security (JWT HS512), Spring Scheduler
  - **Frontend (Desktop):** Electron 44 + React 19 + Vite 8, giao tiếp backend qua Vite Proxy `/api` + STOMP WebSocket
  - **Mobile:** React Native + Expo 58, máy quét mã vạch không dây và tra cứu giá/tồn kho (`MOBILE/`)
  - **Database:** MySQL 8.0 (dữ liệu mẫu 1.949 sản phẩm thực tế lấy từ Bách Hóa Xanh, 2 chi nhánh)
  - **Realtime:** Spring WebSocket STOMP, kênh `/topic/inventory-updates` (tồn kho) và `/topic/terminals/{id}/scans` (mã quét từ điện thoại)
  - **Phần cứng:** In hóa đơn nhiệt K80 qua Electron silent print, máy quét mã vạch HID (buffer < 50ms)
  - **Dự báo AI:** Exponential Smoothing (α = 0.35) + Moving Average 7 ngày, tồn kho an toàn, điểm đặt hàng lại (ROP), đánh giá MAE/RMSE, chạy trong Spring Boot với job hằng đêm; bản Python trong `AI_FORECAST/` dùng để kiểm chứng (xem [`AI_for_POS.md`](AI_for_POS.md))
- **Chuẩn đầu ra:** Phần mềm POS + quản lý kho hoàn chỉnh, thử nghiệm thực tế tại ít nhất 1 cửa hàng.

## Tài liệu trong repo

| File | Nội dung |
|------|----------|
| `README.md` | Tổng quan, cách chạy, kiến trúc, danh sách API |
| [`AI_for_POS.md`](AI_for_POS.md) | Bài toán, công thức và kiến trúc module dự báo AI |
| [`AI_FORECAST/README.md`](AI_FORECAST/README.md) | Bản Python kiểm chứng mô hình dự báo |
| [`FRONTEND/README.md`](FRONTEND/README.md) | Ứng dụng desktop Electron |
| [`MOBILE/README.md`](MOBILE/README.md) | App điện thoại quét mã vạch, API ghép cặp với quầy |
| `.agents/`, `skills/`, `MOBILE/AGENTS.md` | Hướng dẫn cho trợ lý AI khi lập trình (không phải tài liệu người dùng) |

---

## Trạng thái dự án (Cập nhật: 27/10/2026)

```
✅ XONG   Giai đoạn 0 — Chuẩn bị & Lập kế hoạch
✅ XONG   Giai đoạn 1 — Thiết kế hệ thống
✅ XONG   Giai đoạn 2 — Module POS (Bán hàng)
✅ XONG   Giai đoạn 3 — Module Quản lý Kho
✅ XONG   Giai đoạn 4 — Đồng bộ đa chi nhánh (WebSocket STOMP)
✅ XONG   Giai đoạn 5 — Báo cáo & Dự báo AI
✅ XONG   Giai đoạn 6 — Kiểm thử & Mở rộng nghiệp vụ (ca làm, app quét mã, tài khoản)
⏳ CẦN   Giai đoạn 7 — Triển khai thử nghiệm thực tế
⏳ CẦN   Giai đoạn 8 — Hoàn thiện báo cáo đề tài
```

CI (`.github/workflows/ci.yml`) chạy trên mọi PR: `mvn test` cho backend, `lint + build` cho frontend, `expo export` cho mobile và `pytest` cho `AI_FORECAST`.

---

## Hướng dẫn chạy hệ thống nhanh

### Yêu cầu
- Docker Desktop (hoặc JDK 21 + Maven + MySQL 8)
- Node.js 18+
- Python 3.10+ (chỉ khi dùng `AI_FORECAST`)

### Cấu hình môi trường (`.env`)
Mọi cấu hình và bí mật đều lấy từ file `.env`, không có giá trị bí mật nào viết sẵn trong code. Mỗi phần có một file mẫu:

| File | Dùng cho | Cách tạo |
|------|----------|----------|
| `.env` (thư mục gốc) | Backend Spring Boot và Docker Compose | `cp .env.example .env` |
| `FRONTEND/.env` | Vite dev server, React, Electron | `cp FRONTEND/.env.example FRONTEND/.env` |
| `MOBILE/.env` | App quét mã Expo | `cp MOBILE/.env.example MOBILE/.env` |

Biến bắt buộc trong `.env` gốc: `DB_PASSWORD`, `JWT_SECRET` (tối thiểu 32 ký tự, ví dụ `openssl rand -hex 32`), `SEED_DEFAULT_PASSWORD` (lần chạy đầu), và `DB_ROOT_PASSWORD` khi chạy bằng Docker. Thiếu biến bắt buộc thì backend hoặc Docker Compose dừng ngay và báo tên biến còn thiếu. Các tham số dự báo (`AI_LEAD_TIME_DAYS`, `AI_SERVICE_LEVEL_Z`, `AI_HISTORY_DAYS`...) có giá trị mặc định theo `AI_for_POS.md`.

### Khởi động Backend + MySQL
```bash
# Tại thư mục gốc dự án (đã tạo .env)
docker compose up -d

# Hoặc chạy backend trực tiếp (vẫn đọc ../.env)
cd BACKEND && mvn spring-boot:run

# Swagger UI: http://localhost:8080/swagger-ui/index.html
```

### Tài khoản mặc định
Tạo tự động khi database còn trống, mật khẩu là giá trị `SEED_DEFAULT_PASSWORD` trong `.env`.

| Tài khoản | Vai trò |
|-----------|---------|
| `admin` | ADMIN (toàn quyền, chọn được mọi chi nhánh) |
| `cashier` | CASHIER (chỉ bán hàng, được phân công Chi nhánh Quận 1) |

Bốn vai trò: `ADMIN`, `MANAGER`, `CASHIER`, `WAREHOUSE`. Báo cáo dành cho ADMIN/MANAGER; Dự báo dành cho ADMIN/MANAGER/WAREHOUSE.

### Dữ liệu sản phẩm và mã vạch
Database mới chỉ có 8 sản phẩm mẫu. Muốn có 1.949 sản phẩm Bách Hóa Xanh thì nạp `import_bhx_data.sql` sau khi backend đã chạy lần đầu (script này **xóa sạch** bảng `product` và `inventory` rồi nạp lại):

```bash
docker exec -i pos-mysql mysql -uroot -p"$DB_ROOT_PASSWORD" pos_inventory < import_bhx_data.sql
```

Quy tắc mã vạch (`BarcodeRules.java`):
- Mã EAN-8/UPC-A/EAN-13/ITF-14 thật in trên bao bì (đúng số kiểm tra) được giữ nguyên.
- Sản phẩm không có mã thật dùng mã EAN-13 nội bộ của cửa hàng: `20` + ID sản phẩm đủ 10 số + số kiểm tra (vd. sản phẩm #3 → `2000000000039`). In tem ở trang Kho để dán lên hàng.
- Bỏ trống mã vạch khi tạo sản phẩm thì hệ thống tự sinh theo quy tắc trên.
- Lúc khởi động, backend tự đổi các mã cũ không quét được (mã nội bộ BHX, mã giả `8931000000xxx`, mã sai số kiểm tra) sang mã nội bộ. Chỉ đổi cột mã vạch, không xóa hóa đơn hay tồn kho. Tắt bằng `BARCODE_SYNC_ON_STARTUP=false`; quản trị có thể chạy tay qua `POST /api/products/barcodes/sync`.
- Sau khi đồng bộ, mở app điện thoại khi có mạng để tải lại danh mục (danh mục offline cũ vẫn mang mã cũ).

### Chi nhánh và ca làm
- Chi nhánh được chọn ở bước đăng nhập. Tài khoản chỉ chọn được chi nhánh quản trị đã phân công (trang Tài khoản, một người có thể được phân công nhiều chi nhánh); được phân công một chi nhánh thì vào thẳng. Quản trị chọn được mọi chi nhánh.
- Chi nhánh nằm trong token: bán hàng, nhập kho, điều chuyển, mở ca đều ghi vào chi nhánh đó. Muốn đổi chi nhánh thì đăng xuất và đăng nhập lại.
- Đang có ca chưa đóng ở chi nhánh A thì không đăng nhập vào chi nhánh B được: phải vào A đóng ca (đếm két), hoặc nhờ quản lý đóng hộ ở tab Ca làm.
- **Lịch ca:** mỗi chi nhánh có thể có số ca và giờ ca riêng; chi nhánh chưa cấu hình thì dùng lịch ca mẫu chung. Quản lý xếp nhân viên vào từng ca theo ngày.
- **Mở/đóng ca:** mở ca cho nhân viên được xếp (quản lý chọn được người trực thay) với tiền mặt đầu ca. Trong ca ghi được các khoản chi, nộp quỹ giữa ca, bổ sung tiền lẻ. Đóng ca đối soát tiền mặt, VietQR và thẻ; ca lệch tiền được quản lý duyệt và khóa sổ.
- Tiền mặt dự kiến của ca = tiền đầu ca + bán tiền mặt − hoàn trả tiền mặt − chi trong ca − nộp quỹ + bổ sung tiền lẻ. Hoàn trả hóa đơn của ca đã đóng thì tính vào ca đang mở lúc hoàn trả.
- Thu ngân phải có ca đang mở mới tạo được hóa đơn (tắt bằng `POS_REQUIRE_OPEN_SHIFT=false`). Màn hình POS không khóa khi chưa mở ca: chỉ khi thêm hàng lần đầu hoặc thanh toán mới hiện hộp nhắc mở ca.

### Khởi động Frontend
```bash
cd FRONTEND
npm install
npm run dev          # Giao diện web: http://localhost:5173
npm run dev:electron # Chạy Electron desktop app (cần backend đã chạy)
```

> Khi chạy `npm run dev`, frontend dùng Vite Proxy (`/api`, `/ws` → `DEV_PROXY_TARGET`, mặc định `http://localhost:8080`) nên không gặp lỗi CORS. Khi build Electron phải đặt `VITE_API_BASE_URL` và `VITE_WS_URL` trong `FRONTEND/.env`.

### Khởi động app điện thoại và AI_FORECAST
```bash
cd MOBILE && npm install && npx expo start          # xem MOBILE/README.md
cd AI_FORECAST && pip install -r requirements-dev.txt && python -m pytest   # xem AI_FORECAST/README.md
```

---

## Giai đoạn 0 — Chuẩn bị & Lập kế hoạch
- [x] Khảo sát quy trình bán hàng, nhập/xuất kho tại cửa hàng bán lẻ nhỏ
- [x] Xác định phạm vi MVP
- [x] Khởi tạo dự án Spring Boot (Maven, Modular Monolith theo tính năng)
- [x] Khởi tạo dự án Electron + React 19 + Vite 8
- [x] Thiết kế kiến trúc hệ thống tổng thể

## Giai đoạn 1 — Thiết kế hệ thống
- [x] Thiết kế database MySQL 8 (bảng do Hibernate tạo/cập nhật từ entity, `ddl-auto=update`)
- [x] Thiết kế REST API chuẩn hoá theo `ApiResponse<T>` wrapper
- [x] Spring Security + JWT HS512, chi nhánh đăng nhập lưu trong token
- [x] Thiết kế UI/UX POS keyboard-first (F1–F9, Esc)
- [x] Thiết kế tích hợp phần cứng (máy in K80, máy quét HID, điện thoại làm máy quét)

## Giai đoạn 2 — Module POS ✅
- [x] `POST /api/invoices`: tạo hóa đơn, trừ kho nguyên tử chống race condition, chống gửi trùng bằng `clientRequestId`
- [x] Quét mã vạch HID < 50ms (`useBarcodeScanner.js`), thêm/sửa/xóa sản phẩm trong giỏ
- [x] Tra cứu sản phẩm F2, chọn khách hàng F3, nhiều đơn song song F8
- [x] In hóa đơn K80 qua Electron silent print + Web print (`receiptPrinter.js`)
- [x] Thanh toán: Tiền mặt, Thẻ, VietQR Napas247 động (`VietQRModal.jsx`)
- [x] Ghép điện thoại làm máy quét không dây bằng mã QR (`ScannerPairingModal.jsx`)
- [x] Chế độ demo ngoại tuyến khi không có backend

## Giai đoạn 3 — Module Quản lý Kho ✅
- [x] Quản lý sản phẩm (mã vạch, danh mục, giá vốn, giá bán, đơn vị tính, ảnh), in tem mã vạch
- [x] Nhập kho `POST /api/stock-in`: phiếu nhập nhiều mặt hàng, cộng tồn kho tự động
- [x] Lịch sử nhập kho và chi tiết từng phiếu (`StockInDetailModal.jsx`)
- [x] Điều chuyển hàng giữa chi nhánh (`StockTransferModal.jsx` + `POST /api/inventory/transfer`)
- [x] Cảnh báo hàng tồn thấp theo `min_threshold`, lọc theo danh mục và trạng thái tồn
- [x] Xuất tồn kho ra CSV UTF-8 BOM (mở đúng tiếng Việt trên Excel)

## Giai đoạn 4 — Đồng bộ đa chi nhánh ✅
- [x] WebSocket STOMP: endpoint `/ws`, kênh `/topic/inventory-updates` đẩy tồn kho sau mỗi giao dịch
- [x] Frontend STOMP client (`websocketService.js`) tự kết nối lại khi mất kết nối
- [x] Điều chuyển kho giữa 2 chi nhánh trong 1 `@Transactional`
- [x] Badge trạng thái kết nối máy chủ / WebSocket / ngoại tuyến

## Giai đoạn 5 — Báo cáo & Dự báo AI ✅
- [x] Báo cáo doanh thu theo ngày/chi nhánh, biểu đồ SVG doanh thu & lợi nhuận (`ReportPage.jsx`)
- [x] Top sản phẩm bán chạy kèm tỷ trọng doanh thu
- [x] Lịch sử hóa đơn, xem chi tiết, trả hàng & hoàn tiền (`REFUNDED`)
- [x] Xuất báo cáo doanh thu & danh sách hóa đơn ra CSV
- [x] Dự báo theo `AI_for_POS.md`: Exponential Smoothing α = 0.35, Moving Average 7 ngày, tồn kho an toàn, ROP, số lượng đề xuất, MAE/RMSE (`ForecastCalculator.java`)
- [x] Job `@Scheduled` 02:00 hằng đêm tính dự báo mọi chi nhánh, lưu bảng `forecast_result`
- [x] Trang Dự báo (`ForecastPage.jsx`): biểu đồ thực tế 30 ngày và dự báo, MAE/RMSE, bảng đề xuất kèm lý do, nút mở phiếu nhập điền sẵn
- [x] Bản Python `AI_FORECAST/` cho cùng kết quả với Java, kiểm tra bằng `ForecastParityTest`

## Giai đoạn 6 — Kiểm thử & Mở rộng nghiệp vụ ✅
- [x] Unit test JUnit 5 + Mockito và test tích hợp (`mvn test`), pytest cho `AI_FORECAST`, chạy tự động trên CI
- [x] Dockerfile đa giai đoạn (`maven:3.9-eclipse-temurin-21-alpine` → JRE 21) + `docker-compose.yml`
- [x] Trang Tài khoản (`StaffPage.jsx`): tạo tài khoản, phân quyền 4 vai trò, phân công chi nhánh, khóa/mở, đổi mật khẩu
- [x] Ca làm (`ShiftsPage.jsx`): lịch ca theo chi nhánh, xếp ca, mở/đóng ca, thu chi két, đối soát và duyệt ca lệch tiền
- [x] Đối tác (`PartnerPage.jsx`): khách hàng thân thiết + tích điểm, nhà cung cấp
- [x] App điện thoại (`MOBILE/`): quét mã gửi vào giỏ của quầy đã ghép, tra giá/tồn kho, hàng đợi ngoại tuyến, gán mã vạch cho sản phẩm

## Giai đoạn 7 — Triển khai thử nghiệm thực tế
- [ ] Cài đặt & triển khai tại 1 cửa hàng bán lẻ thực tế
- [ ] Test máy in nhiệt K80 và máy quét mã vạch HID thực tế
- [ ] Đào tạo nhân viên cửa hàng sử dụng hệ thống
- [ ] Thu thập phản hồi, sửa lỗi & tối ưu

## Giai đoạn 8 — Hoàn thiện & Báo cáo đề tài
- [ ] Vẽ ERD hoàn chỉnh → lưu vào `docs/erd.png`
- [ ] Xuất số liệu MAE/RMSE của mô hình dự báo trên dữ liệu thật (dùng `AI_FORECAST`)
- [ ] Hoàn thiện tài liệu hướng dẫn sử dụng (user manual)
- [ ] Viết báo cáo khóa luận (SRS, thiết kế, cài đặt, kiểm thử, kết quả)
- [ ] Chuẩn bị slide & demo bảo vệ đề tài

---

## Kiến trúc hệ thống

```
[Electron Desktop App (React 19 + Vite 8)]      [App điện thoại Expo]
         │  axios /api      │  STOMP /ws               │  REST /api/scanner
         ▼                  ▼                          ▼
              [Spring Boot 3.3.3 :8080]
               REST API + WebSocket STOMP
               Spring Security JWT
               ForecastCalculator + job 02:00
                          │
                   [Spring Data JPA]
                          │
                  [MySQL 8.0 :3306]
                (pos_inventory database)

[AI_FORECAST (Python)] ── đọc CSV hoặc MySQL, kiểm chứng công thức, xuất số liệu báo cáo
```

### Cấu trúc thư mục
```
├── BACKEND/        # Spring Boot (Maven)
├── FRONTEND/       # Electron + React + Vite
├── MOBILE/         # Expo / React Native
├── AI_FORECAST/    # Python: kiểm chứng mô hình dự báo
├── AI_for_POS.md   # Tài liệu module dự báo AI
└── docker-compose.yml, .env.example
```

### Cấu trúc thư mục Backend
```
BACKEND/src/main/java/com/pos/
├── PosBackendApplication.java
├── auth/         # AuthController, LoginService (chọn chi nhánh khi đăng nhập), UserProfileResponse
├── branch/       # Branch, BranchController, BranchService, BranchAccessService
├── common/       # ApiResponse<T>, BusinessException, GlobalExceptionHandler
├── config/       # CorsConfig, DatabaseInitializer, OpenApiConfig, WebSocketConfig
├── customer/     # Customer, CustomerController, CustomerService
├── forecast/     # ForecastCalculator (công thức), ForecastService (job đêm, cache), ForecastController
├── inventory/    # Inventory, InventoryController, InventoryService (trừ/cộng kho nguyên tử, điều chuyển)
├── product/      # Product, ProductController, ProductService
├── purchase/     # StockIn, StockInService, Supplier, SupplierService
├── report/       # ReportController, ReportService
├── sale/         # Invoice, InvoiceDetail, InvoiceService (bán + hoàn trả)
├── scanner/      # Ghép cặp điện thoại với quầy (PosTerminal, ScannerPairing), tra cứu, gửi mã quét
├── security/     # JwtTokenProvider, JwtAuthFilter, SecurityConfig, CurrentUserService, BranchSession
├── shift/        # WorkShift (lịch ca), ShiftAssignment (xếp ca), ShiftSession (mở/đóng ca), ShiftCashMovement
├── staff/        # StaffMember: nhân viên được xếp ca
└── user/         # User, Role, UserController, UserService (tài khoản đăng nhập)
```

### Cấu trúc thư mục Frontend
```
FRONTEND/
├── electron/
│   ├── main.cjs          # Electron Main (BrowserWindow, IPC, silent print K80)
│   └── preload.cjs       # ContextBridge (contextIsolation: true)
└── src/
    ├── components/
    │   ├── common/       # Button, Input, Table, Badge, Modal, Tabs, BarcodeView
    │   ├── forecast/     # SalesTrendChart (SVG)
    │   └── layout/       # AppShell (thanh điều hướng bên trái)
    ├── context/          # AuthContext (JWT, chi nhánh, trạng thái WS)
    ├── hooks/            # useBarcodeScanner, useHotkeys, useAppNavigate, useBranches
    ├── pages/
    │   ├── auth/         # LoginPage
    │   ├── pos/          # PosPage, giỏ hàng, thanh toán, VietQR, khách hàng, ca, ghép điện thoại
    │   ├── inventory/    # InventoryPage, nhập kho, điều chuyển, in tem mã vạch
    │   ├── forecast/     # ForecastPage (dự báo + đề xuất nhập hàng)
    │   ├── shifts/       # ShiftsPage (lịch ca, xếp ca, lịch sử ca, đối soát)
    │   ├── report/       # ReportPage (doanh thu, hóa đơn, hoàn trả)
    │   ├── partner/      # PartnerPage (khách hàng + nhà cung cấp)
    │   └── admin/        # StaffPage (tab Tài khoản)
    ├── services/         # api, auth, branch, customer, forecast, inventory, product, purchase,
    │                     # report, sale, scannerPairing, shift, staff, user, websocket
    └── utils/            # audio, exportCsv, forecasting (công thức khi ngoại tuyến), formatters, receiptPrinter
```

---

## API Endpoints

Mọi API (trừ đăng nhập và `/api/scanner/ping`) cần header `Authorization: Bearer <JWT>`. Chi tiết tham số xem Swagger UI.

### Authentication
| Method | Endpoint | Mô tả |
|--------|----------|-------|
| POST | `/api/auth/login` | Đăng nhập (kèm chi nhánh), trả về JWT |
| GET | `/api/auth/me` | Thông tin tài khoản hiện tại |

### Branches
| Method | Endpoint | Mô tả |
|--------|----------|-------|
| GET | `/api/branches`, `/api/branches/active` | Danh sách chi nhánh |
| GET | `/api/branches/{id}` | Chi tiết chi nhánh |
| POST / PUT | `/api/branches`, `/api/branches/{id}` | ADMIN tạo/sửa chi nhánh |

### Products
| Method | Endpoint | Mô tả |
|--------|----------|-------|
| GET | `/api/products` | Danh sách sản phẩm |
| GET | `/api/products/{id}` | Chi tiết sản phẩm |
| GET | `/api/products/barcode/{barcode}` | Tra cứu theo mã vạch |
| POST | `/api/products` | Tạo sản phẩm |
| PUT | `/api/products/{id}` | Cập nhật sản phẩm |
| DELETE | `/api/products/{id}` | Xóa mềm sản phẩm |
| POST | `/api/products/barcodes/sync` | (ADMIN) Đổi mã cũ không quét được sang mã nội bộ `20` + ID |

### Inventory
| Method | Endpoint | Mô tả |
|--------|----------|-------|
| GET | `/api/inventory?branchId=` | Tồn kho theo chi nhánh (mặc định chi nhánh đăng nhập) |
| GET | `/api/inventory/branch/{branchId}` | Tồn kho theo chi nhánh |
| GET | `/api/inventory/low-stock` | Hàng dưới ngưỡng an toàn |
| GET | `/api/inventory/product` | Tồn kho của 1 sản phẩm tại chi nhánh |
| POST | `/api/inventory/transfer` | Điều chuyển hàng sang chi nhánh khác |

### Sales & Invoices
| Method | Endpoint | Mô tả |
|--------|----------|-------|
| GET | `/api/invoices?branchId=&page=&size=` | Danh sách hóa đơn (phân trang) |
| GET | `/api/invoices/{id}` | Chi tiết hóa đơn |
| POST | `/api/invoices` | Tạo hóa đơn (trừ kho nguyên tử, cần ca đang mở) |
| POST | `/api/invoices/{id}/refund` | Hoàn trả hóa đơn (cộng kho, thu hồi điểm) |

### Stock-In & Suppliers
| Method | Endpoint | Mô tả |
|--------|----------|-------|
| GET | `/api/stock-in?branchId=`, `/api/stock-in/{id}` | Phiếu nhập kho |
| POST | `/api/stock-in` | Tạo phiếu nhập (cộng tồn kho) |
| GET | `/api/suppliers`, `/api/suppliers/{id}` | Nhà cung cấp |
| POST | `/api/suppliers` | Tạo nhà cung cấp |

### Customers
| Method | Endpoint | Mô tả |
|--------|----------|-------|
| GET | `/api/customers`, `/api/customers/{id}` | Khách hàng |
| GET | `/api/customers/phone/{phone}` | Tìm khách theo SĐT (F3 trên POS) |
| POST | `/api/customers` | Tạo khách hàng |

### Reports
| Method | Endpoint | Mô tả |
|--------|----------|-------|
| GET | `/api/reports/revenue?branchId=&days=` | Doanh thu theo ngày |
| GET | `/api/reports/top-products?branchId=&days=&limit=` | Top sản phẩm bán chạy |

### Forecast AI
| Method | Endpoint | Mô tả |
|--------|----------|-------|
| GET | `/api/forecast/demand?branchId=&days=&algorithm=` | Dự báo mọi sản phẩm (`EXP_SMOOTH` hoặc `MOVING_AVG`) |
| GET | `/api/forecast/demand/{productId}?branchId=&days=` | Dự báo 1 sản phẩm kèm lịch sử 30 ngày, SS, ROP, MAE/RMSE, lý do |
| GET | `/api/forecast/recommendations?branchId=` | Sản phẩm cần nhập, sắp theo mức Khẩn cấp / Cao / Bình thường |

### Shifts (Ca làm việc)
| Method | Endpoint | Mô tả |
|--------|----------|-------|
| GET | `/api/shifts/work-shifts` | Lịch ca của chi nhánh (hoặc lịch mẫu chung) |
| POST / PUT / DELETE | `/api/shifts/work-shifts[/{id}]` | ADMIN thêm, sửa, xóa ca |
| POST | `/api/shifts/work-shifts/customize` | ADMIN tạo lịch ca riêng cho chi nhánh từ lịch mẫu |
| GET / PUT | `/api/shifts/assignments` | Xem / xếp nhân viên vào ca theo ngày |
| GET | `/api/shifts/current` | Ca đang mở ở chi nhánh, hoặc gợi ý ca, người được xếp, tiền ca trước để lại |
| POST | `/api/shifts/open` | Mở ca với tiền mặt đầu ca |
| POST | `/api/shifts/current/close` | Đóng ca: đối soát tiền mặt, VietQR, thẻ |
| GET / POST | `/api/shifts/current/cash-movements` | Thu/chi két của ca đang mở |
| PUT / DELETE | `/api/shifts/cash-movements/{id}` | Sửa / xóa khoản thu chi khi ca còn mở |
| GET | `/api/shifts` | Lịch sử ca kèm tổng thiếu/dư |
| GET | `/api/shifts/{id}`, `/{id}/invoices`, `/{id}/cash-movements` | Chi tiết ca, hóa đơn và thu chi của ca |
| POST | `/api/shifts/{id}/review` | Quản lý duyệt ca lệch tiền |
| POST | `/api/shifts/{id}/close` | Quản lý đóng hộ ca quên đóng |

### Staff & Users
| Method | Endpoint | Mô tả |
|--------|----------|-------|
| GET / POST / PUT | `/api/staff[/{id}]` | Nhân viên của chi nhánh (dùng để xếp ca) |
| GET / POST | `/api/users` | ADMIN xem / tạo tài khoản |
| GET / PUT | `/api/users/{id}` | Xem / sửa tài khoản (vai trò, chi nhánh) |
| PATCH | `/api/users/{id}/toggle-status` | Khóa / mở khóa tài khoản |
| POST | `/api/users/{id}/change-password` | Đổi mật khẩu |

### Scanner (app điện thoại)
Danh sách đầy đủ ở [`MOBILE/README.md`](MOBILE/README.md): ghép cặp quầy (`/api/scanner/terminals/...`, `/api/scanner/pair`), gửi mã quét (`/api/scanner/broadcast`, `/api/scanner/sync-batch`), tra cứu (`/api/scanner/lookup`, `/api/scanner/products/...`) và gán mã vạch (`PUT /api/scanner/products/{id}/barcode`).

### WebSocket
| Endpoint | Mô tả |
|----------|-------|
| `/ws` (SockJS) | Kết nối STOMP, bắt buộc JWT khi CONNECT |
| `/topic/inventory-updates` | Thay đổi tồn kho realtime |
| `/topic/terminals/{id}/scans` | Mã quét từ điện thoại đã ghép với quầy |

---

## Rủi ro cần lưu ý
- Tích hợp phần cứng (máy in/máy quét) qua Electron có thể phát sinh vấn đề driver trên Windows, nên test sớm trên máy thật.
- Đóng gói & phân phối Electron (`electron-builder`) cho nhiều máy cần quy trình cài đặt hoặc auto-update rõ ràng.
- Độ chính xác dự báo phụ thuộc lượng dữ liệu lịch sử; với cửa hàng mới, kết quả cần diễn giải thận trọng (trang Dự báo hiện độ tin cậy cho từng sản phẩm).
- Mất mạng giữa chi nhánh và máy chủ: POS chưa có hàng đợi đồng bộ hóa đơn ngoại tuyến (hướng mở rộng).
