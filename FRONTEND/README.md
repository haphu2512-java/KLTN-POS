# Ứng dụng Desktop POS & Quản lý Kho (Electron + React 19 + Vite)

Ứng dụng quầy bán hàng (POS Terminal) và quản lý kho bán lẻ đa chi nhánh, được thiết kế chuyên biệt cho thao tác siêu tốc tại quầy (Keyboard-First), đồng bộ dữ liệu thời gian thực và hỗ trợ thiết bị phần cứng bán lẻ.

---

## 🚀 Tính năng nổi bật

1. **POS Bán hàng siêu tốc (Keyboard-First):**
   - Phím tắt: `F1` (Trợ giúp), `F2` (Tra cứu hàng), `F3` (Chọn khách & tích điểm), `F4` (Thanh toán), `F8` (Thêm đơn mới), `F9` (Hủy đơn), `Esc` (Đóng hộp thoại).
   - Tự động nhận diện máy quét mã vạch chuyên dụng (Hardware HID Scanner) qua buffer thời gian `< 50ms`.
   - Phản hồi âm thanh qua Web Audio API: tiếng *beep* tần số 800Hz khi quét thành công, tiếng *buzz* 220Hz khi có lỗi/hết hàng.
   - Tạo mã động **VietQR Napas247** hiển thị ngay tại quầy cho khách quét chuyển khoản.
   - Ghép điện thoại (app `MOBILE/`) làm máy quét không dây bằng mã QR.
   - Phải mở ca mới bán được; đóng ca đối soát tiền mặt, VietQR, thẻ.

2. **Quản lý Kho & Nhập hàng:**
   - Quản lý danh mục hàng hóa (hơn 1.949 mã hàng thực tế).
   - Nhập hàng từ nhà cung cấp (Stock-In) kèm tính tổng chi phí và tự động cộng tồn kho.
   - Điều chuyển tồn kho giữa các chi nhánh an toàn qua 1 thao tác.
   - Thẻ theo dõi trực quan: Tổng sản phẩm, Tổng tồn kho, Cảnh báo sắp hết hàng, Giá trị tồn kho (VND).

3. **Dự báo Tồn kho AI (AI Demand Forecasting):**
   - Biểu đồ SVG: doanh số thực tế 30 ngày và đường dự báo 7/14/30 ngày tới, MAE/RMSE và độ tin cậy do backend tính.
   - Thuật toán Exponential Smoothing ($\alpha = 0.35$) hoặc Moving Average 7 ngày, công thức theo `AI_for_POS.md`.
   - Bảng đề xuất nhập (Khẩn cấp / Cao / Bình thường) kèm điểm đặt hàng lại, tồn kho an toàn và lý do; nút "Lên phiếu nhập" mở phiếu nhập điền sẵn ở trang Kho để kiểm tra rồi lưu.

4. **Báo cáo & Lịch sử giao dịch:**
   - Thống kê doanh thu, số đơn, lợi nhuận, giá trị trung bình/đơn.
   - Tìm kiếm lịch sử hóa đơn, xem chi tiết và xử lý hoàn trả/hủy hóa đơn (tự động hoàn kho).

5. **Đồng bộ thời gian thực (Real-time Synchronization):**
   - Kết nối WebSocket STOMP (`@stomp/stompjs` + `sockjs-client`).
   - Lắng nghe kênh `/topic/inventory-updates` để tự động cập nhật số lượng tồn kho trên toàn bộ quầy POS khi có biến động kho mà không cần F5.

---

## 🛠️ Công nghệ sử dụng

- **Frontend Core:** React 19, React Router DOM 7
- **Build Tool:** Vite 8 (Hot Module Replacement)
- **Desktop Runtime:** Electron 44, Electron Builder 26
- **Giao tiếp API:** Axios (kèm Interceptor tự gắn JWT Token)
- **Đồng bộ Realtime:** `@stomp/stompjs`, `sockjs-client`
- **Biểu đồ & UI:** Thuần CSS tối ưu hiệu năng (Vanilla CSS / Design System), SVG Charts

---

## 📁 Cấu trúc thư mục

```text
FRONTEND/
├── electron/
│   ├── main.cjs             # Tiến trình chính Electron (Cửa sổ, IPC, In nhiệt K80)
│   └── preload.cjs          # ContextBridge an toàn cách ly dữ liệu
├── src/
│   ├── components/
│   │   ├── common/          # Button, Input, Table, Badge, Modal, Tabs, BarcodeView
│   │   ├── forecast/        # Biểu đồ SalesTrendChart (SVG)
│   │   └── layout/          # AppShell (thanh điều hướng)
│   ├── pages/
│   │   ├── pos/             # Màn hình bán hàng POS và các Modal
│   │   ├── inventory/       # Màn hình kho, nhập kho, điều chuyển
│   │   ├── forecast/        # Màn hình dự báo nhu cầu & đề xuất nhập hàng
│   │   ├── report/          # Báo cáo doanh thu & lịch sử hóa đơn
│   │   ├── shifts/          # Lịch ca, xếp ca, lịch sử ca và đối soát
│   │   ├── partner/         # Quản lý khách hàng thân thiết & NCC
│   │   └── admin/           # Tab Tài khoản: tài khoản, vai trò, chi nhánh
│   ├── services/            # API services (auth, sale, inventory, forecast, shift, scannerPairing,...)
│   ├── hooks/               # useBarcodeScanner, useHotkeys, useAppNavigate, useBranches
│   ├── utils/               # formatters, audio, receiptPrinter, exportCsv, forecasting
│   ├── App.jsx              # Định tuyến và bố cục chính
│   └── main.jsx
├── vite.config.js           # Cấu hình proxy /api -> http://localhost:8080
└── package.json
```

---

## 💻 Hướng dẫn cài đặt & Khởi chạy

### 1. Cài đặt thư viện phụ thuộc
```bash
cd FRONTEND
npm install
```

### 2. Chạy môi trường Web (Vite Dev Server)
```bash
npm run dev
```
> Truy cập trình duyệt tại: `http://localhost:5173`

### 3. Chạy môi trường Desktop (Electron + Vite)
Chạy đồng thời Vite server và cửa sổ Electron desktop:
```bash
npm run dev:electron
```

### 4. Đóng gói ứng dụng Desktop (.exe Installer cho Windows)
```bash
npm run electron:build
```
File cài đặt sẽ được tạo ra tại thư mục: `FRONTEND/release/`

---

## ⚙️ Cấu hình kết nối Backend

- Khi chạy chế độ dev, `vite.config.js` đã được thiết lập proxy chuyển tiếp toàn bộ request `/api` và `/ws` về backend `http://localhost:8080`.
- Build Electron: đặt `VITE_API_BASE_URL` và `VITE_WS_URL` trong `FRONTEND/.env` (xem `FRONTEND/.env.example`).
- Tài khoản mẫu `admin` và `cashier` được tạo khi database còn trống, mật khẩu là `SEED_DEFAULT_PASSWORD` trong `.env` ở thư mục gốc.
