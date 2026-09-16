# Hướng Dẫn Step-by-Step: Xây Dựng Hệ Thống POS + Quản Lý Kho (Spring Boot + Electron)

> Tài liệu này hướng dẫn triển khai kỹ thuật chi tiết, đi kèm với `TodoList.md`. Làm theo thứ tự các Phần bên dưới.

---

## PHẦN 1 — Chuẩn bị môi trường

### Bước 1.1: Cài đặt công cụ
```bash
# Backend
- JDK 17+ (Spring Boot 3.x yêu cầu)
- Maven hoặc Gradle
- MySQL 8.x (hoặc SQL Server)
- IntelliJ IDEA / VS Code + Extension Pack for Java

# Frontend
- Node.js 18+ và npm
- VS Code + ESLint/Prettier

# Công cụ hỗ trợ
- Postman (test API)
- DBeaver / MySQL Workbench (quản lý DB)
- Git
```

### Bước 1.2: Tạo cấu trúc thư mục dự án tổng
```
pos-inventory-system/
├── backend/          # Spring Boot project
├── frontend-pos/      # Electron app (bán hàng)
├── docs/              # Tài liệu, ERD, SRS
└── README.md
```

---

## PHẦN 2 — Thiết kế Database

### Bước 2.1: Các bảng chính (MySQL)
```sql
CREATE DATABASE pos_inventory;
USE pos_inventory;

CREATE TABLE branch (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  address VARCHAR(255)
);

CREATE TABLE employee (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  branch_id BIGINT,
  username VARCHAR(50) UNIQUE NOT NULL,
  password VARCHAR(255) NOT NULL,
  full_name VARCHAR(100),
  role VARCHAR(20) DEFAULT 'STAFF',
  FOREIGN KEY (branch_id) REFERENCES branch(id)
);

CREATE TABLE product (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  barcode VARCHAR(50) UNIQUE NOT NULL,
  name VARCHAR(150) NOT NULL,
  category VARCHAR(100),
  cost_price DECIMAL(12,2),
  sale_price DECIMAL(12,2),
  unit VARCHAR(20) DEFAULT 'cái'
);

CREATE TABLE inventory (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  product_id BIGINT,
  branch_id BIGINT,
  quantity INT DEFAULT 0,
  min_threshold INT DEFAULT 5,
  FOREIGN KEY (product_id) REFERENCES product(id),
  FOREIGN KEY (branch_id) REFERENCES branch(id),
  UNIQUE KEY uk_product_branch (product_id, branch_id)
);

CREATE TABLE supplier (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(150),
  phone VARCHAR(20),
  address VARCHAR(255)
);

CREATE TABLE stock_in (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  branch_id BIGINT,
  supplier_id BIGINT,
  employee_id BIGINT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (branch_id) REFERENCES branch(id),
  FOREIGN KEY (supplier_id) REFERENCES supplier(id),
  FOREIGN KEY (employee_id) REFERENCES employee(id)
);

CREATE TABLE stock_in_detail (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  stock_in_id BIGINT,
  product_id BIGINT,
  quantity INT,
  unit_cost DECIMAL(12,2),
  FOREIGN KEY (stock_in_id) REFERENCES stock_in(id),
  FOREIGN KEY (product_id) REFERENCES product(id)
);

CREATE TABLE invoice (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  branch_id BIGINT,
  employee_id BIGINT,
  total_amount DECIMAL(12,2),
  discount DECIMAL(12,2) DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (branch_id) REFERENCES branch(id),
  FOREIGN KEY (employee_id) REFERENCES employee(id)
);

CREATE TABLE invoice_detail (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  invoice_id BIGINT,
  product_id BIGINT,
  quantity INT,
  unit_price DECIMAL(12,2),
  FOREIGN KEY (invoice_id) REFERENCES invoice(id),
  FOREIGN KEY (product_id) REFERENCES product(id)
);
```

### Bước 2.2: Vẽ ERD
- [ ] Dùng dbdiagram.io hoặc MySQL Workbench để trực quan hóa các bảng trên và lưu vào `docs/erd.png`

---

## PHẦN 3 — Xây dựng Backend (Spring Boot)

### Bước 3.1: Khởi tạo project
Vào [start.spring.io](https://start.spring.io) chọn:
- Project: Maven
- Dependencies: Spring Web, Spring Data JPA, MySQL Driver, Spring Security, Validation, Lombok, Spring WebSocket

Tải về, giải nén vào `backend/`.

### Bước 3.2: Cấu trúc thư mục
```
backend/src/main/java/com/pos/
├── config/            # SecurityConfig, WebSocketConfig, CORS
├── controller/         # REST endpoints
├── dto/                # Request/Response objects
├── entity/             # JPA Entities
├── repository/         # Spring Data JPA interfaces
├── service/             # Business logic
├── security/            # JWT filter, provider
└── forecast/             # Module dự báo tồn kho
```

### Bước 3.3: Cấu hình `application.properties`
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/pos_inventory
spring.datasource.username=root
spring.datasource.password=yourpassword
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
server.port=8080
jwt.secret=your-secret-key-here
jwt.expiration=86400000
```

### Bước 3.4: Tạo Entity (ví dụ `Product.java`)
```java
@Entity
@Table(name = "product")
@Data
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String barcode;
    private String name;
    private String category;
    private BigDecimal costPrice;
    private BigDecimal salePrice;
    private String unit;
}
```
- [ ] Lặp lại tương tự cho: `Branch`, `Employee`, `Inventory`, `Supplier`, `StockIn`, `StockInDetail`, `Invoice`, `InvoiceDetail`

### Bước 3.5: Tạo Repository
```java
public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findByBarcode(String barcode);
}
```

### Bước 3.6: Tạo Service (ví dụ logic bán hàng — trừ kho tự động)
```java
@Service
@RequiredArgsConstructor
public class InvoiceService {
    private final InvoiceRepository invoiceRepo;
    private final InventoryRepository inventoryRepo;

    @Transactional
    public Invoice createInvoice(InvoiceRequest req) {
        Invoice invoice = new Invoice();
        invoice.setBranchId(req.getBranchId());
        invoice.setEmployeeId(req.getEmployeeId());
        BigDecimal total = BigDecimal.ZERO;

        for (var item : req.getItems()) {
            Inventory inv = inventoryRepo.findByProductIdAndBranchId(item.getProductId(), req.getBranchId())
                .orElseThrow(() -> new RuntimeException("Sản phẩm không có trong kho"));
            if (inv.getQuantity() < item.getQuantity())
                throw new RuntimeException("Không đủ tồn kho: " + item.getProductId());
            inv.setQuantity(inv.getQuantity() - item.getQuantity());
            inventoryRepo.save(inv);
            total = total.add(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }
        invoice.setTotalAmount(total);
        return invoiceRepo.save(invoice);
    }
}
```

### Bước 3.7: Tạo Controller
```java
@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class InvoiceController {
    private final InvoiceService invoiceService;

    @PostMapping
    public ResponseEntity<Invoice> create(@RequestBody InvoiceRequest req) {
        return ResponseEntity.ok(invoiceService.createInvoice(req));
    }
}
```
- [ ] Lặp lại cho các controller: `ProductController`, `InventoryController`, `StockInController`, `ReportController`, `AuthController`

### Bước 3.8: Cấu hình bảo mật JWT
- [ ] Tạo `JwtUtil` (sinh & xác thực token)
- [ ] Tạo `JwtAuthFilter` (OncePerRequestFilter) kiểm tra token mỗi request
- [ ] Cấu hình `SecurityConfig`: public endpoint `/api/auth/**`, các endpoint còn lại yêu cầu token
- [ ] Endpoint đăng nhập trả về JWT khi username/password đúng

### Bước 3.9: Chạy thử backend
```bash
cd backend
./mvnw spring-boot:run
```
Kiểm tra bằng Postman: `POST http://localhost:8080/api/auth/login`

---

## PHẦN 4 — Xây dựng Frontend (Electron)

### Bước 4.1: Khởi tạo project
```bash
cd frontend-pos
npm create vite@latest . -- --template react
npm install
npm install electron electron-builder --save-dev
npm install axios react-router-dom
```

### Bước 4.2: Thêm file `electron/main.js`
```javascript
const { app, BrowserWindow } = require('electron');
const path = require('path');

function createWindow() {
  const win = new BrowserWindow({
    width: 1280,
    height: 800,
    webPreferences: { preload: path.join(__dirname, 'preload.js') }
  });
  win.loadURL('http://localhost:5173'); // dev; build sẽ load file tĩnh
}

app.whenReady().then(createWindow);
app.on('window-all-closed', () => { if (process.platform !== 'darwin') app.quit(); });
```

### Bước 4.3: Cấu hình gọi API (axios instance)
```javascript
// src/api/client.js
import axios from 'axios';
const client = axios.create({ baseURL: 'http://localhost:8080/api' });
client.interceptors.request.use((config) => {
  const token = localStorage.getItem('token'); // lưu ý: dùng cơ chế lưu an toàn hơn khi triển khai thật
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});
export default client;
```

### Bước 4.4: Giao diện màn hình bán hàng (component chính)
- [ ] Ô input ẩn luôn focus để nhận dữ liệu từ máy quét mã vạch (gõ nhanh + Enter)
- [ ] Danh sách sản phẩm trong hóa đơn hiện tại (thêm/sửa số lượng/xóa)
- [ ] Hiển thị tổng tiền, nút Thanh toán
- [ ] Gọi `POST /api/invoices` khi thanh toán, xử lý lỗi thiếu tồn kho

### Bước 4.5: Tích hợp máy quét mã vạch (HID)
```javascript
// Lắng nghe input nhanh liên tiếp + Enter để phân biệt máy quét với gõ tay
let buffer = '';
let lastTime = Date.now();

window.addEventListener('keypress', (e) => {
  const now = Date.now();
  if (now - lastTime > 50) buffer = ''; // reset nếu gõ chậm (người dùng thật)
  lastTime = now;
  if (e.key === 'Enter') {
    if (buffer.length > 3) handleBarcodeScanned(buffer);
    buffer = '';
  } else {
    buffer += e.key;
  }
});
```

### Bước 4.6: Tích hợp máy in hóa đơn (ESC/POS)
```bash
npm install node-thermal-printer
```
```javascript
const { printer: ThermalPrinter, types } = require('node-thermal-printer');
const printer = new ThermalPrinter({ type: types.EPSON, interface: 'usb' });

async function printInvoice(invoice) {
  printer.alignCenter();
  printer.println('CỬA HÀNG ABC');
  printer.drawLine();
  invoice.items.forEach(i => printer.println(`${i.name} x${i.quantity} - ${i.total}đ`));
  printer.drawLine();
  printer.println(`Tổng: ${invoice.totalAmount}đ`);
  await printer.execute();
}
```

### Bước 4.7 (Tùy chọn): Quét mã vạch bằng camera điện thoại/webcam
```bash
npm install html5-qrcode
```
```javascript
import { Html5Qrcode } from "html5-qrcode";
const scanner = new Html5Qrcode("reader");
scanner.start(
  { facingMode: "environment" },
  { fps: 10, qrbox: 250 },
  (decodedText) => handleBarcodeScanned(decodedText)
);
```
- [ ] Nếu quét bằng điện thoại riêng: viết app di động nhỏ (hoặc mở web app trên trình duyệt điện thoại) dùng thư viện trên, gửi kết quả quét qua API/WebSocket về hệ thống

---

## PHẦN 5 — Module Kho (mở rộng backend + frontend)

- [ ] API `POST /api/stock-in` — tạo phiếu nhập kho, cộng tồn kho tương ứng
- [ ] API `GET /api/inventory?branchId=` — xem tồn kho theo chi nhánh
- [ ] API `GET /api/inventory/low-stock` — danh sách sản phẩm dưới ngưỡng tồn kho
- [ ] Giao diện Electron: màn hình Quản lý sản phẩm, Nhập kho, Danh sách cảnh báo hết hàng
- [ ] Sinh mã vạch tự động cho sản phẩm mới (nếu chưa có mã từ nhà sản xuất):
```bash
# Backend
implementation 'net.sourceforge.barbecue:barbecue:1.5-beta1' // hoặc dùng ZXing để generate
```
```java
BitMatrix bitMatrix = new Code128Writer().encode(productId.toString(), BarcodeFormat.CODE_128, 300, 100);
BufferedImage barcodeImage = MatrixToImageWriter.toBufferedImage(bitMatrix);
```

---

## PHẦN 6 — Đồng bộ đa chi nhánh

### Bước 6.1: Cấu hình WebSocket phía Spring Boot
```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS();
    }
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }
}
```
- [ ] Khi tồn kho thay đổi, publish message tới `/topic/inventory-update`
- [ ] Electron client subscribe kênh này (dùng thư viện `@stomp/stompjs`) để cập nhật giao diện realtime

### Bước 6.2: Xử lý offline
- [ ] Electron lưu hóa đơn vào SQLite local (`better-sqlite3`) khi mất mạng
- [ ] Khi có mạng lại, đồng bộ các hóa đơn chưa gửi lên server (hàng đợi retry)

### Bước 6.3: Điều chuyển hàng hóa giữa chi nhánh
- [ ] API `POST /api/stock-transfer` — trừ kho chi nhánh A, cộng kho chi nhánh B trong 1 transaction

---

## PHẦN 7 — Dự báo AI tồn kho

### Bước 7.1: Chuẩn bị dữ liệu
- [ ] Query lịch sử bán hàng theo ngày cho từng sản phẩm (từ `invoice_detail` + `invoice.created_at`)

### Bước 7.2: Thuật toán dự báo đơn giản (Moving Average) — code mẫu Java
```java
public double predictNextDemand(List<Integer> last7DaysSales) {
    return last7DaysSales.stream().mapToInt(Integer::intValue).average().orElse(0);
}
```

### Bước 7.3: Nâng cấp (nếu còn thời gian) — Exponential Smoothing
```java
public double exponentialSmoothing(List<Double> sales, double alpha) {
    double forecast = sales.get(0);
    for (double actual : sales) forecast = alpha * actual + (1 - alpha) * forecast;
    return forecast;
}
```

### Bước 7.4: Sinh gợi ý nhập hàng
```java
if (predictedDemand > currentStock) {
    suggestedQty = Math.ceil(predictedDemand - currentStock);
}
```
- [ ] Chạy job định kỳ mỗi đêm bằng `@Scheduled(cron = "0 0 1 * * ?")` để cập nhật dự báo
- [ ] Hiển thị gợi ý nhập hàng trên dashboard Electron

### Bước 7.5: Đánh giá mô hình
- [ ] Tính MAE (Mean Absolute Error) giữa dự báo và thực tế trên tập dữ liệu test

---

## PHẦN 8 — Kiểm thử

- [ ] Unit test Service layer (JUnit 5 + Mockito) cho `InvoiceService`, `InventoryService`
- [ ] Test API bằng Postman Collection (lưu lại file `.postman_collection.json` trong `docs/`)
- [ ] Test giao diện Electron thủ công theo checklist chức năng
- [ ] Test tích hợp máy in/máy quét thật

---

## PHẦN 9 — Đóng gói & Triển khai

### Bước 9.1: Đóng gói Electron
```bash
npm run build          # build React
npx electron-builder --win   # tạo file .exe cài đặt
```

### Bước 9.2: Đóng gói & chạy backend
```bash
./mvnw clean package
java -jar target/pos-backend-0.0.1.jar
```
- [ ] Cân nhắc host backend trên máy chủ trung tâm (VPS hoặc máy tính tại 1 chi nhánh chính) để các chi nhánh khác kết nối vào

### Bước 9.3: Triển khai thử nghiệm thực tế
- [ ] Cài Electron app lên máy tính cửa hàng
- [ ] Kết nối máy in, máy quét thật
- [ ] Đào tạo nhân viên sử dụng
- [ ] Thu thập phản hồi và log lỗi trong 1–2 tuần dùng thử

---

## PHẦN 10 — DevOps: Docker & DockerHub (chuẩn bị cho khả năng mở rộng)

> Lưu ý: Electron là ứng dụng desktop chạy trực tiếp trên máy nhân viên (không đóng gói vào Docker được vì cần truy cập GUI/phần cứng). Phần Docker áp dụng cho **backend Spring Boot + MySQL + các service phụ trợ** — đây cũng là phần cần "scale" khi có nhiều chi nhánh cùng kết nối vào.

### Bước 10.1: Viết Dockerfile cho Spring Boot backend
```dockerfile
# backend/Dockerfile
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline
COPY src ./src
RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```
- [ ] Dùng multi-stage build (build jar ở stage 1, chỉ copy jar chạy ở stage 2) để image nhỏ gọn hơn

### Bước 10.2: Viết `.dockerignore`
```
target/
.git/
.idea/
*.iml
```

### Bước 10.3: Docker Compose cho môi trường dev/test (backend + MySQL)
```yaml
# docker-compose.yml (đặt ở thư mục gốc dự án)
version: '3.8'
services:
  mysql:
    image: mysql:8.0
    container_name: pos-mysql
    restart: unless-stopped
    environment:
      MYSQL_ROOT_PASSWORD: ${DB_PASSWORD}
      MYSQL_DATABASE: pos_inventory
    ports:
      - "3306:3306"
    volumes:
      - mysql_data:/var/lib/mysql
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 10s
      timeout: 5s
      retries: 5

  backend:
    build: ./backend
    container_name: pos-backend
    restart: unless-stopped
    depends_on:
      mysql:
        condition: service_healthy
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/pos_inventory
      SPRING_DATASOURCE_USERNAME: root
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}
      JWT_SECRET: ${JWT_SECRET}
    ports:
      - "8080:8080"

volumes:
  mysql_data:
```
- [ ] Tạo file `.env` (không commit lên Git) chứa `DB_PASSWORD`, `JWT_SECRET`
- [ ] Chạy thử: `docker compose up -d --build`

### Bước 10.4: Build & đẩy image lên DockerHub
```bash
# Đăng nhập DockerHub
docker login

# Build image với tag rõ ràng (username/tên-image:version)
docker build -t yourusername/pos-backend:1.0.0 -t yourusername/pos-backend:latest ./backend

# Đẩy lên DockerHub
docker push yourusername/pos-backend:1.0.0
docker push yourusername/pos-backend:latest
```
- [ ] Tạo repository `pos-backend` trên DockerHub (public hoặc private tùy nhu cầu)
- [ ] Đặt tag theo semver (1.0.0, 1.0.1...) mỗi lần release để dễ rollback

### Bước 10.5: Triển khai tại chi nhánh/server chỉ bằng image có sẵn (không cần build lại)
```bash
# Trên máy chủ trung tâm hoặc server chi nhánh
docker pull yourusername/pos-backend:latest
docker run -d --name pos-backend \
  -e SPRING_DATASOURCE_URL=jdbc:mysql://<db-host>:3306/pos_inventory \
  -e SPRING_DATASOURCE_USERNAME=root \
  -e SPRING_DATASOURCE_PASSWORD=${DB_PASSWORD} \
  -e JWT_SECRET=${JWT_SECRET} \
  -p 8080:8080 \
  yourusername/pos-backend:latest
```
- [ ] Đây chính là điểm thuận lợi cho scale: mỗi server mới chỉ cần `docker pull` + `docker run`, không cần cài JDK/Maven thủ công

### Bước 10.6: CI/CD tự động build & push image (GitHub Actions)
```yaml
# .github/workflows/docker-publish.yml
name: Build and Push Docker Image
on:
  push:
    branches: [main]
    paths: ['backend/**']

jobs:
  build-push:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Login to DockerHub
        uses: docker/login-action@v3
        with:
          username: ${{ secrets.DOCKERHUB_USERNAME }}
          password: ${{ secrets.DOCKERHUB_TOKEN }}
      - name: Build and push
        uses: docker/build-push-action@v5
        with:
          context: ./backend
          push: true
          tags: |
            yourusername/pos-backend:latest
            yourusername/pos-backend:${{ github.sha }}
```
- [ ] Tạo `DOCKERHUB_USERNAME` và `DOCKERHUB_TOKEN` (Access Token, không dùng password) trong GitHub Repo Settings → Secrets
- [ ] Mỗi lần push code vào `main` → tự động build image mới và đẩy lên DockerHub

### Bước 10.7: Hướng mở rộng khi cần scale thật sự (nhiều chi nhánh/traffic lớn)
- [ ] **Load balancing nhiều instance backend:** thêm Nginx/Traefik làm reverse proxy trước nhiều container backend
- [ ] **Docker Swarm** (đơn giản, phù hợp quy mô vừa): `docker stack deploy` để chạy nhiều replica backend tự động
- [ ] **Kubernetes** (nếu quy mô lớn hơn, nhiều cụm server): dùng Deployment + Service + HPA (Horizontal Pod Autoscaler) để tự động scale theo tải — có thể nêu như hướng phát triển tương lai trong báo cáo đồ án, không nhất thiết phải triển khai thật
- [ ] **Database:** khi scale lớn, cân nhắc tách MySQL ra khỏi Docker Compose, dùng managed DB (RDS, Cloud SQL) hoặc replication master-slave

### Bước 10.8: Checklist bảo mật khi dùng Docker/DockerHub
- [ ] Không hardcode password/secret trong Dockerfile hoặc image — luôn dùng biến môi trường/`.env`
- [ ] Dùng image `private` trên DockerHub nếu chứa thông tin nhạy cảm, hoặc quản lý secret riêng
- [ ] Định kỳ cập nhật base image (`eclipse-temurin`, `mysql`) để vá lỗ hổng bảo mật
- [ ] Không expose port MySQL (3306) ra ngoài internet trong môi trường production

---

## Rủi ro & lưu ý về DevOps
- Đóng gói Docker giúp **backend + DB** dễ scale/triển khai đồng nhất giữa các chi nhánh, nhưng **Electron client vẫn phải cài đặt thủ công** trên từng máy bán hàng — đây là 2 quy trình triển khai tách biệt cần làm rõ trong tài liệu hướng dẫn vận hành.
- Với quy mô đồ án (thử nghiệm 1 cửa hàng thực tế), **Docker Compose là đủ** — không cần triển khai Kubernetes thật, chỉ cần trình bày như hướng mở rộng trong phần "Định hướng phát triển" của báo cáo.
- Nhớ thêm `docker-compose.yml`, `Dockerfile`, và hướng dẫn `docker compose up` vào tài liệu README để giảng viên/người chấm có thể tự chạy thử hệ thống dễ dàng.

---

## Gợi ý thứ tự làm việc tổng quát
1. Backend cơ bản (Entity + API CRUD sản phẩm/kho) → test bằng Postman
2. Electron cơ bản (giao diện bán hàng, gọi API) → chạy thử luồng bán hàng hoàn chỉnh 1 chi nhánh
3. Tích hợp máy in + máy quét thật
4. Module báo cáo
5. Đồng bộ đa chi nhánh
6. Dự báo AI
7. Dockerize backend + MySQL, đẩy image lên DockerHub (Phần 10)
8. Kiểm thử + triển khai thực tế
