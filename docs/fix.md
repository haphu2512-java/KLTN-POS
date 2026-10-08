# 🛠️ Danh sách Bug & Fix — Trạng thái hoàn thành

> Cập nhật lúc: 2026-10-07 16:51 — Đã xử lý & kiểm thử toàn diện bởi Antigravity AI  
> Kết quả kiểm thử: **11/11 JUnit Tests PASS** | **Vite Frontend Build PASS (0 errors)**

---

## ✅ Trạng thái xử lý chi tiết

| STT | Phân loại | Vấn đề | Trạng thái | Giải pháp đã triển khai |
| :--- | :--- | :--- | :---: | :--- |
| **Fix 1** | 🔴 Bảo mật | `/api/auth/me` trả về raw `User` entity làm lộ `passwordHash` | **ĐÃ XONG** | Tạo `UserProfileResponse` DTO (chỉ gồm `id, username, fullName, role, branchId`) và cập nhật `AuthController.java` trả về DTO này. |
| **Fix 2** | 🔴 Bảo mật | CORS quá mở: `allowedOriginPatterns("*")` + `allowCredentials(true)` | **ĐÃ XONG** | Giới hạn danh sách origin an toàn trong `CorsConfig.java` gồm: `http://localhost:5173`, `http://localhost:3000`, `http://127.0.0.1:5173`, `http://127.0.0.1:3000`, `file://*`. |
| **Fix 3** | 🟡 Tính năng | `runNightlyForecastJob()` trong `ForecastService` là stub rỗng | **ĐÃ XONG** | Bổ sung `BranchRepository`, duyệt qua tất cả chi nhánh để tự động tính toán nhu cầu dự báo cho 7 ngày tới, lọc và log cảnh báo sản phẩm cần bổ sung tồn kho. |
| **Fix 4** | 🟡 Concurrency | Race condition khi cộng điểm tích lũy khách hàng (`read-modify-write`) | **ĐÃ XONG** | Thêm query atomic `@Modifying UPDATE Customer c SET c.points = c.points + :pts WHERE c.id = :id` vào `CustomerRepository` và gọi trực tiếp trong `InvoiceService`. |
| **Fix 5** | 🟡 Dự báo AI | Fallback dữ liệu dự báo dùng mảng số cứng `[2.0, 3.0, ...]` | **ĐÃ XONG** | Khi sản phẩm mới chưa có lịch sử bán hàng (`dailySales.isEmpty()`): gán `hasHistory = false`, `suggestedRestockQty = 0`, `dailyDemandForecast = 0.0` để tránh tạo đề xuất nhập hàng sai lệch. |
| **Fix 6** | 🟢 Chất lượng code | `InventoryService` nuốt lỗi WebSocket `catch (Exception ignored)` | **ĐÃ XONG** | Thêm `@Slf4j` và ghi log cảnh báo: `log.warn("WS notify failed branchId={} productId={}: {}", branchId, productId, e.getMessage())`. |
| **Fix 7** | 🟢 Chất lượng code | Dependency H2 database scope đang là `runtime` | **ĐÃ XONG** | Cập nhật `pom.xml` chuyển scope sang `<scope>test</scope>` để tránh đóng gói H2 vào production bundle. |
| **Fix 8** | 🟢 Hiệu năng | `getAllInvoices` thiếu phân trang (Pagination) | **ĐÃ XONG** | Thêm `Page<Invoice> findByBranchId(Long, Pageable)` vào `InvoiceRepository`, cung cấp `getInvoices(branchId, pageable)` trong `InvoiceService`, và cập nhật `InvoiceController` hỗ trợ params `page` & `size` linh hoạt. |
| **Fix 9** | 🐳 DevOps | `Dockerfile` lỗi copy `mvnw` do thiếu wrapper script | **ĐÃ XONG** | Cập nhật `BACKEND/Dockerfile` dùng base image `maven:3.9-eclipse-temurin-21-alpine` build JAR độc lập, không phụ thuộc vào `mvnw` local. |

---

## 🧪 Kết quả kiểm thử sau khi sửa

- **Backend Unit Tests:** `mvn test` chạy thành công **11/11 tests pass** (bao gồm cả test case mới cho sản phẩm không có lịch sử bán hàng và atomic update điểm thưởng).
- **Frontend Build:** `npm run build` chạy thành công không có warning hay lỗi nào.
