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

### Bước 1.2: Cấu trúc thư mục dự án tổng
```text
pos-inventory-system/
├── BACKEND/          # Spring Boot 3.3.x Modular Monolith API
├── FRONTEND/         # Electron + React + Vite POS Desktop App
├── docs/             # Tài liệu thiết kế, ERD, SRS, chat_history
└── README.md
```

---

## PHẦN 2 — Thiết kế Database

### Bước 2.1: Các bảng chính (MySQL 8.x)
```sql
CREATE DATABASE IF NOT EXISTS pos_inventory CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE pos_inventory;

-- 1. Chi nhánh
CREATE TABLE branch (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  address VARCHAR(255),
  phone VARCHAR(20),
  is_active BOOLEAN DEFAULT TRUE,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 2. Người dùng & Phân quyền (Admin / Manager / Cashier)
CREATE TABLE users (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  branch_id BIGINT,
  username VARCHAR(50) UNIQUE NOT NULL,
  password VARCHAR(255) NOT NULL,
  full_name VARCHAR(100) NOT NULL,
  role ENUM('ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_CASHIER') DEFAULT 'ROLE_CASHIER',
  is_active BOOLEAN DEFAULT TRUE,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (branch_id) REFERENCES branch(id)
);

-- 3. Khách hàng thân thiết & Tích điểm
CREATE TABLE customer (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  phone VARCHAR(20) UNIQUE NOT NULL,
  name VARCHAR(100) NOT NULL,
  points INT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 4. Danh mục & Sản phẩm
CREATE TABLE product (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  barcode VARCHAR(50) UNIQUE NOT NULL,
  name VARCHAR(150) NOT NULL,
  category VARCHAR(100),
  cost_price DECIMAL(12,2) NOT NULL,
  sale_price DECIMAL(12,2) NOT NULL,
  unit VARCHAR(20) DEFAULT 'cái',
  is_active BOOLEAN DEFAULT TRUE,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 5. Quản lý tồn kho theo Chi nhánh
CREATE TABLE inventory (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  product_id BIGINT NOT NULL,
  branch_id BIGINT NOT NULL,
  quantity INT DEFAULT 0,
  min_threshold INT DEFAULT 5,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (product_id) REFERENCES product(id),
  FOREIGN KEY (branch_id) REFERENCES branch(id),
  UNIQUE KEY uk_product_branch (product_id, branch_id)
);

-- 6. Nhà cung cấp
CREATE TABLE supplier (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(150) NOT NULL,
  phone VARCHAR(20),
  address VARCHAR(255),
  email VARCHAR(100)
);

-- 7. Phiếu nhập kho
CREATE TABLE stock_in (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  branch_id BIGINT NOT NULL,
  supplier_id BIGINT,
  user_id BIGINT NOT NULL,
  total_amount DECIMAL(12,2) DEFAULT 0,
  note TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (branch_id) REFERENCES branch(id),
  FOREIGN KEY (supplier_id) REFERENCES supplier(id),
  FOREIGN KEY (user_id) REFERENCES users(id)
);

-- 8. Chi tiết phiếu nhập
CREATE TABLE stock_in_detail (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  stock_in_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  quantity INT NOT NULL,
  unit_cost DECIMAL(12,2) NOT NULL,
  FOREIGN KEY (stock_in_id) REFERENCES stock_in(id),
  FOREIGN KEY (product_id) REFERENCES product(id)
);

-- 9. Hóa đơn bán hàng
CREATE TABLE invoice (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  invoice_code VARCHAR(30) UNIQUE NOT NULL,
  branch_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  customer_id BIGINT,
  total_amount DECIMAL(12,2) NOT NULL,
  discount DECIMAL(12,2) DEFAULT 0,
  final_amount DECIMAL(12,2) NOT NULL,
  payment_method VARCHAR(20) DEFAULT 'CASH', -- CASH / TRANSFER / QR
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (branch_id) REFERENCES branch(id),
  FOREIGN KEY (user_id) REFERENCES users(id),
  FOREIGN KEY (customer_id) REFERENCES customer(id)
);

-- 10. Chi tiết hóa đơn
CREATE TABLE invoice_detail (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  invoice_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  quantity INT NOT NULL,
  unit_price DECIMAL(12,2) NOT NULL,
  total_price DECIMAL(12,2) NOT NULL,
  FOREIGN KEY (invoice_id) REFERENCES invoice(id),
  FOREIGN KEY (product_id) REFERENCES product(id)
);
```

### Bước 2.2: Vẽ ERD
- [ ] Dùng dbdiagram.io hoặc MySQL Workbench để trực quan hóa các bảng trên và lưu vào `docs/erd.png`

---

## PHẦN 3 — Xây dựng Backend (Spring Boot 3.3.x Modular Monolith)

### Bước 3.1: Khởi tạo project
Chọn:
- **Project:** Maven | **Language:** Java 21 | **Spring Boot:** 3.3.3
- **Dependencies:** Spring Web, Spring Data JPA, MySQL Driver, Spring Security, Validation (`jakarta.validation`), Lombok, Spring WebSocket, SpringDoc OpenAPI (Swagger UI).
- Thư mục dự án: `BACKEND/`.

### Bước 3.2: Cấu trúc thư mục theo tính năng (Feature-based Modular Monolith)
Tuân theo chuẩn Clean Architecture / Modular Monolith:
```text
BACKEND/src/main/java/com/pos/
├── config/            # SecurityConfig, WebSocketConfig, CorsConfig, OpenApiConfig, DatabaseInitializer
├── security/          # JwtTokenProvider, JwtAuthFilter, CustomUserDetailsService
├── common/            # ApiResponse<T>, BusinessException, GlobalExceptionHandler
├── user/              # User, UserRepository, Role
├── branch/            # Branch, BranchRepository
├── customer/          # Customer, CustomerRepository, CustomerService, CustomerController
├── product/           # Product, ProductRepository, ProductService, ProductController, DTOs
├── inventory/         # Inventory, InventoryRepository, InventoryService, InventoryController, DTOs
├── sale/              # Invoice, InvoiceDetail, InvoiceRepository, InvoiceService, InvoiceController, DTOs
├── purchase/          # StockIn, StockInDetail, StockInRepository, StockInService, StockInController, DTOs
├── forecast/          # ForecastService, ForecastController, DTOs
└── PosBackendApplication.java
```

### Bước 3.3: Cấu hình `application.properties`
```properties
spring.application.name=pos-backend
server.port=8080

# Database Connection
spring.datasource.url=jdbc:mysql://localhost:3306/pos_inventory?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=Asia/Ho_Chi_Minh&allowPublicKeyRetrieval=true
spring.datasource.username=root
spring.datasource.password=
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# JPA / Hibernate
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect

# JWT Security
jwt.secret=9a3f4e8b2d1c7f0a5e8c3b6d9e2a1f4a7c0b8e6d3f2a1c5b8e9d0f3a6b2c5e8d
jwt.expiration=86400000

# OpenAPI / Swagger UI
springdoc.swagger-ui.path=/swagger-ui/index.html
springdoc.api-docs.path=/v3/api-docs
```

### Bước 3.4: Tạo Entity (Ví dụ `Product.java`)
```java
@Entity
@Table(name = "product")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String barcode;

    @Column(nullable = false, length = 150)
    private String name;

    private String category;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal costPrice;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal salePrice;

    private String unit;
    private Boolean isActive;
    private LocalDateTime createdAt;
}
```

### Bước 3.5: Tạo Repository với Atomic Concurrency Protection
Để chống Race Condition khi nhiều quầy POS cùng bán 1 sản phẩm:
```java
public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    Optional<Inventory> findByProductIdAndBranchId(Long productId, Long branchId);

    // Trừ kho nguyên tử cấp DB - Chống âm kho tuyệt đối
    @Modifying
    @Query("UPDATE Inventory i SET i.quantity = i.quantity - :qty " +
           "WHERE i.product.id = :productId AND i.branch.id = :branchId AND i.quantity >= :qty")
    int deductStock(@Param("productId") Long productId, @Param("branchId") Long branchId, @Param("qty") Integer qty);

    // Cộng kho nguyên tử
    @Modifying
    @Query("UPDATE Inventory i SET i.quantity = i.quantity + :qty " +
           "WHERE i.product.id = :productId AND i.branch.id = :branchId")
    int addStock(@Param("productId") Long productId, @Param("branchId") Long branchId, @Param("qty") Integer qty);
}
```

### Bước 3.6: Tạo Service bán hàng (@Transactional & Realtime WebSocket)
```java
@Service
@RequiredArgsConstructor
public class InvoiceService {
    private final InvoiceRepository invoiceRepo;
    private final InventoryRepository inventoryRepo;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public Invoice checkout(CreateInvoiceRequest req, User cashier) {
        // 1. Khởi tạo hóa đơn
        Invoice invoice = new Invoice();
        // ... set branch, cashier, customer, discount ...

        // 2. Duyệt từng sản phẩm & Trừ kho nguyên tử
        for (var item : req.getItems()) {
            int updated = inventoryRepo.deductStock(item.getProductId(), req.getBranchId(), item.getQuantity());
            if (updated == 0) {
                throw new BusinessException("INSUFFICIENT_STOCK", "Sản phẩm ID " + item.getProductId() + " không đủ tồn kho!");
            }
        }

        // 3. Lưu hóa đơn & Chi tiết hóa đơn
        Invoice saved = invoiceRepo.save(invoice);

        // 4. Bắn WebSocket thông báo biến động kho realtime
        messagingTemplate.convertAndSend("/topic/inventory-updates", "STOCK_CHANGED");

        return saved;
    }
}
```

### Bước 3.7: Tạo Controller & Swagger Documentation
```java
@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
@Tag(name = "Sale & Invoices", description = "Quản lý Hóa đơn & Bán hàng POS")
public class InvoiceController {
    private final InvoiceService invoiceService;

    @PostMapping("/checkout")
    public ResponseEntity<ApiResponse<InvoiceResponse>> checkout(@Valid @RequestBody CreateInvoiceRequest req) {
        return ResponseEntity.ok(ApiResponse.success(invoiceService.checkout(req)));
    }
}
```

### Bước 3.8: Tự động khởi tạo dữ liệu mẫu (DatabaseInitializer)
Khi ứng dụng khởi chạy lần đầu, `DatabaseInitializer` tự động sinh:
- Tài khoản: `admin` / `admin123` (Admin), `cashier1` / `cashier123` (Thu ngân).
- 2 chi nhánh: Chi nhánh Trung tâm & Chi nhánh Quận 1.
- Dữ liệu sản phẩm và số lượng tồn kho ban đầu để có thể test ngay mà không cần nhập tay.

### Bước 3.9: Chạy thử backend & Xem Swagger UI
```bash
cd BACKEND
./mvnw spring-boot:run
# hoặc chạy file .jar đã đóng gói:
java -jar target/pos-backend-1.0.0.jar
```
- **Swagger UI:** `http://localhost:8080/swagger-ui/index.html`
- **Postman Test Login:** `POST http://localhost:8080/api/auth/login` với body: `{"username":"admin", "password":"admin123"}`

---

## PHẦN 4 — Xây dựng Frontend (Electron + React + Vite POS)

### Bước 4.1: Cấu trúc thư mục Frontend
```text
FRONTEND/
├── src/
│   ├── components/
│   │   ├── common/          # Button, Input, Table, Badge, Modal, Tabs
│   │   └── forecast/        # SalesTrendChart (interactive SVG)
│   ├── pages/
│   │   ├── pos/             # PosPage, PosHeader, PosCart, PosPayment, ProductSearchModal, CustomerModal, VietQRModal
│   │   ├── inventory/       # InventoryPage, StockInModal, StockInDetailModal, StockTransferModal
│   │   └── forecast/        # ForecastPage (Dự báo nhu cầu & đề xuất nhập hàng)
│   ├── hooks/               # useBarcodeScanner (HID <50ms buffer), useHotkeys (F1-F9, Esc)
│   └── utils/               # formatters.js, audio.js (Web Audio API sound), forecasting.js
├── electron/
│   ├── main.cjs             # Electron Main Process (quản lý BrowserWindow, IPC)
│   └── preload.cjs          # ContextBridge an toàn cách ly (contextIsolation: true)
└── package.json
```

### Bước 4.2: Khởi tạo project
```bash
cd FRONTEND
npm install
npm run dev                  # Chạy giao diện trên trình duyệt tại http://localhost:5173
```

### Bước 4.3: Cấu hình gọi API (Axios instance)
```javascript
// src/services/api.js
import axios from 'axios';

const api = axios.create({
  baseURL: 'http://localhost:8080/api',
  timeout: 10000,
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('pos_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

export default api;
```

### Bước 4.4: Giao diện màn hình bán hàng (POS Terminal - Keyboard First)
- Hệ thống phím tắt nhanh: `F1` (Tập trung quét mã), `F2` (Tìm sản phẩm), `F3` (Khách hàng), `F4` (In lại hóa đơn), `F9` (Thanh toán), `Esc` (Đóng modal).
- Âm thanh phản hồi qua **Web Audio API**: tiếng *beep* 800Hz khi quét trúng hàng, tiếng *buzz* 220Hz khi lỗi/hết hàng.
- Tích hợp động **VietQR Napas247** chuyển khoản ngân hàng trực tiếp.

### Bước 4.5: Tích hợp máy quét mã vạch (HID Hardware Scanner)
```javascript
// src/hooks/useBarcodeScanner.js
import { useEffect, useRef } from 'react';

export function useBarcodeScanner({ onScan, minLength = 3, maxInterval = 50 }) {
  const bufferRef = useRef('');
  const lastKeyTimeRef = useRef(0);

  useEffect(() => {
    const handleKeyDown = (e) => {
      const now = Date.now();
      if (now - lastKeyTimeRef.current > maxInterval) {
        bufferRef.current = ''; // Reset nếu gõ chậm bằng tay
      }
      lastKeyTimeRef.current = now;

      if (e.key === 'Enter') {
        if (bufferRef.current.length >= minLength) {
          onScan(bufferRef.current);
        }
        bufferRef.current = '';
      } else if (e.key.length === 1) {
        bufferRef.current += e.key;
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [onScan, minLength, maxInterval]);
}
```

---

## PHẦN 5 — Module Kho (Inventory & Stock-In)

### Các API chính của Module Kho:
- `POST /api/purchases/stock-in` — Tạo phiếu nhập kho từ nhà cung cấp & cộng tồn kho tự động.
- `GET /api/inventory?branchId=` — Xem danh sách tồn kho theo chi nhánh (lọc theo danh mục, sắp hết hàng).
- `GET /api/inventory/low-stock` — Danh sách sản phẩm dưới ngưỡng an toàn (`quantity <= min_threshold`).
- `POST /api/inventory/transfer` — Điều chuyển hàng hóa giữa 2 chi nhánh trong cùng 1 transaction an toàn.

### Giao diện Quản lý kho:
- 4 thẻ thống kê: Tổng mã hàng, Tổng tồn kho, Cảnh báo sắp hết hàng, Tổng giá trị tồn (VND).
- Modal nhập kho hỗ trợ nhiều mặt hàng, tự động tính tổng tiền nhập.
- Modal điều chuyển hàng liên chi nhánh (chọn chi nhánh xuất và chi nhánh nhập).

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
- Khi tồn kho thay đổi (thanh toán POS, nhập hàng, chuyển kho), backend publish message tới `/topic/inventory-updates`.
- Khi có đơn hàng mới phát sinh, backend publish tới `/topic/sales-updates`.
- Electron client subscribe các kênh này (dùng thư viện `@stomp/stompjs`) để cập nhật giao diện realtime không cần F5.

### Bước 6.2: Xử lý offline
- [ ] Electron lưu hóa đơn vào SQLite local (`better-sqlite3`) khi mất mạng
- [ ] Khi có mạng lại, đồng bộ các hóa đơn chưa gửi lên server (hàng đợi retry)

### Bước 6.3: Điều chuyển hàng hóa giữa chi nhánh
- API `POST /api/inventory/transfer` — trừ kho chi nhánh xuất, cộng kho chi nhánh nhập trong 1 `@Transactional` nguyên tử.

---

## PHẦN 7 — Dự báo AI tồn kho & Phân tích bán hàng

### Bước 7.1: Thuật toán dự báo
Hệ thống triển khai kết hợp 2 phương pháp dự báo:
1. **Exponential Smoothing ($\alpha = 0.35$):** Đánh trọng số cao hơn cho các ngày bán gần nhất.
2. **Moving Average (7 ngày):** Lấy trung bình trượt 7 ngày gần nhất để làm mịn dữ liệu.
3. **Đánh giá sai số MAE (Mean Absolute Error):** So sánh giữa giá trị dự báo và số lượng bán thực tế để đánh giá độ chính xác.

### Bước 7.2: Các Endpoint Backend
- `GET /api/forecast/demand/{productId}?days=7` — Trả về chuỗi dữ liệu dự báo bán ra trong 7-14 ngày tới.
- `GET /api/forecast/recommendations` — Tự động tính toán khoảng chênh lệch giữa nhu cầu dự báo và tồn kho hiện tại để xuất danh sách đề xuất nhập hàng (kèm mức độ ưu tiên: Khẩn cấp / Cao / Bình thường).

### Bước 7.3: Giao diện Trực quan hóa
- Biểu đồ tương tác SVG: đường doanh số thực tế vs đường dự báo AI.
- Bảng gợi ý nhập hàng thông minh: hỗ trợ bấm 1 click để tạo ngay đơn nhập kho.

---

## PHẦN 8 — Kiểm thử

- [ ] Unit test Service layer (JUnit 5 + Mockito) cho `InvoiceService`, `InventoryService`, `ForecastService`.
- [ ] Test API bằng Swagger UI (`http://localhost:8080/swagger-ui/index.html`) hoặc Postman Collection.
- [ ] Test giao diện Electron thủ công theo checklist chức năng: Quét mã vạch <50ms, Phím tắt F1-F9, VietQR Napas247, Nhập kho nhiều sản phẩm.

---

## PHẦN 9 — Đóng gói & Triển khai

### Bước 9.1: Đóng gói Electron
```bash
cd FRONTEND
npm run build          # build React
npx electron-builder --win   # tạo file .exe cài đặt
```

### Bước 9.2: Đóng gói & chạy backend
```bash
cd BACKEND
./mvnw clean package -DskipTests
java -jar target/pos-backend-1.0.0.jar
```

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
# BACKEND/Dockerfile
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline
COPY src ./src
RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/pos-backend-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```
- Dùng multi-stage build (build jar ở stage 1, chỉ copy jar chạy ở stage 2) để image nhỏ gọn và bảo mật.

### Bước 10.2: Viết `.dockerignore`
```text
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
      MYSQL_ROOT_PASSWORD: ${DB_PASSWORD:-root}
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
    build: ./BACKEND
    container_name: pos-backend
    restart: unless-stopped
    depends_on:
      mysql:
        condition: service_healthy
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/pos_inventory?useSSL=false&serverTimezone=Asia/Ho_Chi_Minh&allowPublicKeyRetrieval=true
      SPRING_DATASOURCE_USERNAME: root
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD:-root}
      JWT_SECRET: ${JWT_SECRET:-9a3f4e8b2d1c7f0a5e8c3b6d9e2a1f4a7c0b8e6d3f2a1c5b8e9d0f3a6b2c5e8d}
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
