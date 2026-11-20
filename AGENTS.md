# Hướng dẫn cho trợ lý AI (Codex, Copilot, Claude...)

Đọc [`docs/HANDOVER.md`](docs/HANDOVER.md) trước khi làm bất cứ việc gì: tổng quan, cách chạy, quy ước và trạng thái hiện tại.

Quy tắc bắt buộc:

- Mọi pull request mở vào nhánh **`dev`** (base = `dev`), không mở vào `main`.
- Trước khi báo xong, chạy kiểm tra của phần đã sửa (giống CI):
  - Backend: `cd BACKEND && mvn -B test`
  - Frontend: `cd FRONTEND && npm run lint && npm test && npm run build`
  - Mobile: `cd MOBILE && npx expo export --platform android --output-dir dist` (xem thêm `MOBILE/AGENTS.md`)
  - AI_FORECAST: `cd AI_FORECAST && python -m pytest -q`
- Không tắt hay bỏ qua test để CI xanh.
- Frontend không dùng `window.alert/confirm/prompt`; dùng `toast`, `confirmDialog`, `promptDialog` trong `FRONTEND/src/components/common/toastStore.js`.
- Thêm bảng/cột: thêm migration Flyway mới (`BACKEND/src/main/resources/db/migration/V<n>__*.sql`, không sửa file cũ) và cập nhật SQL dữ liệu mẫu trong `data/`.
- Quy tắc nhà cung cấp – danh mục dùng chung `SupplierCategoryRule`, không viết lại.
- Sửa code thì sửa luôn tài liệu liên quan (`README.md`, `setup.md`, `.env.example`).
- Không commit `.env` hay bí mật. Biến `VITE_*` và `EXPO_PUBLIC_*` bị nhúng vào bundle, không đặt bí mật vào đó.
- Giao diện và thông báo viết tiếng Việt có dấu.
