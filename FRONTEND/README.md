# Ứng dụng Desktop POS & Quản lý Kho (Electron + React 19 + Vite)

Ứng dụng quầy bán hàng (POS Terminal) và quản lý kho bán lẻ đa chi nhánh, được thiết kế chuyên biệt cho thao tác siêu tốc tại quầy (Keyboard-First), đồng bộ dữ liệu thời gian thực và hỗ trợ thiết bị phần cứng bán lẻ.

---

## 🚀 Tính năng nổi bật

1. **POS Bán hàng siêu tốc (Keyboard-First):**
   - Hệ thống phím tắt nhanh: `F1` (Tập trung quét mã), `F2` (Tìm kiếm sản phẩm), `F3` (Khách hàng & tích điểm), `F4` (In lại hóa đơn gần nhất), `F9` (Mở modal thanh toán), `Esc` (Đóng nhanh modal).
   - Tự động nhận diện máy quét mã vạch chuyên dụng (Hardware HID Scanner) qua buffer thời gian `< 50ms`.
   - Phản hồi âm thanh qua Web Audio API: tiếng *beep* tần số 800Hz khi quét thành công, tiếng *buzz* 220Hz khi có lỗi/hết hàng.
   - Tạo mã động **VietQR Napas247** hiển thị ngay tại quầy cho khách quét chuyển khoản.

2. **Quản lý Kho & Nhập hàng:**
   - Quản lý danh mục hàng hóa (hơn 1.949 mã hàng thực tế).
   - Nhập hàng từ nhà cung cấp (Stock-In) kèm tính tổng chi phí và tự động cộng tồn kho.
   - Điều chuyển tồn kho giữa các chi nhánh an toàn qua 1 thao tác.
   - Thẻ theo dõi trực quan: Tổng sản phẩm, Tổng tồn kho, Cảnh báo sắp hết hàng, Giá trị tồn kho (VND).

3. **Dự báo Tồn kho AI (AI Demand Forecasting):**
   - Biểu đồ tương tác SVG: xu hướng doanh số thực tế và đường dự báo nhu cầu 7–14 ngày tới.
   - Thuật toán Exponential Smoothing ($\alpha = 0.35$) kết hợp Moving Average 7 ngày.
   - Bảng cảnh báo thông minh (Khẩn cấp / Cao / Bình thường) kèm số lượng đề xuất nhập hàng.

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
│   │   ├── common/          # Button, Input, Table, Badge, Modal, Tabs
│   │   └── forecast/        # Biểu đồ SalesTrendChart (SVG)
│   ├── pages/
│   │   ├── pos/             # Màn hình bán hàng POS và các Modal
│   │   ├── inventory/       # Màn hình kho, nhập kho, điều chuyển
│   │   ├── forecast/        # Màn hình dự báo nhu cầu & đề xuất nhập hàng
│   │   ├── report/          # Báo cáo doanh thu & lịch sử hóa đơn
│   │   ├── partner/         # Quản lý khách hàng thân thiết & NCC
│   │   └── admin/           # Phân quyền & danh sách nhân viên
│   ├── services/            # API services (auth, sale, inventory, forecast,...)
│   ├── hooks/               # useBarcodeScanner, useHotkeys
│   ├── utils/               # formatters, audio, receiptPrinter, exportCsv
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
- Tài khoản đăng nhập thử nghiệm:
  - **Quản lý (Admin):** `admin` / `123456`
  - **Thu ngân (Cashier):** `cashier` / `123456`
