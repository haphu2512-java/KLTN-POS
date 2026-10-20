# KLTN POS — Review toàn bộ dự án (20/10/2026, main @ 9845ab6)

Phạm vi: backend Spring Boot, Electron/React (`FRONTEND/`), app Expo (`MOBILE/`), mọi nhánh và các PR #4 đến #8. Bản này nối tiếp hai review cũ trong cùng thư mục (`kltn-pos-review.md`, `mobile-scanner-review.md`); những mục đã sửa chỉ được liệt kê ngắn ở cuối.

## Tóm tắt

- Toàn bộ PR #1 đến #8 đã merge vào `main`. PR #7 (bảo mật scanner) không còn mở. CI xanh trên `main` và `dev`.
- Chạy lại tại máy: `mvn test` đạt **42 test**; `npm run lint` của FRONTEND còn **0 lỗi, 4 cảnh báo**; `npm run build` đạt (chunk JS 524 kB).
- Hầu hết lỗi nghiêm trọng của hai review cũ đã được sửa. Còn **3 lỗi chặn việc chạy thử ở cửa hàng** (mục A1 đến A3) và **1 lỗ hổng bảo mật mới, đã chứng minh bằng test** (B1).

## Nhánh

| Nhánh | Trạng thái | Đề xuất |
|---|---|---|
| `main` | 9845ab6, đã gồm PR #8 | Giữ |
| `dev` | Cùng nội dung với `main` (chỉ khác 1 commit merge e6861b9) | Giữ làm nhánh làm việc |
| `test` | Commit đầu tiên (16/09), sau `main` 28 commit, không có gì riêng | Xóa |
| `claude/project-thread-nzmlkv` | Đã merge (PR #4, #5) | Xóa |
| `claude/project-thread-r6buz6` | Đã merge (PR #7) | Xóa |

Không có nhánh nào chứa thay đổi chưa merge hay xung đột với `main`.

PR #8 (sửa `Table.jsx` và các cột của Staff/Partner/Report) đúng hướng: mọi cột giờ dùng `render(val, row)`. Riêng phần `try/catch` trong `components/common/Table.jsx` gọi lại `render(row, idx)` khi lỗi thì nên bỏ, vì nó che lỗi thật của cột và có thể hiện dữ liệu sai thay vì báo lỗi.

---

## A. Chặn việc chạy thử tại cửa hàng (sửa trước tiên)

### A1. Bản Electron đóng gói sẽ hiện màn hình trắng
`electron/main.cjs` mở `dist/index.html` bằng `loadFile`, nhưng `vite.config.js` không đặt `base: './'`, nên `dist/index.html` trỏ tới `/assets/index-*.js` (đã kiểm tra sau `npm run build`). Với `file://`, đường dẫn này thành `file:///assets/...` và không tải được. Chỉ chạy ở chế độ dev (`npm run dev:electron`) mới thấy giao diện.

Thêm vào đó, `api.js` mặc định `baseURL = '/api'` và `websocketService.js` dựng `ws://${window.location.hostname}:8080/ws`; khi chạy từ `file://` thì hostname rỗng. Nếu build không có `.env` chứa `VITE_API_BASE_URL`/`VITE_WS_URL` thì cả API lẫn WebSocket đều hỏng.

Sửa: `base: './'` trong Vite; bắt buộc có URL máy chủ khi build (hoặc màn hình cấu hình địa chỉ server, lưu trong `localStorage`); thử `npm run electron:build` rồi chạy file cài đặt trên một máy Windows sạch.

### A2. In hóa đơn sẽ in cả màn hình POS chứ không phải hóa đơn
`receiptPrinter.js` ghi hóa đơn vào một iframe 0×0, rồi gọi `electronAPI.printReceipt`, mà handler `printer:print-receipt` trong `main.cjs` lại gọi `mainWindow.webContents.print(...)`, tức in **toàn bộ cửa sổ chính**. Không có CSS `@media print` nào ẩn giao diện POS (chỉ có trong `BarcodePrintModal.css`). Kết quả trên máy in K80 sẽ là ảnh màn hình bán hàng bị co lại.

Sửa: trong main process, mở một `BrowserWindow` ẩn, `loadURL('data:text/html,...')` với HTML hóa đơn rồi gọi `print` trên cửa sổ đó (cách chuẩn cho in nhiệt bằng Electron). Đồng thời:
- Hóa đơn in mã `HD` + 6 số cuối timestamp và "Chi nhánh {id}", không phải mã hóa đơn và tên chi nhánh thật. Nên in từ dữ liệu `createInvoice` trả về (`id`, `totalAmount`, `changeAmount`, `branch.name`).
- `item.name` và tên khách được chèn thẳng vào HTML không escape. Iframe cùng origin với app, nên một tên sản phẩm chứa `<img onerror=...>` có thể đọc `localStorage` (JWT). Nên escape.

### A3. Mất mạng lúc thanh toán: báo thành công, in hóa đơn, nhưng đơn không được lưu
`PosPage.jsx`, `executeFinishPayment`: nếu `createInvoice` lỗi mà không có `err.response` (mất mạng, hết 15 giây timeout), code vẫn kêu bíp thành công, trừ tồn kho trên màn hình, in hóa đơn và báo "Thanh toán thành công". Không có hàng đợi, nên đơn đó không bao giờ vào DB, tồn kho và doanh thu sai. Ngược lại, khi timeout mà server thực ra đã lưu, thu ngân không biết và có thể bán lại lần nữa.

Ngoài ra không có chặn bấm hai lần: `PosPayment` có prop `isProcessing` nhưng `PosPage` không truyền, và phím F4 gọi thẳng `handleProcessPayment`. Bấm nhanh hai lần sẽ tạo hai hóa đơn và trừ kho hai lần.

Sửa (tối thiểu cho chạy thử): khi không có phản hồi thì **báo lỗi, không in, giữ nguyên giỏ**; thêm cờ `isProcessing` cho nút và F4; gửi kèm một `clientInvoiceId` (UUID tạo khi mở đơn) và để server bỏ qua bản trùng (cột unique). Hàng đợi offline thật (giai đoạn đã ghi trong `StepByStep_Implementation.md`) có thể để sau, nhưng không được báo thành công giả.

---

## B. Bảo mật

### B1. Bất kỳ tài khoản nào cũng bắn được hàng vào giỏ POS của người khác qua WebSocket (đã chứng minh)
PR #7 chuyển mã quét sang `/user/queue/barcode-scans`, nhưng `WebSocketConfig` chỉ kiểm tra JWT ở frame `CONNECT`, không kiểm tra `SEND`/`SUBSCRIBE`. Spring chuyển các frame `SEND` tới `/user/{username}/...` và `/topic/...` thẳng tới broker. Tôi viết một test tạm (đã xóa, không commit): tài khoản `admin` kết nối STOMP và gửi `SEND /user/cashier/queue/barcode-scans {"type":"SCAN","barcode":"INJECTED"}`, và POS của `cashier` nhận được tin đó. Như vậy:
- Tài khoản WAREHOUSE (không có quyền `/api/scanner/broadcast`) hoặc thu ngân chi nhánh khác vẫn chèn được hàng vào giỏ bất kỳ quầy nào, hoặc gửi `BATCH` giả.
- Cũng gửi được tin giả vào `/topic/inventory-updates`.
- Mỗi mã không có trong danh mục làm POS bật `alert()` chặn cửa sổ, nên có thể spam để làm treo quầy.

Sửa: trong interceptor `configureClientInboundChannel`, từ chối mọi `SEND` có đích bắt đầu bằng `/topic`, `/queue`, `/user` (client chỉ được `SUBSCRIBE`; server tự gửi qua `SimpMessagingTemplate`). Hoặc dùng `spring-security-messaging` với `simpDestMatchers(...).denyAll()`. Thêm một test như trên vào `ScannerWebSocketDeliveryTest` để giữ hồi quy.

### B2. Giá vốn vẫn lộ cho thu ngân qua API của desktop
PR #7 bỏ `costPrice` khỏi API mobile, nhưng `GET /api/products`, `/api/products/{id}`, `/api/products/barcode/{code}` trả nguyên entity `Product`; `POST/GET /api/invoices` trả `Invoice → details → product` cũng có `costPrice` (và `InvoiceDetail.costPrice`). Mọi vai trò đều gọi được. Nếu giá vốn là thông tin cần giấu với thu ngân thì cần DTO theo vai trò, hoặc `@JsonView`.

### B3. Phạm vi chi nhánh chỉ áp dụng cho tạo hóa đơn
`InvoiceService.createInvoice` đã lấy nhân viên/chi nhánh từ JWT (PR #4), nhưng các chỗ khác vẫn tin request:
- `StockInService.createStockIn`: `branchId` và `employeeId` lấy từ request (frontend gửi `currentUser?.userId || 1`). Thủ kho chi nhánh 2 nhập kho được vào chi nhánh 1, và phiếu nhập ghi sai người lập.
- `InventoryService.transferStock`: MANAGER/WAREHOUSE chi nhánh 2 rút hàng khỏi chi nhánh 1 được.
- `GET /api/invoices` (không truyền `branchId`) trả **toàn bộ hóa đơn của mọi chi nhánh, không phân trang** cho bất kỳ ai đã đăng nhập; `GET /api/invoices/{id}` cũng không kiểm tra chi nhánh. Báo cáo/dự báo của MANAGER cũng xem được chi nhánh khác.

Với một cửa hàng một chi nhánh thì ảnh hưởng thấp, nhưng nên làm cho nhất quán với logic của hóa đơn (ADMIN được chọn, vai trò khác bị khóa vào chi nhánh của mình).

### B4. Các điểm nhỏ hơn
- `CustomerController.create` nhận thẳng entity `Customer`, nên thu ngân tạo được khách với `points` tùy ý (`createCustomer` giữ nguyên `points` nếu có). Hiện chưa có chức năng đổi điểm nên thiệt hại thấp; nên dùng DTO.
- `GlobalExceptionHandler` trả `ex.getMessage()` của mọi exception cho client (lộ câu SQL, tên bảng khi lỗi DB). Nên log chi tiết và trả câu chung.
- Không giới hạn số lần đăng nhập sai; tài khoản seed `admin/123456`, `cashier/123456`. Trước khi chạy thử phải đổi mật khẩu và đặt `JWT_SECRET` riêng (hiện chỉ có cảnh báo trong log).
- Đổi mật khẩu không vô hiệu token cũ (token vẫn dùng được tới 24 giờ). Khóa tài khoản thì đã chặn được.

---

## C. Sai lệch giữa ba phần (hợp đồng API, đồng bộ)

### C1. Cập nhật tồn kho realtime không hoạt động
Backend gửi `/topic/inventory-updates` với `{branchId, productId}` (`InventoryService.notifyInventoryUpdate`). Cả `PosPage.jsx` (dòng 79) và `InventoryPage.jsx` (dòng 107) đọc `event.newQuantity ?? p.stock`, mà trường này không bao giờ có, nên tồn kho trên màn hình **không bao giờ đổi** khi quầy khác bán hoặc khi nhập kho. Phía client cũng không lọc `branchId`. Đây là tính năng chính của giai đoạn 4 trong README.

Sửa: gửi kèm `newQuantity` (đọc lại sau khi update) và lọc theo chi nhánh ở client. Nên gửi tin **sau khi transaction commit** (`TransactionSynchronization.afterCommit` hoặc `@TransactionalEventListener`), vì hiện tại nếu sản phẩm thứ hai trong hóa đơn hết hàng và cả giao dịch rollback, tin của sản phẩm thứ nhất vẫn đã được gửi.

`/topic/sales-updates` được frontend subscribe nhưng backend không bao giờ gửi.

### C2. Phiên WebSocket không đổi khi đăng xuất / đổi tài khoản trên máy POS
`AuthContext.logout` không gọi `websocketService.disconnect()`. Kết nối STOMP vẫn mang danh tính tài khoản cũ cho tới khi rớt mạng. Nếu ca sáng `cashier1` đăng xuất và `cashier2` đăng nhập trên cùng máy, điện thoại của `cashier2` quét sẽ **không** tới quầy, còn điện thoại của `cashier1` vẫn bắn vào máy này. Sửa: disconnect khi logout và connect lại sau login.

### C3. Ghép điện thoại theo tài khoản: hạn chế khi dùng chung tài khoản
Thiết kế PR #7 gửi mã tới mọi phiên của cùng tài khoản. Nếu cửa hàng dùng một tài khoản `cashier` cho 2 quầy thì một lần quét vào cả 2 giỏ. Cần ghi rõ trong hướng dẫn chạy thử: mỗi quầy một tài khoản. Ngoài ra khi không có POS nào đang mở, `convertAndSendToUser` âm thầm bỏ tin nhưng điện thoại vẫn báo "Đã gửi tới quầy POS". Có thể dùng `SimpUserRegistry` để trả về số phiên nhận được và báo "Không có quầy POS nào đang mở".

### C4. Trạng thái online trên desktop là giả
`isBackendOnline` chỉ được đặt `true` khi có token và không bao giờ chuyển về `false`, nên badge "Online" và nhánh `isOnline` trong POS không phản ánh thực tế. Gắn nó với trạng thái WebSocket hoặc một ping nhẹ (`/api/scanner/ping` đã có).

### C5. Giá thay đổi khi POS đang mở
Thu ngân bán theo giá đã tải lúc mở trang. Khi quản lý đổi giá, server từ chối (`PRICE_OVERRIDE_FORBIDDEN`, "Vui lòng tải lại") nhưng POS không có nút tải lại và chỉ tải lại danh mục khi đổi chi nhánh. Nên tự tải lại danh mục khi gặp lỗi này (hoặc khi nhận tin cập nhật sản phẩm).

---

## D. Độ đúng của dự báo

### D1. Ngày hôm nay (chưa hết ngày) được tính như một ngày đủ
`ForecastService.getDemandForecastAndReplenishment` lấy cửa sổ 14 ngày **kết thúc ở hôm nay**. Job ban đêm chạy lúc 01:00, khi hôm nay gần như chưa bán gì, nên điểm cuối của chuỗi gần bằng 0. Với làm trơn hàm mũ `alpha = 0.35`, điểm cuối chiếm 35% trọng số, nên dự báo được cache mỗi đêm bị **thấp hơn thực tế khoảng một phần ba** với hàng bán đều. Gọi realtime ban ngày cũng bị kéo xuống tương tự. Sửa: dùng 14 ngày đã kết thúc (`lastDay = today - 1`). Đây là điểm dễ bị hỏi khi bảo vệ, và nên có một test: chuỗi đều 10/ngày phải cho ra 10.

### D2. Ghi cache ban đêm không nguyên tử (nhỏ)
`persistForecastResults` vẫn được gọi qua `this` nên `@Transactional` của nó không có tác dụng; job vẫn chạy được vì `deleteByBranchId` và `saveAll` tự có transaction riêng. Hệ quả là giữa lúc xóa và lúc ghi, API sẽ thấy cache rỗng và tính realtime; nếu `saveAll` lỗi thì chi nhánh mất cache tới đêm sau. Có thể chấp nhận; nếu sửa thì tách sang bean khác hoặc dùng `TransactionTemplate`.

---

## E. App mobile

### E1. Bản APK build thật sẽ không kết nối được server HTTP trong LAN
App gọi `http://192.168.x.x:8080`. Expo Go cho phép, nhưng bản build release Android (targetSdk mới) chặn HTTP không mã hóa theo mặc định. `app.json` chưa bật `usesCleartextTraffic` (qua plugin `expo-build-properties`). Nếu định cài APK cho nhân viên thì cần thêm cấu hình này và thử trên bản build thật, không chỉ Expo Go.

### E2. Các điểm còn lại
- Tài khoản WAREHOUSE đăng nhập được, nhưng khi quét ở chế độ "bắn vào POS" nhận 403 và bị **đăng xuất** (`authFetch` coi 403 là `AuthError`). Nên hiện thông báo và chuyển sang chế độ tra giá thay vì đăng xuất.
- Server lỗi 500 khi bắn mã cũng bị coi là mất mạng và xếp vào hàng đợi offline.
- Hàng đợi offline không gắn với tài khoản: đăng xuất rồi đăng nhập tài khoản khác, hàng đợi cũ sẽ đồng bộ vào POS của tài khoản mới.
- Chống trùng `clientScanId` chỉ nằm trong bộ nhớ server; khởi động lại server rồi đồng bộ lại vẫn có thể trùng (POS có hỏi xác nhận nên ảnh hưởng thấp).
- Còn tồn từ review mobile cũ: dữ liệu `SEED_PRODUCTS` với giá và tồn kho tự đặt vẫn được ghi vào cache khi chưa tải danh mục (mục 10), nút mã mẫu (Mock) vẫn hiện trong bản thật, bàn phím nhập tay chỉ có số (mục 14), `SafeAreaView` cũ của react-native (mục 18), emoji làm icon (mục 17), chưa tự đồng bộ khi có mạng trở lại, file template `MOBILE/AGENTS.md`, `LICENSE`, `.claude/settings.json` (mục 23).
- `SettingsModal`/`config.js` vẫn lưu `branchId` cục bộ, không còn được dùng vì chi nhánh lấy từ JWT. Nên bỏ để tránh nhầm.

---

## F. Test và CI

- CI có 3 job (backend `mvn test`, frontend lint + build, mobile `expo export`). Tốt.
- Backend có 42 test, gồm test bảo mật API, phân quyền scanner và một test STOMP thật. Còn thiếu: test chặn `SEND` STOMP (B1), test phạm vi chi nhánh cho nhập kho/điều chuyển (B3), test dự báo với chuỗi đều (D1), test payload `inventory-updates` (C1).
- Integration test dùng H2 chế độ MySQL. Các truy vấn có `CAST(... AS LocalDate)`, `FUNCTION('DATE', ...)` và khóa `PESSIMISTIC_WRITE` chưa được thử trên MySQL thật. Nên chạy một lần với Testcontainers MySQL, hoặc ít nhất thử bằng tay với `docker compose` trước khi ra cửa hàng.
- Frontend và mobile chưa có unit test nào. Ưu tiên: luồng thanh toán của `PosPage` (A3) và `offlineStorage`/`syncOfflineQueue`.
- CI không chạy `electron:build`, nên lỗi A1 không bị phát hiện. Có thể thêm một bước kiểm tra `dist/index.html` không chứa `src="/assets`.

## G. Vệ sinh repo và vận hành

- `bhx_products_scraped.json`, `import_bhx_data.sql`, `scrape_bhx.py`, thư mục `skills/` vẫn nằm ở gốc repo (mục 17 cũ).
- `spring.jpa.open-in-view=true`, `ddl-auto=update` vẫn bật (mục 18 cũ). Trước chạy thử: tắt `open-in-view`, chuyển `ddl-auto=validate` hoặc dùng Flyway, và **lên lịch sao lưu MySQL** (`mysqldump` hằng ngày). Hiện không có gì sao lưu dữ liệu bán hàng.
- `docs/fix.md` vẫn mô tả CORS chỉ localhost, không khớp cấu hình hiện tại.
- POS dùng `alert()` cho mọi lỗi và cả thông báo thanh toán thành công; trên quầy thật mỗi đơn phải bấm OK một lần. Nên đổi sang toast.
- Chunk JS 524 kB: có thể tách route bằng `React.lazy` (không gấp).

---

## Đã sửa so với hai review cũ (không lặp lại)

Từ `kltn-pos-review.md`: giá do server quyết định, giảm giá có giới hạn, nhân viên/chi nhánh hóa đơn lấy từ JWT (1); phân quyền endpoint (2); chặn tài khoản bị khóa (3); WebSocket bắt buộc JWT và giới hạn origin (5); tiền khách đưa thiếu, khóa khi hoàn trả (6); dự báo theo chi nhánh, điền 0 ngày trống, bỏ hóa đơn hoàn trả (7–9); job ban đêm ghi được cache (10, xem D2); dự báo dùng 2 truy vấn thay vì 4.000 (11); sắp xếp ngày trong báo cáo (12); lợi nhuận dùng giá vốn lúc bán (13); lint (14); CI (15).

Từ `mobile-scanner-review.md`: scanner bắt buộc đăng nhập, DTO không có giá vốn, giới hạn 200 mã (1); gửi theo tài khoản thay vì topic chung (2, xem B1/C3); tra giá tách khỏi bắn vào giỏ (3); đồng bộ offline thành một lô có xác nhận trên POS và đúng thứ tự (4); xóa hàng đợi theo id, chống trùng `clientScanId`, validate (5, 6, 8); timeout 8 giây (7); tồn kho thật theo chi nhánh (9, trừ seed); endpoint ping nhẹ (12); câu thông báo sai (11); CI bundle Expo (22).

## Thứ tự đề xuất trước ngày chạy thử

1. A1, A2: build Electron chạy được và in đúng hóa đơn K80 (thử trên máy in thật).
2. A3: không báo thành công khi đơn chưa lưu; chặn bấm hai lần; chống trùng hóa đơn.
3. B1: chặn `SEND` STOMP từ client.
4. C1, C2: tồn kho realtime đúng, WebSocket đổi theo tài khoản.
5. D1: bỏ ngày hôm nay khỏi chuỗi dự báo.
6. Vận hành: đổi mật khẩu seed, `JWT_SECRET` riêng, sao lưu MySQL, `ddl-auto=validate`, mỗi quầy một tài khoản; nếu dùng APK thì E1.
7. Sau đó: B2, B3, test còn thiếu, các điểm nhỏ.
