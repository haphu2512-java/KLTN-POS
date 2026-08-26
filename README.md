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
- [ ] Xây dựng REST API bán hàng phía Spring Boot (tạo hóa đơn, trừ tồn kho tự động)
- [ ] Giao diện bán hàng trên Electron: quét mã vạch (lắng nghe input HID), thêm/sửa/xóa sản phẩm trong hóa đơn
- [ ] Tính tổng tiền, áp dụng khuyến mãi/giảm giá
- [ ] In hóa đơn: tích hợp thư viện in nhiệt ESC/POS qua Node.js (electron-pos-printer hoặc node-thermal-printer)
- [ ] Thanh toán (tiền mặt/thẻ/QR - tùy phạm vi)
- [ ] Quản lý ca làm việc, đăng nhập nhân viên (gọi API JWT từ Spring Boot)
- [ ] Lưu lịch sử giao dịch offline (SQLite local trong Electron) khi mất kết nối, đồng bộ lại khi có mạng

## Giai đoạn 3 — Xây dựng Module Quản lý Kho (Spring Boot API + Electron UI) (Tuần 6–8, song song GĐ2)
- [ ] API + giao diện quản lý danh mục sản phẩm (thêm/sửa/xóa, phân loại, mã vạch)
- [ ] API + giao diện quản lý nhập kho (phiếu nhập, nhà cung cấp, giá vốn)
- [ ] Cập nhật tồn kho theo thời gian thực khi bán hàng (Service layer xử lý transaction trừ kho)
- [ ] Kiểm kê kho (đối chiếu tồn kho thực tế vs hệ thống)
- [ ] Cảnh báo hết hàng / hàng tồn thấp — job định kỳ (Spring Scheduler) hoặc trigger khi tồn < ngưỡng

## Giai đoạn 4 — Đồng bộ đa chi nhánh (Tuần 9–10)
- [ ] Thiết kế cơ chế đồng bộ dữ liệu: WebSocket (Spring WebSocket/STOMP) cho realtime, hoặc REST polling định kỳ
- [ ] Xử lý xung đột dữ liệu (conflict resolution) giữa các chi nhánh (versioning/timestamp)
- [ ] Quản lý điều chuyển hàng hóa giữa các chi nhánh (API + UI Electron)
- [ ] Đẩy thông báo realtime tới Electron client (cảnh báo tồn kho, đơn hàng mới) qua WebSocket
- [ ] Kiểm thử đồng bộ với nhiều chi nhánh giả lập (nhiều instance Electron kết nối cùng backend)

## Giai đoạn 5 — Module Báo cáo & Dự báo AI (Tuần 10–12)
- [ ] API báo cáo doanh thu, lợi nhuận theo ngày/tuần/tháng/chi nhánh (Spring Boot, dùng JPA/native query)
- [ ] Giao diện báo cáo trên Electron (bảng biểu, biểu đồ — có thể dùng Chart.js/Recharts trong renderer)
- [ ] Báo cáo sản phẩm bán chạy/chậm
- [ ] Thu thập & chuẩn hóa dữ liệu lịch sử bán hàng cho mô hình dự báo
- [ ] Nghiên cứu & lựa chọn thuật toán dự báo (Moving Average, Exponential Smoothing đơn giản trong Java; hoặc gọi microservice Python riêng nếu cần mô hình phức tạp hơn)
- [ ] Xây dựng module/service dự báo nhu cầu tồn kho (chạy job định kỳ bằng Spring Scheduler)
- [ ] Sinh gợi ý nhập hàng tự động dựa trên kết quả dự báo, hiển thị trên dashboard Electron
- [ ] Đánh giá độ chính xác mô hình dự báo (MAE/RMSE) với dữ liệu thực/giả lập

## Giai đoạn 6 — Kiểm thử (Tuần 12–13)
- [ ] Unit test cho các module chính (bán hàng, kho, đồng bộ, dự báo)
- [ ] Kiểm thử tích hợp toàn hệ thống (end-to-end)
- [ ] Kiểm thử hiệu năng khi dữ liệu lớn (nhiều giao dịch/sản phẩm)
- [ ] Kiểm thử tích hợp phần cứng thực tế (máy in, máy quét)

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
