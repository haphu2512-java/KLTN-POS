# Hệ thống POS và Quản lý Kho tích hợp AI Dự báo Tồn kho cho Cửa hàng Bán lẻ nhỏ

## Thông tin đề tài
- **Mô tả:** Giải pháp số hóa toàn diện cho cửa hàng bán lẻ nhỏ — bán hàng tại quầy (POS), quản lý tồn kho theo thời gian thực, đồng bộ đa chi nhánh, cảnh báo & gợi ý nhập hàng thông minh dựa trên AI/thuật toán dự báo.
- **Công nghệ sử dụng:**
  - **Backend:** Java 21 + Spring Boot 3.3.3 (REST API + WebSocket STOMP), Spring Data JPA, Spring Security (JWT HS512), Spring Scheduler
  - **Frontend (Desktop):** Electron 44 + React 19 + Vite 8 (renderer), giao tiếp backend qua Vite Proxy `/api` + STOMP WebSocket
  - **Database:** MySQL 8.0 — 1.949+ sản phẩm thực (scrape từ Bach Hoa Xanh), 2 chi nhánh, 10+ khách hàng, toàn bộ dữ liệu tồn kho
  - **Realtime/đồng bộ:** Spring WebSocket STOMP broker, topic `/topic/inventory-updates` push kho realtime sau mỗi giao dịch POS
  - **Phần cứng:** In hóa đơn nhiệt K80 qua Electron silent print (ESC/POS), máy quét mã vạch HID < 50ms buffer
  - **Dự báo AI:** Exponential Smoothing (α=0.35) + Moving Average 7 ngày chạy phía Spring Boot, `@Scheduled` job chạy định kỳ hàng đêm
- **Chuẩn đầu ra:** Phần mềm POS + quản lý kho hoàn chỉnh, thử nghiệm thực tế tại ít nhất 1 cửa hàng.

---

## Trạng thái dự án (Cập nhật: 15/10/2026)

> **Hoàn thành 6/8 giai đoạn** — Hệ thống đã đủ chức năng cho thử nghiệm thực tế.

```
✅ XONG   Giai đoạn 0 — Chuẩn bị & Lập kế hoạch
✅ XONG   Giai đoạn 1 — Thiết kế hệ thống
✅ XONG   Giai đoạn 2 — Module POS (Bán hàng)
✅ XONG   Giai đoạn 3 — Module Quản lý Kho
✅ XONG   Giai đoạn 4 — Đồng bộ đa chi nhánh (WebSocket STOMP)
✅ XONG   Giai đoạn 5 — Báo cáo & Dự báo AI
✅ XONG   Giai đoạn 6 — Kiểm thử & Mở rộng nghiệp vụ
⏳ CẦN   Giai đoạn 7 — Triển khai thử nghiệm thực tế
⏳ CẦN   Giai đoạn 8 — Hoàn thiện báo cáo đề tài
```

---

## Hướng dẫn chạy hệ thống nhanh

### Yêu cầu
- Docker Desktop (Windows)
- Node.js 18+

### Cấu hình môi trường (`.env`)
Mọi cấu hình và bí mật đều lấy từ file `.env`, không có giá trị bí mật nào viết sẵn trong code. Mỗi phần có một file mẫu:

| File | Dùng cho | Cách tạo |
|------|----------|----------|
| `.env` (thư mục gốc) | Backend Spring Boot và Docker Compose | `cp .env.example .env` |
| `FRONTEND/.env` | Vite dev server, React, Electron | `cp FRONTEND/.env.example FRONTEND/.env` |
| `MOBILE/.env` | App quét mã Expo | `cp MOBILE/.env.example MOBILE/.env` |

Biến bắt buộc trong `.env` gốc: `DB_PASSWORD`, `JWT_SECRET` (tối thiểu 32 ký tự, ví dụ `openssl rand -hex 32`), `SEED_DEFAULT_PASSWORD` (lần chạy đầu), và `DB_ROOT_PASSWORD` khi chạy bằng Docker. Thiếu biến bắt buộc thì backend hoặc Docker Compose dừng ngay và báo tên biến còn thiếu.

### Khởi động Backend + MySQL
```bash
# Tại thư mục gốc dự án (đã tạo .env)
docker compose up -d

# Hoặc chạy backend trực tiếp (vẫn đọc ../.env)
cd BACKEND && mvn spring-boot:run

# Swagger UI: http://localhost:8080/swagger-ui/index.html
# API Login: POST http://localhost:8080/api/auth/login
```

### Tài khoản mặc định
Tạo tự động khi database còn trống, mật khẩu là giá trị `SEED_DEFAULT_PASSWORD` trong `.env`.

| Tài khoản | Vai trò |
|-----------|---------|
| `admin` | ADMIN (toàn quyền) |
| `cashier` | CASHIER (chỉ bán hàng) |

### Khởi động Frontend
```bash
cd FRONTEND
npm install
npm run dev          # Giao diện web: http://localhost:5173
npm run dev:electron # Chạy Electron desktop app (cần backend đã chạy)
```

> **Lưu ý quan trọng:** Khi chạy `npm run dev`, frontend dùng Vite Proxy (`/api`, `/ws` → `DEV_PROXY_TARGET`, mặc định `http://localhost:8080`) nên không gặp lỗi CORS. Khi build Electron phải đặt `VITE_API_BASE_URL` và `VITE_WS_URL` trong `FRONTEND/.env`.

---

## Giai đoạn 0 — Chuẩn bị & Lập kế hoạch
- [x] Khảo sát thực tế quy trình bán hàng, nhập/xuất kho tại cửa hàng bán lẻ nhỏ
- [x] Xác định phạm vi MVP
- [x] Khởi tạo dự án Spring Boot (Maven, Modular Monolith theo tính năng)
- [x] Khởi tạo dự án Electron + React 19 + Vite 8
- [x] Thiết kế kiến trúc hệ thống tổng thể
- [x] Lập sơ đồ ERD Database

## Giai đoạn 1 — Thiết kế hệ thống
- [x] Thiết kế database MySQL 8.x (branch, users, product, inventory, invoice, stock_in, customer, supplier)
- [x] Thiết kế REST API chuẩn hoá theo `ApiResponse<T>` wrapper
- [x] Thiết kế Spring Security + JWT HS512 (24h expiration)
- [x] Thiết kế UI/UX POS keyboard-first (F1–F9, Esc hotkeys)
- [x] Thiết kế tích hợp phần cứng (ESC/POS printer, HID barcode scanner)

## Giai đoạn 2 — Module POS (Electron) ✅
- [x] API bán hàng Spring Boot: `POST /api/invoices` — tạo hóa đơn, trừ kho nguyên tử chống race condition
- [x] Giao diện bán hàng: quét mã vạch HID < 50ms (`useBarcodeScanner.js`), thêm/sửa/xóa sản phẩm giỏ hàng
- [x] Modal tra cứu sản phẩm F2 — tra cứu trong 1.949 sản phẩm từ database, giới hạn 100 kết quả/lần
- [x] Tính tổng tiền, giảm giá, quản lý nhiều đơn song song (Tabs F8)
- [x] In hóa đơn K80 nhiệt qua Electron silent print + Web print (`receiptPrinter.js`)
- [x] Thanh toán: Tiền mặt, Thẻ/Chuyển khoản, VietQR Napas247 động (`VietQRModal.jsx`)
- [x] Đăng nhập JWT, phân quyền ADMIN/CASHIER/MANAGER/WAREHOUSE, đăng xuất
- [x] Offline-first demo mode khi backend không có sẵn

## Giai đoạn 3 — Module Quản lý Kho ✅
- [x] API + giao diện quản lý sản phẩm (mã vạch, danh mục, giá vốn, giá bán, đơn vị tính, ảnh)
- [x] API + giao diện nhập kho: `POST /api/stock-in` — phiếu nhập nhiều mặt hàng, cộng tồn kho tự động
- [x] Cập nhật tồn kho realtime khi bán hàng (`deductStockAtomic` — atomic UPDATE chống âm kho)
- [x] Xem lịch sử nhập kho và chi tiết từng phiếu (`StockInDetailModal.jsx`)
- [x] Điều chuyển hàng giữa chi nhánh (`StockTransferModal.jsx` + `POST /api/inventory/transfer`)
- [x] Cảnh báo hàng tồn thấp: filter `quantity <= min_threshold`, badge đỏ/vàng/xanh
- [x] Bộ lọc sản phẩm theo danh mục, tìm kiếm nhanh theo tên/mã vạch
- [x] Xuất tồn kho ra CSV chuẩn UTF-8 BOM (hiển thị tiếng Việt trên Excel)

## Giai đoạn 4 — Đồng bộ đa chi nhánh ✅
- [x] Spring WebSocket STOMP — endpoint `/ws`, broker topic `/topic/inventory-updates` và `/topic/sales-updates`
- [x] Atomic deductStock (`InventoryRepository.deductStock` với `quantity >= :qty` guard)
- [x] Push notification realtime sau mỗi giao dịch POS (tồn kho cập nhật ngay màn hình các quầy khác)
- [x] Frontend STOMP client (`websocketService.js`) tự động reconnect khi ngắt kết nối
- [x] Điều chuyển kho giữa 2 chi nhánh trong 1 `@Transactional` nguyên tử
- [x] Badge trạng thái kết nối `🟢 Máy chủ & WS` / `🟢 API Online` / `⚪ Ngoại tuyến`

## Giai đoạn 5 — Báo cáo & Dự báo AI ✅
- [x] API báo cáo doanh thu theo ngày/tuần/tháng/chi nhánh (`ReportController` + `ReportService`)
- [x] Giao diện báo cáo: biểu đồ SVG doanh thu & lợi nhuận (`ReportPage.jsx`)
- [x] Top sản phẩm bán chạy nhất kèm tỷ trọng doanh thu
- [x] Lịch sử hóa đơn, xem chi tiết, thực hiện trả hàng & hoàn tiền (`REFUNDED` status)
- [x] Xuất báo cáo doanh thu & danh sách hóa đơn ra CSV/Excel (`exportCsv.js`)
- [x] Thuật toán dự báo kép: **Exponential Smoothing (α=0.35)** + **Moving Average 7 ngày** (`ForecastService.java`)
- [x] `@Scheduled` nightly job tự động tính toán dự báo nhu cầu toàn bộ chi nhánh
- [x] API `GET /api/forecast/demand` + `GET /api/forecast/recommendations` (gợi ý nhập hàng ưu tiên)
- [x] Giao diện dự báo AI (`ForecastPage.jsx`): biểu đồ SVG thực tế vs dự báo, MAE, bảng đề xuất nhập hàng
- [x] Đánh giá độ chính xác MAE (Mean Absolute Error)

## Giai đoạn 6 — Kiểm thử & Mở rộng nghiệp vụ ✅
- [x] Unit test JUnit 5 + Mockito: **16/16 tests PASS** (`UserServiceTest`, `ForecastServiceTest`, `InventoryServiceTest`, `InvoiceServiceTest`, `ReportServiceTest`)
- [x] Kiểm thử giao diện tự động qua Browser Subagent + Vite Build (0 errors, 131 modules)
- [x] Sửa 9 vấn đề kỹ thuật & bảo mật (xem `docs/fix.md`): bảo mật endpoint, CORS, atomic points, pagination, forecast fallback, WS logging, H2 test scope, Dockerfile
- [x] **Sửa lỗi CORS preflight (2026-10-13):** Thêm `HttpMethod.OPTIONS` permit all, `CorsConfigurationSource` bean, Vite dev proxy `/api` → `http://localhost:8080`
- [x] **Kết nối database thực tế:** 1.949 sản phẩm từ BachHoaXanh, tồn kho đầy đủ hai chi nhánh
- [x] Dockerfile đa giai đoạn (`maven:3.9-eclipse-temurin-21-alpine` → JRE 21) + `docker-compose.yml`
- [x] Module Quản lý Nhân viên (StaffPage.jsx): CRUD tài khoản, phân quyền 4 vai trò, khóa/mở tài khoản
- [x] Module Đối tác (PartnerPage.jsx): Khách hàng thân thiết + tích điểm, Nhà cung cấp
- [x] Trả hàng & Hoàn tiền: `InvoiceStatus.REFUNDED`, hoàn kho nguyên tử, thu hồi điểm thưởng
- [x] Xuất dữ liệu CSV/Excel (UTF-8 BOM) cho báo cáo, hóa đơn và tồn kho

## Giai đoạn 7 — Triển khai thử nghiệm thực tế
- [ ] Cài đặt & triển khai tại 1 cửa hàng bán lẻ thực tế
- [ ] Test máy in nhiệt K80 thực tế (silent print qua Electron)
- [ ] Test máy quét mã vạch HID thực tế (< 50ms response)
- [ ] Đào tạo nhân viên cửa hàng sử dụng hệ thống
- [ ] Thu thập phản hồi, ghi nhận lỗi phát sinh
- [ ] Sửa lỗi & tối ưu dựa trên phản hồi thực tế

## Giai đoạn 8 — Hoàn thiện & Báo cáo đề tài
- [ ] Vẽ ERD hoàn chỉnh → lưu vào `docs/erd.png`
- [ ] Hoàn thiện tài liệu kỹ thuật (kiến trúc, API, database schema)
- [ ] Hoàn thiện tài liệu hướng dẫn sử dụng (user manual)
- [ ] Viết báo cáo khóa luận (SRS, thiết kế, cài đặt, kiểm thử, kết quả)
- [ ] Chuẩn bị slide & demo bảo vệ đề tài
- [ ] Đánh giá khả năng thương mại hóa (mô hình bán license / SaaS theo chi nhánh)

---

## Kiến trúc hệ thống

```
[Electron Desktop App (React 19 + Vite 8)]
         │   axios/proxy  │  @stomp/stompjs
         ▼               ▼
[Vite Dev Proxy]──────[Spring Boot 3.3.3 :8080]
  /api → :8080               │  REST API
  /ws  → :8080          [Spring Security JWT]
                              │
                        [Spring Data JPA]
                              │
                       [MySQL 8.0 :3306]
                    (pos_inventory database)
                    1.949 sản phẩm thực tế
```

### Cấu trúc thư mục Backend
```
BACKEND/src/main/java/com/pos/
├── PosBackendApplication.java
├── auth/         # AuthController, LoginRequest, AuthResponse, UserProfileResponse
├── branch/       # Branch entity, BranchRepository, BranchService
├── common/       # ApiResponse<T>, BusinessException, GlobalExceptionHandler
├── config/       # CorsConfig, DatabaseInitializer, OpenApiConfig, WebSocketConfig
├── customer/     # Customer, CustomerController, CustomerService, CustomerRepository
├── forecast/     # ForecastController, ForecastService (EXP_SMOOTH + MA7), ForecastResponse
├── inventory/    # Inventory, InventoryController, InventoryService (atomic deduct/add), InventoryRepository
├── product/      # Product, ProductController, ProductService, ProductRequest
├── purchase/     # StockIn, StockInService, Supplier, SupplierController, SupplierService
├── report/       # ReportController, ReportService, RevenueSummaryResponse, TopProductResponse
├── sale/         # Invoice, InvoiceDetail, InvoiceController, InvoiceService (atomic + refund), InvoiceRepository
├── security/     # JwtTokenProvider, JwtAuthFilter, SecurityConfig, CustomUserDetailsService
└── user/         # User, Role, UserController, UserService, UserResponse
```

### Cấu trúc thư mục Frontend
```
FRONTEND/
├── electron/
│   ├── main.cjs          # Electron Main (BrowserWindow, IPC, silent print K80)
│   └── preload.cjs       # ContextBridge (contextIsolation: true)
└── src/
    ├── components/
    │   ├── common/       # Button, Input, Table, Badge, Modal, Tabs (+ CSS)
    │   └── forecast/     # SalesTrendChart (SVG interactive)
    ├── context/
    │   └── AuthContext.jsx   # AuthProvider, useAuth, JWT + WS state
    ├── hooks/
    │   ├── useBarcodeScanner.js   # HID scanner < 50ms buffer
    │   └── useHotkeys.js          # F1-F9, Esc global hotkeys
    ├── pages/
    │   ├── auth/         # LoginPage.jsx
    │   ├── pos/          # PosPage, PosHeader, PosCart, PosPayment, ProductSearchModal, CustomerModal, VietQRModal
    │   ├── inventory/    # InventoryPage, StockInModal, StockInDetailModal, StockTransferModal
    │   ├── forecast/     # ForecastPage (AI demand forecast + recommendations)
    │   ├── report/       # ReportPage (revenue chart, invoice history, refund)
    │   ├── partner/      # PartnerPage (customers + suppliers)
    │   └── admin/        # StaffPage (staff management + roles)
    ├── services/
    │   ├── api.js              # axios + JWT interceptor (baseURL: /api proxy)
    │   ├── authService.js      # login, getCurrentUser, getToken, logout
    │   ├── productService.js   # getProducts, getProductByBarcode, searchProducts
    │   ├── inventoryService.js # getBranchInventory, getLowStock
    │   ├── customerService.js  # getCustomers, getCustomerByPhone, createCustomer
    │   ├── saleService.js      # createInvoice, getInvoices, refundInvoice
    │   ├── purchaseService.js  # getStockIns, createStockIn, getSuppliers
    │   ├── forecastService.js  # getDemandForecast
    │   ├── reportService.js    # getRevenueReport, getTopProducts
    │   ├── userService.js      # getUsers, createUser, updateUser, toggleStatus
    │   └── websocketService.js # @stomp/stompjs STOMP client, auto-reconnect
    └── utils/
        ├── audio.js          # Web Audio API: beep 800Hz (success), buzz 220Hz (error)
        ├── exportCsv.js      # CSV export UTF-8 BOM (Excel compatible)
        ├── forecasting.js    # Client-side EXP_SMOOTH + MA (fallback khi offline)
        ├── formatters.js     # formatCurrency, formatDate, formatDateTime
        └── receiptPrinter.js # K80 receipt print (Electron IPC + window.print fallback)
```

---

## API Endpoints

### Authentication
| Method | Endpoint | Mô tả |
|--------|----------|-------|
| POST | `/api/auth/login` | Đăng nhập, trả về JWT token |
| GET | `/api/auth/me` | Lấy thông tin user hiện tại (cần JWT) |

### Products
| Method | Endpoint | Mô tả |
|--------|----------|-------|
| GET | `/api/products` | Danh sách sản phẩm (tìm kiếm theo `search`, `category`) |
| GET | `/api/products/{id}` | Chi tiết sản phẩm |
| GET | `/api/products/barcode/{barcode}` | Tra cứu theo mã vạch (POS scanner) |
| POST | `/api/products` | Tạo sản phẩm mới |
| PUT | `/api/products/{id}` | Cập nhật sản phẩm |
| DELETE | `/api/products/{id}` | Xóa mềm sản phẩm |

### Inventory
| Method | Endpoint | Mô tả |
|--------|----------|-------|
| GET | `/api/inventory?branchId=` | Tồn kho theo chi nhánh |
| GET | `/api/inventory/low-stock?branchId=` | Hàng tồn dưới ngưỡng an toàn |
| POST | `/api/inventory/transfer` | Điều chuyển hàng giữa chi nhánh |

### Sales & Invoices
| Method | Endpoint | Mô tả |
|--------|----------|-------|
| GET | `/api/invoices?branchId=&page=&size=` | Danh sách hóa đơn (có phân trang) |
| GET | `/api/invoices/{id}` | Chi tiết hóa đơn |
| POST | `/api/invoices` | Tạo hóa đơn bán hàng (trừ kho nguyên tử) |
| POST | `/api/invoices/{id}/refund` | Hoàn trả hóa đơn (cộng kho, thu hồi điểm) |

### Stock-In & Suppliers
| Method | Endpoint | Mô tả |
|--------|----------|-------|
| GET | `/api/stock-in?branchId=` | Danh sách phiếu nhập kho |
| POST | `/api/stock-in` | Tạo phiếu nhập kho (cộng tồn kho tự động) |
| GET | `/api/suppliers` | Danh sách nhà cung cấp |
| POST | `/api/suppliers` | Tạo nhà cung cấp mới |

### Customers
| Method | Endpoint | Mô tả |
|--------|----------|-------|
| GET | `/api/customers` | Danh sách khách hàng |
| GET | `/api/customers/phone/{phone}` | Tìm khách hàng theo SĐT (F3 POS) |
| POST | `/api/customers` | Tạo khách hàng mới |

### Reports
| Method | Endpoint | Mô tả |
|--------|----------|-------|
| GET | `/api/reports/revenue?branchId=&days=` | Báo cáo doanh thu theo ngày |
| GET | `/api/reports/top-products?branchId=&days=&limit=` | Top sản phẩm bán chạy |

### Forecast AI
| Method | Endpoint | Mô tả |
|--------|----------|-------|
| GET | `/api/forecast/demand?branchId=&days=&algorithm=` | Dự báo nhu cầu (EXP_SMOOTH hoặc MOVING_AVG) |
| GET | `/api/forecast/recommendations?branchId=` | Danh sách gợi ý nhập hàng ưu tiên |

### Shifts (Ca làm việc)
| Method | Endpoint | Mô tả |
|--------|----------|-------|
| GET | `/api/shifts/work-shifts` | 5 ca cố định trong ngày (mặc định 06–09, 09–12, 12–15, 15–18, 18–22) |
| PUT | `/api/shifts/work-shifts/{id}` | ADMIN sửa tên/giờ ca |
| GET | `/api/shifts/current` | Ca đang mở của tài khoản, hoặc gợi ý tiền và ghi chú bàn giao từ ca trước |
| POST | `/api/shifts/open` | Mở ca với tiền mặt đầu ca |
| POST | `/api/shifts/current/close` | Đóng ca: nhập tiền mặt đếm được, hệ thống tính chênh lệch so với tiền đầu ca + doanh số tiền mặt |
| GET | `/api/shifts?branchId=&from=&to=` | ADMIN/MANAGER xem lịch sử ca (MANAGER chỉ chi nhánh mình) |
| POST | `/api/shifts/{id}/close` | ADMIN/MANAGER đóng hộ ca nhân viên quên đóng |

Thu ngân phải mở ca mới tạo được hóa đơn (tắt bằng `POS_REQUIRE_OPEN_SHIFT=false`). Mỗi hóa đơn lưu `shift_session_id` của ca đang mở.

### Users (Admin only)
| Method | Endpoint | Mô tả |
|--------|----------|-------|
| GET | `/api/users` | Danh sách nhân viên |
| POST | `/api/users` | Tạo tài khoản nhân viên |
| PUT | `/api/users/{id}` | Cập nhật thông tin |
| PUT | `/api/users/{id}/toggle-status` | Khóa/mở khóa tài khoản |

### WebSocket
| Endpoint | Mô tả |
|----------|-------|
| `/ws` (SockJS) | Kết nối WebSocket STOMP |
| `/topic/inventory-updates` | Thông báo thay đổi tồn kho realtime |

---

## Rủi ro cần lưu ý
- Tích hợp phần cứng (máy in/máy quét) qua Electron/Node.js có thể phát sinh vấn đề driver/tương thích trên Windows — nên test sớm trên máy thật.
- Đóng gói & phân phối ứng dụng Electron (`electron-builder`) cho nhiều máy cần cấu hình auto-update hoặc quy trình cài đặt rõ ràng.
- Độ chính xác mô hình dự báo phụ thuộc nhiều vào lượng dữ liệu lịch sử — với cửa hàng mới ít lịch sử, kết quả dự báo cần được diễn giải thận trọng.
- Đồng bộ đa chi nhánh qua WebSocket khi mất mạng — cần offline-first (SQLite local) và hàng đợi đồng bộ (giai đoạn mở rộng).
- Bảo mật API: Spring Security/JWT đã cấu hình đúng, CORS giới hạn origin an toàn, preflight OPTIONS được permit.
