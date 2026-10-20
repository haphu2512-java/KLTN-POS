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
    branch_id BIGINT,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'CASHIER',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (branch_id) REFERENCES branch(id) ON DELETE SET NULL
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
