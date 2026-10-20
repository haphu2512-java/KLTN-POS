# 📱 POS Mobile Barcode Scanner (Expo Go)

Ứng dụng quét mã vạch không dây và tra cứu tồn kho **Offline-First** cho hệ thống cửa hàng POS KLTN, xây dựng bằng **React Native + Expo**.

---

## 🌟 Tính năng nổi bật

1. **Bắn mã vạch trực tiếp vào POS (Remote Scanner Mode)**:
   - Dùng camera điện thoại quét mã vạch (EAN-13, EAN-8, Code 128, QR Code,...).
   - Truyền mã tức thì qua **Spring Boot REST & STOMP WebSocket** (`/topic/barcode-scans`).
   - Màn hình POS trên máy tính tự động nhận diện mã và thêm ngay vào giỏ hàng mà không cần dây nối.
2. **Tra cứu giá & tồn kho di động (Price & Stock Check Mode)**:
   - Nhân viên quét sản phẩm để xem tên, phân loại, đơn vị tính, giá bán và số lượng tồn kho theo chi nhánh.
3. **Offline-First Architecture**:
   - Tự động lưu cache danh mục sản phẩm vào bộ nhớ máy (`AsyncStorage`).
   - Khi mất sóng Wi-Fi hoặc kiểm kê trong kho sâu, vẫn tra cứu được giá & tồn kho bình thường.
   - Các mã quét khi offline được lưu an toàn trong **Hàng đợi Offline (Offline Scan Queue)**. Khi có mạng lại, nhấn **Đồng bộ ngay** để đẩy toàn bộ vào hệ thống POS.
4. **Mock Scanner & Nhập tay (Theo chuẩn SKILL.md)**:
   - Cho phép nhập mã vạch bằng bàn phím ảo.
   - Có sẵn các chip mã vạch mẫu (Mì Hảo Hảo, Coca-Cola, Snack Sriracha, Bánh gạo Poca,...) để test tức thì ngay trên máy tính mà không cần in mã vạch ra giấy.
5. **Trải nghiệm phần cứng chuyên nghiệp**:
   - Khung ngắm laser đỏ chuyển động, hỗ trợ bật/tắt đèn Flash (Torch), đổi camera trước/sau.
   - Rung phản hồi xúc giác (Haptics) và âm thanh Beep khi quét thành công.

---

## 🚀 Hướng dẫn khởi chạy với Expo Go

### Bước 1: Khởi động Backend Spring Boot & Frontend POS
- Backend chạy ở cổng: `http://localhost:8080` (hoặc IP Wi-Fi nội bộ của máy bạn, ví dụ: `http://192.168.1.15:8080`).
- Frontend POS chạy ở cổng: `http://localhost:5173`.

### Bước 2: Khởi động ứng dụng Expo
Mở terminal tại thư mục `MOBILE`:
```bash
cd MOBILE
npx expo start
```
hoặc:
```bash
npm start
```

### Bước 3: Mở ứng dụng trên điện thoại
1. Tải ứng dụng **Expo Go** trên App Store (iOS) hoặc Google Play (Android).
2. Đảm bảo điện thoại và máy tính kết nối **cùng một mạng Wi-Fi**.
3. Dùng ứng dụng Camera của điện thoại (hoặc tính năng Scan QR trong app Expo Go) quét mã QR hiển thị trên màn hình terminal.
4. Ở màn hình **Đăng nhập máy quét**, điền IP máy tính (`http://<IP_WIFI_CUA_BAN>:8080`) và **đăng nhập bằng đúng tài khoản đang dùng trên máy POS**. Mã quét chỉ được gửi tới máy POS của tài khoản đó.
5. Vào biểu tượng **⚙️ Cài đặt** trên app điện thoại:
   - Nhấn **⚡ Kiểm tra kết nối máy chủ** -> Báo `✓ Kết nối thành công`.
   - Nhấn **📥 Tải danh mục sản phẩm về máy (Offline)**.

### Chạy trên Android Emulator hoặc Web
- **Android Emulator**: Trong Settings chọn preset `Android Emu (10.0.2.2:8080)`.
- **Trình duyệt Web**: Chạy `npm run web` (hoặc nhấn phím `w` trong terminal Expo).

---

## 📡 API & WebSocket Specification

Mọi API trừ `ping` đều cần header `Authorization: Bearer <JWT>` (lấy từ `POST /api/auth/login`).

- **GET** `/api/scanner/ping`: Kiểm tra kết nối, không cần đăng nhập.
- **POST** `/api/scanner/broadcast`: Bắn một mã vào giỏ POS của cùng tài khoản, qua hàng đợi riêng `/user/queue/barcode-scans` (tin `type: "SCAN"`). Gửi trùng `clientScanId` sẽ bị bỏ qua.
- **GET** `/api/scanner/lookup?barcode=`: Tra giá và tồn kho tại chi nhánh của tài khoản; không gửi gì tới POS.
- **GET** `/api/scanner/products/cache`: Danh mục sản phẩm đang kinh doanh (không có giá vốn) để tra cứu Offline.
- **POST** `/api/scanner/sync-batch`: Body `{ "scans": [...] }`, tối đa 200 mã mỗi lần. Server gửi một tin `type: "BATCH"` theo thứ tự quét; POS hỏi thu ngân trước khi thêm vào giỏ.
