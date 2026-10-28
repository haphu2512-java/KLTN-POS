# Hướng dẫn dựng lại toàn bộ hệ thống từ máy trắng

Tài liệu này dành cho người **chưa biết gì** về dự án và **không còn gì trong tay** ngoài repo trên GitHub: không database, không file `.env`, không máy chủ. Làm lần lượt từ trên xuống là chạy được đủ 4 phần:

| Phần | Thư mục | Công nghệ | Bắt buộc? |
|------|---------|-----------|-----------|
| Database | (Docker hoặc cài tay) | MySQL 8.0 | Có |
| Backend | `BACKEND/` | Java 21, Spring Boot 3.3.3, Maven | Có |
| Ứng dụng quầy (desktop/web) | `FRONTEND/` | Node.js, React 19, Vite 8, Electron 44 | Có |
| App điện thoại quét mã | `MOBILE/` | Expo 58 / React Native | Tùy chọn |
| Dự báo bằng Python (kiểm chứng) | `AI_FORECAST/` | Python 3.10+ | Tùy chọn |

> Dự báo AI dùng trong ứng dụng thật chạy **bên trong backend Java** (job 02:00 mỗi đêm). Thư mục `AI_FORECAST/` chỉ để kiểm chứng công thức và xuất số liệu báo cáo, không cần cho hệ thống chạy.

Thứ tự khuyên dùng: **Bước 1 → 2 → 3 → 4 → 5 → 6**, sau đó mới làm điện thoại (Bước 8) và AI (Bước 9).

---

## Bước 1. Cài công cụ

| Công cụ | Phiên bản | Dùng cho | Kiểm tra sau khi cài |
|---------|-----------|----------|----------------------|
| Git | bất kỳ | tải mã nguồn | `git --version` |
| Docker Desktop (có Docker Compose v2) | mới | chạy MySQL (và backend nếu muốn) | `docker compose version` |
| JDK | **21** (Temurin là bản CI đang dùng) | chạy backend trực tiếp | `java -version` |
| Maven | 3.9+ | build/test backend | `mvn -v` |
| Node.js | **22** (CI dùng 22; README ghi tối thiểu 18) | frontend, mobile | `node -v`, `npm -v` |
| Python | 3.12 (CI dùng 3.12; tối thiểu 3.10) | chỉ khi dùng `AI_FORECAST/` | `python --version` |
| OpenSSL | bất kỳ | tạo khóa JWT ngẫu nhiên | `openssl version` |
| Expo Go (trên điện thoại) | bản mới nhất trên App Store / Google Play | chỉ khi dùng app quét mã | |

Ghi chú:
- Repo **không có** Maven Wrapper (`mvnw`), nên phải cài Maven riêng.
- Nếu chỉ chạy bằng Docker thì không bắt buộc JDK + Maven (Dockerfile tự build), nhưng vẫn nên cài để chạy test và để ghép điện thoại dễ hơn (xem Bước 8).
- Không có OpenSSL (Windows): dùng một chuỗi ngẫu nhiên bất kỳ **dài từ 32 ký tự trở lên** cho `JWT_SECRET`.

---

## Bước 2. Tải mã nguồn

```bash
git clone https://github.com/haphu2512-java/KLTN-POS.git
cd KLTN-POS
git checkout dev      # nhánh phát triển; main là nhánh ổn định
```

Mọi lệnh bên dưới đều tính từ **thư mục gốc repo** (`KLTN-POS/`) trừ khi ghi khác.

---

## Bước 3. Tạo các file `.env`

Không có giá trị bí mật nào viết sẵn trong code. Có 4 file mẫu, chép ra rồi điền:

```bash
cp .env.example .env                              # backend + docker compose (BẮT BUỘC)
cp FRONTEND/.env.example FRONTEND/.env            # ứng dụng quầy
cp MOBILE/.env.example MOBILE/.env                # app điện thoại (tùy chọn)
cp AI_FORECAST/.env.example AI_FORECAST/.env      # AI Python (tùy chọn)
```

Các file `.env` đã nằm trong `.gitignore`, **không bao giờ commit** chúng. Viết dạng `KEY=value`, không đặt dấu ngoặc kép. Biến môi trường của hệ điều hành luôn được ưu tiên hơn giá trị trong `.env`.

### 3.1. `.env` ở thư mục gốc

Backend đọc file này dù chạy từ `BACKEND/` hay từ thư mục gốc (`spring.config.import` trong `application.properties`), Docker Compose cũng đọc file này.

**Biến bắt buộc** (thiếu thì backend hoặc Docker Compose dừng ngay):

| Biến | Ý nghĩa | Gợi ý điền |
|------|---------|------------|
| `DB_PASSWORD` | Mật khẩu tài khoản MySQL mà backend dùng | Tự đặt một mật khẩu mạnh |
| `JWT_SECRET` | Khóa ký token đăng nhập, **tối thiểu 32 ký tự** (backend kiểm tra và từ chối khởi động nếu ngắn hơn) | `openssl rand -hex 32` |
| `SEED_DEFAULT_PASSWORD` | Mật khẩu cho 2 tài khoản mẫu `admin` và `cashier`. Bắt buộc ở **lần chạy đầu** (database còn trống) | Tự đặt; đổi lại trong ứng dụng sau khi đăng nhập |
| `DB_ROOT_PASSWORD` | Mật khẩu `root` của container MySQL. **Chỉ bắt buộc khi dùng Docker** | Tự đặt, khác `DB_PASSWORD` |

**Biến có sẵn giá trị mặc định** (giữ nguyên nếu không có lý do đổi):

| Biến | Mặc định | Ý nghĩa |
|------|----------|---------|
| `DB_HOST` | `localhost` | Máy chạy MySQL (khi chạy backend trực tiếp) |
| `DB_PORT` | `3306` | Cổng MySQL (Docker cũng mở cổng này ra máy thật) |
| `DB_NAME` | `pos_inventory` | Tên database. Backend tự tạo nếu chưa có (`createDatabaseIfNotExist=true`) |
| `DB_USERNAME` | `root` | Tài khoản MySQL backend dùng **khi chạy trực tiếp** bằng `mvn spring-boot:run` |
| `DB_USER` | `posuser` | Tài khoản ứng dụng Docker tạo trong container MySQL; backend chạy trong Docker dùng tài khoản này |
| `SERVER_PORT` | `8080` | Cổng backend |
| `JWT_EXPIRATION` | `86400000` | Thời hạn token (mili giây, = 24 giờ) |
| `BARCODE_SYNC_ON_STARTUP` | `true` | Khởi động thì đổi mã vạch cũ không quét được sang mã nội bộ (xem Bước 7) |
| `CORS_ALLOWED_ORIGINS` | localhost, 127.0.0.1, các dải LAN, `file://*` | Nguồn được gọi API/WebSocket |
| `JPA_DDL_AUTO` | `update` | Hibernate tự tạo/cập nhật bảng từ entity. **Đừng đổi** khi dựng mới |
| `JPA_SHOW_SQL`, `JPA_FORMAT_SQL` | `false` | In câu SQL ra log |
| `AI_FORECAST_ALGORITHM` | `EXP_SMOOTH` | `EXP_SMOOTH` hoặc `MOVING_AVG` |
| `AI_DEFAULT_FORECAST_DAYS` | `7` | Số ngày dự báo |
| `AI_LEAD_TIME_DAYS` | `2` | Thời gian giao hàng của nhà cung cấp |
| `AI_SERVICE_LEVEL_Z` | `1.65` | Hệ số Z (mức phục vụ 95%) |
| `AI_HISTORY_DAYS` | `30` | Số ngày lịch sử dùng để dự báo |
| `POS_REQUIRE_OPEN_SHIFT` | `true` | Thu ngân phải mở ca mới bán được |
| `SCANNER_REQUIRE_SAME_NETWORK` | `true` | Điện thoại phải cùng mạng với máy POS mới bắn mã được (xem Bước 8) |
| `SCANNER_LAN_IPV4_PREFIX` | `24` | Độ rộng dải LAN (24 = `192.168.1.x`; mạng `10.0.x.x` thì đặt `16`) |
| `SCANNER_TRUST_FORWARDED_FOR` | `false` | Chỉ đặt `true` khi backend đứng sau reverse proxy tin cậy |
| `ENABLE_AI_ASSISTANT`, `GEMINI_API_KEY`, `AI_LLM_MODEL` | | **Chưa dùng trong code**, để trống |

Ví dụ một file `.env` tối thiểu (giá trị chỉ là chỗ trống, hãy tự điền):

```dotenv
DB_PASSWORD=<mật-khẩu-db>
DB_ROOT_PASSWORD=<mật-khẩu-root-mysql>     # chỉ khi dùng Docker
JWT_SECRET=<kết-quả-của-openssl-rand-hex-32>
SEED_DEFAULT_PASSWORD=<mật-khẩu-cho-admin-và-cashier>
```

> **Bẫy hay gặp:** Docker tạo user `posuser` với mật khẩu `DB_PASSWORD`, còn `root` dùng `DB_ROOT_PASSWORD`. Nếu MySQL chạy bằng Docker nhưng backend chạy trực tiếp bằng `mvn spring-boot:run`, hãy đặt `DB_USERNAME=posuser` (vì `DB_USERNAME` mặc định là `root`, ghép với `DB_PASSWORD` sẽ sai mật khẩu).

### 3.2. `FRONTEND/.env`

| Biến | Mặc định | Khi nào cần |
|------|----------|-------------|
| `DEV_SERVER_PORT` | `5173` | Cổng Vite khi chạy dev |
| `DEV_PROXY_TARGET` | `http://localhost:8080` | Địa chỉ backend mà Vite chuyển tiếp `/api` và `/ws` tới. Đổi nếu backend ở máy khác |
| `VITE_API_BASE_URL` | trống | Để trống khi chạy dev. **Bắt buộc khi đóng gói Electron**: `http://<IP_MÁY_CHỦ>:8080/api` |
| `VITE_WS_URL` | trống | Để trống khi chạy dev. **Bắt buộc khi đóng gói Electron**: `ws://<IP_MÁY_CHỦ>:8080/ws` |
| `VITE_APP_TITLE` | `POS & Quản Lý Kho Bán Lẻ` | Tiêu đề ứng dụng |
| `VITE_DEMO_PASSWORD` | trống | Mật khẩu điền sẵn ở màn hình đăng nhập, chỉ để demo. Để trống ở môi trường thật |

Mọi biến `VITE_*` bị nhúng vào file JS, ai mở ứng dụng cũng đọc được: **không đặt bí mật ở đây**.

### 3.3. `MOBILE/.env`

| Biến | Ý nghĩa |
|------|---------|
| `EXPO_PUBLIC_SERVER_URL` | Địa chỉ backend mặc định cho app. Có thể để trống: app tự lấy IP máy tính đang chạy Expo, và người dùng vẫn sửa được trong màn hình Cài đặt của app |

Ví dụ: điện thoại thật `http://<IP_WIFI_MÁY_TÍNH>:8080`, máy ảo Android `http://10.0.2.2:8080`, trình duyệt/iOS Simulator `http://localhost:8080`.

### 3.4. `AI_FORECAST/.env`

Xem Bước 9. Mặc định đọc dữ liệu CSV mẫu, không cần điền gì.

---

## Bước 4. Dựng database và backend

Chọn **một** trong hai cách.

### Cách A: Docker (dễ nhất)

```bash
docker compose up -d          # tạo container pos-mysql và pos-backend
docker compose ps             # cả hai phải ở trạng thái running / healthy
docker compose logs -f backend   # xem log, Ctrl+C để thoát
```

- MySQL 8.0 chạy ở container `pos-mysql`, dữ liệu nằm trong volume `mysql_data` (xóa container không mất dữ liệu; `docker compose down -v` thì **mất hết**).
- Backend được build từ `BACKEND/Dockerfile` (lần đầu mất vài phút vì tải thư viện Maven).
- Lưu ý: `docker-compose.yml` **không** chuyển biến `POS_REQUIRE_OPEN_SHIFT` vào container backend, nên khi chạy bằng Docker giá trị luôn là mặc định `true`.

### Cách B: Chạy trực tiếp (MySQL cài tay hoặc chỉ MySQL bằng Docker)

1. Có một MySQL 8.0 đang chạy. Muốn dùng Docker chỉ cho MySQL:
   ```bash
   docker compose up -d mysql
   ```
   rồi đặt `DB_USERNAME=posuser` trong `.env` (xem bẫy ở mục 3.1). Nếu MySQL cài tay, dùng `DB_USERNAME=root` và `DB_PASSWORD` là mật khẩu root của bạn (hoặc một user có quyền tạo database).
2. Chạy backend:
   ```bash
   cd BACKEND
   mvn spring-boot:run
   ```

Không cần chạy file SQL nào để tạo bảng: Hibernate tự tạo bảng từ entity (`JPA_DDL_AUTO=update`). File `BACKEND/src/main/resources/schema.sql` chỉ là tài liệu tham khảo cấu trúc bảng.

### Lần chạy đầu tiên backend tự làm gì

Khi database còn trống (chưa có chi nhánh nào), lớp `DatabaseInitializer` tạo sẵn:

- 5 ca làm mẫu: 06:00–09:00, 09:00–12:00, 12:00–15:00, 15:00–18:00, 18:00–22:00.
- 2 chi nhánh: **id 1 = Chi nhánh Quận 1 (Trụ sở)**, **id 2 = Chi nhánh Quận 3**.
- 2 tài khoản: `admin` (ADMIN) và `cashier` (CASHIER, được phân công Chi nhánh Quận 1), mật khẩu đều là `SEED_DEFAULT_PASSWORD`.
- 5 nhà cung cấp và 8 sản phẩm mẫu kèm tồn kho ở cả 2 chi nhánh.

Nếu database trống mà chưa đặt `SEED_DEFAULT_PASSWORD`, backend dừng với lỗi *"Database trống nhưng chưa đặt SEED_DEFAULT_PASSWORD..."*.

### Kiểm tra backend đã chạy

- Mở <http://localhost:8080/swagger-ui/index.html>: thấy trang Swagger là được.
- Hoặc `curl http://localhost:8080/api/scanner/ping` (endpoint không cần đăng nhập).

---

## Bước 5. Nạp dữ liệu 1.949 sản phẩm Bách Hóa Xanh (tùy chọn)

Database mới chỉ có 8 sản phẩm mẫu. File `import_bhx_data.sql` ở thư mục gốc chứa 1.949 sản phẩm thật và tồn kho cho 2 chi nhánh.

> ⚠️ **Script này xóa sạch bảng `product` và `inventory`** (`TRUNCATE` khi đã tắt kiểm tra khóa ngoại) rồi nạp lại với id từ 1 đến 1949. Chỉ chạy trên **database mới dựng**, trước khi có hóa đơn hay phiếu nhập. Chạy trên database đã có giao dịch thì các hóa đơn cũ sẽ trỏ nhầm sang sản phẩm khác.

Điều kiện: backend **đã chạy ít nhất một lần** (để Hibernate tạo bảng và tạo 2 chi nhánh id 1, id 2 mà tồn kho trong script tham chiếu tới).

Với Docker (gõ thẳng mật khẩu root thay cho chỗ trống, hoặc `export DB_ROOT_PASSWORD=...` trước):

```bash
docker exec -i pos-mysql mysql -uroot -p"<DB_ROOT_PASSWORD>" pos_inventory < import_bhx_data.sql
```

Với MySQL cài tay:

```bash
mysql -uroot -p pos_inventory < import_bhx_data.sql
```

Sau khi nạp, **khởi động lại backend** để bước đồng bộ mã vạch chạy lại. Mã vạch trong file đã ở dạng mã nội bộ `20...` nên thường không đổi gì.

Các file đi kèm: `bhx_products_scraped.json` là dữ liệu thô, `scrape_bhx.py` là script đã dùng để lấy dữ liệu. Không cần chạy lại chúng.

---

## Bước 6. Chạy ứng dụng quầy (FRONTEND)

```bash
cd FRONTEND
npm install
npm run dev            # bản web: http://localhost:5173
# hoặc
npm run dev:electron   # mở cửa sổ desktop Electron (chạy kèm Vite)
```

- Khi chạy dev, Vite chuyển tiếp `/api` và `/ws` sang `DEV_PROXY_TARGET` nên không bị lỗi CORS.
- Vite lắng nghe trên mọi địa chỉ mạng, nên máy khác cùng LAN mở được `http://<IP_MÁY_NÀY>:5173`.
- Đăng nhập bằng `admin` hoặc `cashier` với mật khẩu `SEED_DEFAULT_PASSWORD`. Ở bước đăng nhập chọn chi nhánh (tài khoản chỉ được phân công một chi nhánh thì vào thẳng).

### Đóng gói bản cài đặt Windows

```bash
cd FRONTEND
# Điền VITE_API_BASE_URL và VITE_WS_URL trong FRONTEND/.env trước (bắt buộc)
npm run electron:build
```

File cài đặt `.exe` (NSIS) nằm trong `FRONTEND/release/`. Bản đóng gói chạy qua `file://`, không có proxy, nên thiếu hai biến trên là không gọi được backend.

### In hóa đơn và máy quét USB

- In hóa đơn K80 không hỏi hộp thoại chỉ hoạt động trong **Electron** (`electron/main.cjs`); bản web dùng hộp thoại in của trình duyệt.
- Máy quét mã vạch USB/Bluetooth không cần cấu hình: nó gõ mã như bàn phím, POS tự nhận khi các phím đến dồn dập dưới 50 ms.

---

## Bước 7. Mã vạch nội bộ

Quy tắc nằm ở `BACKEND/src/main/java/com/pos/product/BarcodeRules.java`:

- Mã EAN-8 / UPC-A / EAN-13 / ITF-14 thật in trên bao bì (đúng số kiểm tra) được **giữ nguyên**.
- Sản phẩm không có mã thật dùng **mã EAN-13 nội bộ**: `20` + ID sản phẩm đủ 10 chữ số + 1 số kiểm tra. Ví dụ sản phẩm id 1 → `2000000000015`.
- Tạo sản phẩm mà bỏ trống mã vạch thì hệ thống tự sinh theo quy tắc trên. Có thể in tem mã vạch trong trang Kho.
- Mỗi lần khởi động (khi `BARCODE_SYNC_ON_STARTUP=true`), backend đổi các mã cũ không quét được (mã nội bộ của BHX, mã giả `8931000000xxx`, mã sai số kiểm tra) sang mã nội bộ. Chỉ đổi cột mã vạch, không đụng tới hóa đơn hay tồn kho. ADMIN có thể chạy tay qua `POST /api/products/barcodes/sync`.
- Sau khi mã thay đổi, mở app điện thoại lúc có mạng để tải lại danh mục offline.

---

## Bước 8. App điện thoại quét mã và ghép với máy POS (tùy chọn)

### 8.1. Chạy app

```bash
cd MOBILE
npm install
npx expo start         # hoặc: npm start
```

1. Cài **Expo Go** trên điện thoại.
2. Điện thoại và máy tính **cùng một mạng Wi-Fi**.
3. Quét mã QR hiện trong terminal bằng Camera (iOS) hoặc trong Expo Go (Android).
4. Ở màn hình đăng nhập của app, kiểm tra địa chỉ máy chủ là `http://<IP_WIFI_MÁY_CHẠY_BACKEND>:8080` (app thường tự điền IP máy đang chạy Expo), rồi đăng nhập bằng tài khoản **cùng chi nhánh** với quầy.
5. Vào **Cài đặt** → *Kiểm tra kết nối máy chủ* → *Tải danh mục sản phẩm về máy (Offline)*.

Chạy trên máy ảo Android: chọn preset `Android Emu (10.0.2.2:8080)` trong Cài đặt. Chạy trên trình duyệt: `npm run web` hoặc nhấn `w` trong terminal Expo.

Cách tìm IP Wi-Fi của máy tính: Windows `ipconfig`, macOS `ipconfig getifaddr en0`, Linux `hostname -I`.

### 8.2. Ghép điện thoại với quầy

1. Trên máy POS (đã đăng nhập, đang ở màn hình bán hàng) bấm **Ghép điện thoại**: hiện mã QR (và mã 6 số).
2. Trên app điện thoại quét QR đó (hoặc nhập mã 6 số trong Cài đặt). Mã chỉ dùng một lần và hết hạn sau **5 phút**.
3. Từ giờ mã quét trên điện thoại đi thẳng vào giỏ của đúng quầy đó. Quét lúc mất mạng thì mã nằm trong hàng đợi offline, có mạng lại bấm *Đồng bộ ngay*.

Các vai trò dùng được tính năng bắn mã vào quầy: ADMIN, MANAGER, CASHIER. Tra giá/tồn kho thì cả WAREHOUSE cũng dùng được.

### 8.3. Điều kiện "cùng mạng"

Khi `SCANNER_REQUIRE_SAME_NETWORK=true`, server so IP của điện thoại với IP của máy POS: trùng nhau (cùng router ra Internet) hoặc cùng dải mạng nội bộ (`/24` mặc định) thì mới nhận mã.

> **Bẫy với Docker:** nếu backend chạy trong Docker và máy POS là chính máy đặt container, server thấy máy POS ở IP cổng Docker (`172.x`), khác dải Wi-Fi của điện thoại nên **bị chặn**. Cách xử lý: chạy backend bằng `mvn spring-boot:run` (Cách B ở Bước 4), hoặc dùng `network_mode: host`. Chỉ đặt `SCANNER_REQUIRE_SAME_NETWORK=false` khi thử nghiệm.

Ngoài ra, nếu tường lửa của máy chạy backend chặn cổng 8080 từ mạng ngoài, điện thoại sẽ không kết nối được: mở cổng 8080 cho mạng riêng (Private network).

---

## Bước 9. AI dự báo bản Python (tùy chọn)

```bash
cd AI_FORECAST
python -m venv .venv
source .venv/bin/activate          # Windows: .venv\Scripts\activate
pip install -r requirements-dev.txt

python -m pytest                                   # chạy test
python -m pos_forecast.cli --branch 1              # dự báo bằng dữ liệu CSV mẫu
python -m pos_forecast.cli --all-branches --only-restock --out forecast.json
uvicorn pos_forecast.api:app --port 8000           # REST API, tài liệu ở http://localhost:8000/docs
```

Biến môi trường (`AI_FORECAST/.env.example`):

| Biến | Mặc định | Ý nghĩa |
|------|----------|---------|
| `FORECAST_DATA_SOURCE` | `csv` | `csv` (đọc `sample_data/`) hoặc `mysql` (đọc thẳng database của hệ thống) |
| `FORECAST_DATA_DIR` | `./sample_data` | Thư mục CSV |
| `DB_HOST`, `DB_PORT`, `DB_NAME` | `localhost`, `3306`, `pos_inventory` | Khi đọc MySQL |
| `DB_USERNAME` (hoặc `DB_USER`), `DB_PASSWORD` | | Tài khoản MySQL khi đọc MySQL |
| `AI_FORECAST_ALGORITHM`, `AI_FORECAST_ALPHA`, `AI_FORECAST_MA_WINDOW`, `AI_SERVICE_LEVEL_Z`, `AI_LEAD_TIME_DAYS`, `AI_REVIEW_PERIOD_DAYS`, `AI_HISTORY_DAYS`, `AI_VALIDATION_DAYS`, `AI_INTERMITTENT_ZERO_RATIO` | xem file mẫu | Tham số mô hình |

Python **không tự đọc** file `.env`. Muốn dùng thì nạp vào shell trước: `export $(cat .env | xargs)` (Linux/macOS).

Công thức và lý do thiết kế: xem [`AI_for_POS.md`](AI_for_POS.md) và [`AI_FORECAST/README.md`](AI_FORECAST/README.md).

---

## Bước 10. Vai trò và phân quyền

Có 4 vai trò. Quyền dưới đây lấy từ các `@PreAuthorize` trong backend:

| Chức năng | ADMIN | MANAGER | CASHIER | WAREHOUSE |
|-----------|:-----:|:-------:|:-------:|:---------:|
| Bán hàng, tạo hóa đơn | ✓ | ✓ | ✓ | |
| Hoàn trả, xem lịch sử hóa đơn | ✓ | ✓ | | |
| Khách hàng | ✓ | ✓ | ✓ | |
| Sản phẩm, tồn kho, nhập kho, nhà cung cấp | ✓ | ✓ | | ✓ |
| Đồng bộ mã vạch (`/api/products/barcodes/sync`) | ✓ | | | |
| Báo cáo doanh thu | ✓ | ✓ | | |
| Dự báo AI | ✓ | ✓ | | ✓ |
| Mở/đóng ca, thu chi két | ✓ | ✓ | ✓ | |
| Duyệt ca lệch tiền, đóng hộ ca, xếp ca | ✓ | ✓ | | |
| Sửa lịch ca (thêm/sửa/xóa ca) | ✓ | | | |
| Quản lý tài khoản (tạo, phân quyền, khóa, đổi mật khẩu) | ✓ | | | |
| Quản lý chi nhánh | ✓ | | | |
| Ghép điện thoại với quầy | ✓ | ✓ | ✓ | |

Chi nhánh:
- ADMIN chọn được mọi chi nhánh khi đăng nhập. Người khác chỉ chọn được chi nhánh mà ADMIN đã phân công (trang **Tài khoản**).
- Chi nhánh nằm trong token đăng nhập; muốn đổi chi nhánh thì đăng xuất rồi đăng nhập lại.

Việc nên làm ngay sau khi dựng xong: đăng nhập `admin`, **đổi mật khẩu** của `admin` và `cashier`, tạo tài khoản thật cho từng người và phân công chi nhánh.

---

## Bước 11. Chạy test

Giống hệt những gì CI (`.github/workflows/ci.yml`) chạy trên mỗi PR:

```bash
# Backend: dùng H2 trong bộ nhớ, KHÔNG cần MySQL hay file .env
cd BACKEND && mvn -B test

# Frontend: kiểm tra lint và build
cd FRONTEND && npm ci && npm run lint && npm run build

# Mobile: kiểm tra đóng gói được
cd MOBILE && npm ci && npx expo export --platform android --output-dir dist

# AI Python
cd AI_FORECAST && pip install -r requirements-dev.txt && python -m pytest -q
```

Khi đổi công thức dự báo ở Java hoặc Python, phải đổi bên còn lại rồi chạy `python -m pos_forecast.parity` (trong `AI_FORECAST/`) để sinh lại `tests/fixtures/parity_cases.json`; JUnit `ForecastParityTest` kiểm tra hai bên cho cùng kết quả.

---

## Bước 12. Lỗi thường gặp

| Hiện tượng | Nguyên nhân | Cách xử lý |
|------------|-------------|------------|
| `docker compose up` báo *Thiếu DB_ROOT_PASSWORD / DB_PASSWORD / JWT_SECRET trong .env* | Chưa tạo hoặc chưa điền `.env` ở thư mục gốc | Làm lại Bước 3.1 |
| Backend dừng: *Database trống nhưng chưa đặt SEED_DEFAULT_PASSWORD* | Lần chạy đầu thiếu biến này | Điền `SEED_DEFAULT_PASSWORD` rồi chạy lại |
| Backend dừng vì lỗi khóa JWT | `JWT_SECRET` trống hoặc ngắn hơn 32 ký tự | `openssl rand -hex 32` |
| `Access denied for user 'root'...` khi chạy `mvn spring-boot:run` với MySQL trong Docker | `DB_USERNAME` mặc định là `root` nhưng `DB_PASSWORD` là mật khẩu của `posuser` | Đặt `DB_USERNAME=posuser` |
| `Communications link failure` | MySQL chưa chạy hoặc sai `DB_HOST`/`DB_PORT` | `docker compose ps`; chờ `pos-mysql` healthy |
| Cổng 3306 hoặc 8080 đã bị chiếm | Đã có MySQL/ứng dụng khác chạy | Tắt chương trình đó, hoặc đổi `DB_PORT` / `SERVER_PORT` trong `.env` (nhớ đổi `DEV_PROXY_TARGET` theo) |
| Quên mật khẩu `admin` sau khi đã có dữ liệu | Tài khoản mẫu chỉ được tạo khi database trống | Đổi `SEED_DEFAULT_PASSWORD` **không** có tác dụng. Dựng lại database mới (`docker compose down -v` sẽ **xóa toàn bộ dữ liệu**), hoặc nhờ một ADMIN khác đổi mật khẩu |
| Nạp `import_bhx_data.sql` báo lỗi bảng không tồn tại | Backend chưa chạy lần nào nên chưa có bảng | Chạy backend một lần rồi nạp lại |
| Frontend đăng nhập báo lỗi mạng | Backend chưa chạy, hoặc `DEV_PROXY_TARGET` sai | Kiểm tra <http://localhost:8080/swagger-ui/index.html> |
| Bản Electron đã đóng gói không gọi được API | Thiếu `VITE_API_BASE_URL` / `VITE_WS_URL` lúc build | Điền vào `FRONTEND/.env` rồi build lại |
| Không bán được, hiện hộp nhắc mở ca | Thu ngân chưa mở ca (`POS_REQUIRE_OPEN_SHIFT=true`) | Mở ca ở tab Ca làm với tiền đầu ca |
| Thu ngân không mở được ca | Chỉ mở được ca trong khung giờ của ca (sớm tối đa 15 phút); quản lý mở ngoài giờ phải ghi lý do | Mở đúng giờ, hoặc nhờ quản lý mở |
| Không đăng nhập được vào chi nhánh B | Còn ca chưa đóng ở chi nhánh A | Vào A đóng ca, hoặc nhờ quản lý đóng hộ ở tab Ca làm |
| Điện thoại không kết nối được máy chủ | Sai IP, khác Wi-Fi, hoặc tường lửa chặn cổng 8080 | Kiểm tra IP ở Cài đặt của app, cùng Wi-Fi, mở cổng 8080 |
| Điện thoại quét được nhưng mã không vào giỏ | Chưa ghép quầy, mã ghép quá 5 phút, khác chi nhánh, hoặc bị chặn do không cùng mạng (đặc biệt khi backend chạy Docker) | Ghép lại; xem mục 8.3 |
| App điện thoại tra không ra sản phẩm khi offline | Danh mục offline cũ, mã vạch đã được đồng bộ sang mã nội bộ | Có mạng thì tải lại danh mục trong Cài đặt |

---

## Phụ lục: địa chỉ và cổng

| Thành phần | Địa chỉ |
|------------|---------|
| Backend REST | `http://localhost:8080/api` |
| Swagger UI | `http://localhost:8080/swagger-ui/index.html` |
| WebSocket (SockJS/STOMP) | `http://localhost:8080/ws` |
| Frontend dev | `http://localhost:5173` |
| MySQL | `localhost:3306`, database `pos_inventory` |
| AI Python API (nếu chạy) | `http://localhost:8000/docs` |

Tài liệu khác trong repo: [`README.md`](README.md) (tổng quan, danh sách API), [`FRONTEND/README.md`](FRONTEND/README.md), [`MOBILE/README.md`](MOBILE/README.md), [`AI_FORECAST/README.md`](AI_FORECAST/README.md), [`AI_for_POS.md`](AI_for_POS.md).
