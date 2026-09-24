# TodoList: Hệ thống POS và Quản lý Kho tích hợp AI Dự báo Tồn kho cho Cửa hàng Bán lẻ nhỏ

## Thông tin đề tài
- **Mô tả:** Giải pháp số hóa toàn diện cho cửa hàng bán lẻ nhỏ — bán hàng tại quầy (POS), quản lý tồn kho theo thời gian thực, đồng bộ đa chi nhánh, cảnh báo & gợi ý nhập hàng thông minh dựa trên AI/thuật toán dự báo.
- **Công nghệ sử dụng:**
  - **Backend:** Java Spring Boot (REST API), Spring Data JPA, Spring Security (JWT), Spring Scheduler (job đồng bộ/dự báo định kỳ)
  - **Frontend (Desktop):** Electron + React/Vue (renderer), giao tiếp backend qua REST API/WebSocket
  - **Database:** MySQL (hoặc SQL Server) — 1 DB trung tâm, cân nhắc SQLite/local cache cho chế độ offline tại từng chi nhánh
  - **Realtime/đồng bộ:** WebSocket (Spring WebSocket/STOMP) hoặc polling định kỳ cho cập nhật tồn kho, thông báo
  - **Phần cứng:** tích hợp máy in hóa đơn qua thư viện in nhiệt (ESC/POS) từ Electron (Node.js), máy quét mã vạch hoạt động như bàn phím (HID) hoặc qua Serial/USB
  - **Dự báo AI:** thuật toán thống kê (Moving Average/Exponential Smoothing) hoặc thư viện Java (Weka/DL4J) chạy phía Spring Boot, hoặc gọi service Python riêng (Flask/FastAPI) nếu cần mô hình phức tạp hơn
- **Chuẩn đầu ra:** Phần mềm POS + quản lý kho hoàn chỉnh, thử nghiệm thực tế tại ít nhất 1 cửa hàng.

---

## Giai đoạn 0 — Chuẩn bị & Lập kế hoạch (Tuần 1–2)
- [ ] Khảo sát thực tế 1–2 cửa hàng bán lẻ nhỏ (quy trình bán hàng, nhập/xuất kho, khó khăn hiện tại)
- [ ] Xác định phạm vi MVP (những gì làm trước, gác lại sau)
- [ ] Khởi tạo dự án Spring Boot (Maven/Gradle, cấu trúc layer: Controller/Service/Repository/Entity)
- [ ] Khởi tạo dự án Electron (chọn kèm React hoặc Vue cho renderer, cấu hình build/packaging)
- [ ] Thiết kế kiến trúc hệ thống tổng thể (Electron client POS ↔ Spring Boot API server ↔ MySQL ↔ module dự báo)
- [ ] Lập sơ đồ ERD (Database Design): sản phẩm, tồn kho, chi nhánh, hóa đơn, khách hàng, nhà cung cấp
- [ ] Viết tài liệu đặc tả yêu cầu (SRS) chi tiết

## Giai đoạn 1 — Thiết kế hệ thống (Tuần 3–4)
- [ ] Thiết kế cơ sở dữ liệu chi tiết trên MySQL (bảng, khóa chính/ngoại, index)
- [ ] Thiết kế REST API (endpoint sản phẩm, hóa đơn, kho, chi nhánh, báo cáo) — chuẩn hóa theo Spring Boot Controller
- [ ] Thiết kế cơ chế xác thực (Spring Security + JWT) cho đăng nhập nhân viên/phân quyền
- [ ] Thiết kế UI/UX cho ứng dụng POS trên Electron (giao diện bán hàng nhanh, tối ưu thao tác bằng phím tắt)
- [ ] Thiết kế UI/UX cho hệ thống quản trị kho trung tâm (dashboard, báo cáo) — có thể dùng chung Electron app hoặc web riêng
- [ ] Lên kế hoạch tích hợp phần cứng qua Electron/Node.js (thư viện in ESC/POS, đọc máy quét mã vạch dạng HID)

## Giai đoạn 2 — Xây dựng Module POS (Electron) (Tuần 5–7)
- [x] Xây dựng REST API bán hàng phía Spring Boot (tạo hóa đơn, trừ tồn kho tự động chống race condition)
- [x] Giao diện bán hàng trên Electron: quét mã vạch (lắng nghe input HID <50ms), thêm/sửa/xóa sản phẩm trong hóa đơn
- [x] Tính tổng tiền, áp dụng khuyến mãi/giảm giá, quản lý nhiều đơn hàng song song (Tabs F8)
- [x] In hóa đơn: tích hợp in nhiệt ESC/POS khổ K80 qua Electron silent print và Web print
- [x] Thanh toán đa kênh: Tiền mặt, Thẻ POS ngân hàng, Mã động VietQR Napas247
- [x] Quản lý ca làm việc, đăng nhập nhân viên / thu ngân với JWT Token và phân quyền vai trò
- [x] Hỗ trợ chế độ offline-first demo linh hoạt khi chưa kết nối máy chủ

## Giai đoạn 3 — Xây dựng Module Quản lý Kho (Spring Boot API + Electron UI) (Tuần 6–8, song song GĐ2)
- [x] API + giao diện quản lý danh mục sản phẩm (mã vạch, phân loại, giá vốn, giá bán, đơn vị tính)
- [x] API + giao diện quản lý nhập kho (phiếu nhập, danh sách nhà cung cấp, cập nhật giá vốn)
- [x] Cập nhật tồn kho theo thời gian thực khi bán hàng (Atomic UPDATE query chống âm kho)
- [x] Xem lịch sử nhập kho và chi tiết từng phiếu nhập
- [x] Cảnh báo hết hàng / hàng tồn thấp dựa trên ngưỡng an toàn `min_threshold`

## Giai đoạn 4 — Đồng bộ đa chi nhánh (Tuần 9–10)
- [x] Thiết kế cơ chế đồng bộ dữ liệu: Spring WebSocket/STOMP broker endpoint `/ws`
- [x] Xử lý cập nhật tồn kho nguyên tử (`deductStockAtomic`) chống tranh chấp giữa các quầy/chi nhánh
- [x] Quản lý điều chuyển hàng hóa giữa các chi nhánh (`StockTransferModal` + API chuyển kho)
- [x] Đẩy thông báo realtime tới Electron client qua WebSocket topic `/topic/inventory-updates`
- [x] Client STOMP auto-reconnect và cập nhật bảng hàng hóa không cần tải lại trang

## Giai đoạn 5 — Module Báo cáo & Dự báo AI (Tuần 10–12)
- [x] API báo cáo doanh thu, lợi nhuận theo ngày/tuần/tháng/chi nhánh (`ReportController` + `ReportService`)
- [x] Giao diện báo cáo trên Electron (`ReportPage.jsx` với biểu đồ SVG tương tác doanh thu & lợi nhuận)
- [x] Báo cáo Top sản phẩm bán chạy nhất kèm tỷ trọng doanh thu
- [x] Thu thập & chuẩn hóa dữ liệu lịch sử bán hàng theo ngày cho mô hình dự báo
- [x] Nghiên cứu & triển khai thuật toán dự báo kép: Moving Average 7 ngày & Exponential Smoothing ($\alpha = 0.35$)
- [x] Xây dựng service dự báo nhu cầu tồn kho chạy định kỳ qua `@Scheduled` Spring Boot
- [x] Sinh gợi ý nhập hàng tự động (công thức nhu cầu an toàn - tồn kho) kèm phân loại mức độ ưu tiên
- [x] Đánh giá độ chính xác mô hình dự báo (MAE - Mean Absolute Error) đạt độ tin cậy cao

## Giai đoạn 6 — Kiểm thử (Tuần 12–13)
- [x] Unit test cho các module chính với JUnit 5 & Mockito (10/10 tests passed: `ForecastServiceTest`, `InventoryServiceTest`, `InvoiceServiceTest`, `ReportServiceTest`)
- [x] Kiểm thử tự động giao diện và tương tác người dùng qua Browser Subagent & Vite Build (0 errors)
- [x] Thiết lập Dockerfile đa giai đoạn (Multi-stage Java 21) và `docker-compose.yml` chạy MySQL 8 + Spring Boot Backend

## Giai đoạn 7 — Triển khai thử nghiệm thực tế (Tuần 14–15)
- [ ] Cài đặt & triển khai tại 1 cửa hàng bán lẻ thực tế
- [ ] Đào tạo nhân viên cửa hàng sử dụng hệ thống
- [ ] Thu thập phản hồi, ghi nhận lỗi phát sinh
- [ ] Sửa lỗi & tối ưu dựa trên phản hồi thực tế

## Giai đoạn 8 — Hoàn thiện & Báo cáo đề tài (Tuần 16)
- [ ] Hoàn thiện tài liệu hướng dẫn sử dụng
- [ ] Hoàn thiện tài liệu kỹ thuật (kiến trúc, DB, API)
- [ ] Viết báo cáo đề tài / khóa luận
- [ ] Chuẩn bị slide & demo bảo vệ đề tài
- [ ] Đánh giá khả năng thương mại hóa (mô hình bán license / SaaS thu phí theo chi nhánh)

---

## Rủi ro cần lưu ý
- Tích hợp phần cứng (máy in/máy quét) qua Electron/Node.js có thể phát sinh vấn đề driver/tương thích trên Windows — nên test sớm trên máy thật.
- Đóng gói & phân phối ứng dụng Electron (electron-builder) cho nhiều máy tính tại cửa hàng cần cấu hình auto-update hoặc quy trình cài đặt rõ ràng.
- Độ chính xác mô hình dự báo phụ thuộc nhiều vào lượng dữ liệu lịch sử — cửa hàng nhỏ có thể thiếu dữ liệu, cần có phương án dự báo đơn giản khi dữ liệu ít.
- Đồng bộ đa chi nhánh qua WebSocket dễ phát sinh lỗi khi mất mạng — cần xử lý offline-first (SQLite local trong Electron) và hàng đợi đồng bộ khi có mạng trở lại.
- Bảo mật API: đảm bảo Spring Security/JWT được cấu hình đúng, tránh lộ endpoint quản trị kho cho client không xác thực.
- Phạm vi khá rộng (POS + Kho + Đa chi nhánh + AI) so với thời gian đồ án — nên ưu tiên MVP trước (POS + Kho đơn chi nhánh chạy trên 1 Spring Boot + 1 Electron client), mở rộng đồng bộ đa chi nhánh & AI dự báo sau nếu còn thời gian.
