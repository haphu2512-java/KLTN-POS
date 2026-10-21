-- ===================================================================
-- SCHEMA DATABASE CHO HỆ THỐNG POS & QUẢN LÝ KHO (MYSQL 8.X)
-- ===================================================================

CREATE DATABASE IF NOT EXISTS pos_inventory CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE pos_inventory;

-- 1. Bảng Chi Nhánh
CREATE TABLE IF NOT EXISTS branch (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    address VARCHAR(255),
    phone VARCHAR(20),
    active BOOLEAN NOT NULL DEFAULT TRUE
);

-- 2. Bảng Tài Khoản & Nhân Viên
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'CASHIER',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 2b. Phân công chi nhánh: nhân viên chọn một trong các chi nhánh này lúc đăng nhập (ADMIN không cần gán)
CREATE TABLE IF NOT EXISTS user_branch (
    user_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, branch_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (branch_id) REFERENCES branch(id) ON DELETE CASCADE
);

-- 3. Bảng Khách Hàng
CREATE TABLE IF NOT EXISTS customer (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    phone VARCHAR(20) UNIQUE,
    points INT NOT NULL DEFAULT 0,
    address VARCHAR(255),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 4. Bảng Sản Phẩm
CREATE TABLE IF NOT EXISTS product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    barcode VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(150) NOT NULL,
    category VARCHAR(100),
    cost_price DECIMAL(14,2),
    sale_price DECIMAL(14,2) NOT NULL,
    unit VARCHAR(30) DEFAULT 'Cái',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 5. Bảng Tồn Kho Theo Chi Nhánh (Atomic Safe)
CREATE TABLE IF NOT EXISTS inventory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    quantity INT NOT NULL DEFAULT 0,
    min_threshold INT NOT NULL DEFAULT 5,
    FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE CASCADE,
    FOREIGN KEY (branch_id) REFERENCES branch(id) ON DELETE CASCADE,
    UNIQUE KEY uk_product_branch (product_id, branch_id)
);

-- 6. Bảng Nhà Cung Cấp
CREATE TABLE IF NOT EXISTS supplier (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    phone VARCHAR(20),
    address VARCHAR(255)
);

-- 7. Bảng Phiếu Nhập Kho
CREATE TABLE IF NOT EXISTS stock_in (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    branch_id BIGINT NOT NULL,
    supplier_id BIGINT,
    employee_id BIGINT,
    total_cost DECIMAL(14,2),
    note VARCHAR(255),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (branch_id) REFERENCES branch(id) ON DELETE CASCADE,
    FOREIGN KEY (supplier_id) REFERENCES supplier(id) ON DELETE SET NULL,
    FOREIGN KEY (employee_id) REFERENCES users(id) ON DELETE SET NULL
);

-- 8. Bảng Chi Tiết Phiếu Nhập Kho
CREATE TABLE IF NOT EXISTS stock_in_detail (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stock_in_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    unit_cost DECIMAL(14,2),
    FOREIGN KEY (stock_in_id) REFERENCES stock_in(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE CASCADE
);

-- 9. Bảng Hóa Đơn Bán Lẻ
CREATE TABLE IF NOT EXISTS invoice (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    branch_id BIGINT NOT NULL,
    customer_id BIGINT,
    employee_id BIGINT,
    subtotal DECIMAL(14,2),
    discount DECIMAL(14,2) DEFAULT 0,
    total_amount DECIMAL(14,2) NOT NULL,
    payment_method VARCHAR(20) NOT NULL DEFAULT 'CASH',
    received_amount DECIMAL(14,2),
    change_amount DECIMAL(14,2),
    note VARCHAR(255),
    client_request_id VARCHAR(64) UNIQUE,
    shift_session_id BIGINT,
    refunded_at DATETIME,
    refunded_by BIGINT,
    refund_shift_session_id BIGINT,      -- ca có két chi tiền hoàn trả (null: tính vào ca đã bán)
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (branch_id) REFERENCES branch(id) ON DELETE CASCADE,
    FOREIGN KEY (customer_id) REFERENCES customer(id) ON DELETE SET NULL,
    FOREIGN KEY (employee_id) REFERENCES users(id) ON DELETE SET NULL
);

-- 10. Bảng Chi Tiết Hóa Đơn Bán Lẻ
CREATE TABLE IF NOT EXISTS invoice_detail (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(14,2) NOT NULL,
    cost_price DECIMAL(14,2),
    total_price DECIMAL(14,2) NOT NULL,
    FOREIGN KEY (invoice_id) REFERENCES invoice(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE CASCADE
);

-- 11. Bảng Quầy POS (mỗi máy POS là một quầy; id do server cấp, lưu trên máy POS)
CREATE TABLE IF NOT EXISTS pos_terminal (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    branch_id BIGINT,
    created_by VARCHAR(50),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 12. Bảng Ghép cặp Điện thoại quét mã ↔ Quầy POS (tạo khi điện thoại quét QR trên máy POS)
CREATE TABLE IF NOT EXISTS scanner_pairing (
    id VARCHAR(36) PRIMARY KEY,
    terminal_id VARCHAR(36) NOT NULL,
    username VARCHAR(50) NOT NULL,
    device_name VARCHAR(100),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    last_used_at DATETIME,
    FOREIGN KEY (terminal_id) REFERENCES pos_terminal(id) ON DELETE CASCADE
);

-- 13. Bảng Ca Làm Việc Cố Định (mặc định 5 ca trong ngày; end_time < start_time là ca qua đêm)
CREATE TABLE IF NOT EXISTS work_shift (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    sort_order INT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

-- 14. Bảng Ca Trực (mỗi lần nhân viên mở ca: tiền đầu ca, chốt tiền cuối ca, ghi chú bàn giao)
CREATE TABLE IF NOT EXISTS shift_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    branch_id BIGINT NOT NULL,
    work_shift_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    employee_name VARCHAR(100),
    shift_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    open_key BIGINT UNIQUE,              -- = employee_id khi ca đang mở: mỗi nhân viên tối đa 1 ca mở
    opened_at DATETIME NOT NULL,
    opening_cash DECIMAL(14,2) NOT NULL,
    opening_note VARCHAR(500),
    closed_at DATETIME,
    closed_by BIGINT,
    closing_cash DECIMAL(14,2),          -- tiền mặt đếm được cuối ca
    expected_cash DECIMAL(14,2),         -- tiền đầu ca + doanh số tiền mặt
    cash_difference DECIMAL(14,2),       -- closing_cash - expected_cash
    cash_sales DECIMAL(14,2),
    non_cash_sales DECIMAL(14,2),
    invoice_count INT,
    cash_refunds DECIMAL(14,2),          -- tiền mặt hoàn trả chi từ két trong ca
    non_cash_refunds DECIMAL(14,2),
    refunded_count INT,
    handover_note VARCHAR(500),
    FOREIGN KEY (branch_id) REFERENCES branch(id) ON DELETE CASCADE,
    FOREIGN KEY (work_shift_id) REFERENCES work_shift(id),
    FOREIGN KEY (employee_id) REFERENCES users(id)
);
