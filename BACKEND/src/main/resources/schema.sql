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
    client_request_id VARCHAR(64) UNIQUE,
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

-- 8b. Bảng Phiếu Điều Chuyển Kho (client_request_id chống điều chuyển 2 lần khi gửi lại)
CREATE TABLE IF NOT EXISTS stock_transfer (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    from_branch_id BIGINT NOT NULL,
    to_branch_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    employee_id BIGINT,
    note VARCHAR(255),
    client_request_id VARCHAR(64) UNIQUE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE CASCADE,
    FOREIGN KEY (from_branch_id) REFERENCES branch(id) ON DELETE CASCADE,
    FOREIGN KEY (to_branch_id) REFERENCES branch(id) ON DELETE CASCADE,
    FOREIGN KEY (employee_id) REFERENCES users(id) ON DELETE SET NULL
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
    device_id VARCHAR(64),
    device_name VARCHAR(100),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    last_used_at DATETIME,
    FOREIGN KEY (terminal_id) REFERENCES pos_terminal(id) ON DELETE CASCADE
);

-- 13. Bảng Ca Làm Việc Cố Định (mặc định 5 ca trong ngày; end_time < start_time là ca qua đêm)
CREATE TABLE IF NOT EXISTS work_shift (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    branch_id BIGINT,                    -- null: lịch ca mẫu chung; có giá trị: lịch ca riêng của chi nhánh
    name VARCHAR(50) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    sort_order INT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

-- 13b. Bảng Nhân Viên Trực Ca (không phải tài khoản đăng nhập; quầy dùng tài khoản chung)
CREATE TABLE IF NOT EXISTS staff_member (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    branch_id BIGINT NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    phone VARCHAR(20),
    position VARCHAR(50),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (branch_id) REFERENCES branch(id) ON DELETE CASCADE
);

-- 14. Bảng Ca Trực (mỗi lần nhân viên mở ca: tiền đầu ca, chốt tiền cuối ca, ghi chú bàn giao)
CREATE TABLE IF NOT EXISTS shift_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    branch_id BIGINT NOT NULL,
    work_shift_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,         -- tài khoản bấm mở ca (tài khoản quầy)
    staff_member_id BIGINT,              -- nhân viên trực ca (người được xếp ca)
    employee_name VARCHAR(100),          -- tên nhân viên trực ca
    shift_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    open_key BIGINT UNIQUE,              -- cột cũ (mỗi nhân viên 1 ca mở), ca mới để null
    branch_open_key BIGINT UNIQUE,       -- = branch_id khi ca đang mở: mỗi chi nhánh tối đa 1 ca mở
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
    handover_expected DECIMAL(14,2),     -- tiền ca trước để lại lúc mở ca này
    handover_difference DECIMAL(14,2),   -- opening_cash - handover_expected (không tính vào ca này)
    handover_reason VARCHAR(255),
    open_override_reason VARCHAR(255),   -- quản lý mở ca ngoài khung giờ
    expenses DECIMAL(14,2),              -- tổng chi trong ca
    drops DECIMAL(14,2),                 -- tổng nộp quỹ giữa ca
    float_in DECIMAL(14,2),              -- tổng bổ sung tiền lẻ
    vietqr_sales DECIMAL(14,2),
    card_sales DECIMAL(14,2),
    vietqr_refunds DECIMAL(14,2),
    card_refunds DECIMAL(14,2),
    counted_vietqr DECIMAL(14,2),        -- tiền trên sao kê VietQR
    counted_card DECIMAL(14,2),          -- tiền trên phiếu chốt máy POS ngân hàng
    vietqr_difference DECIMAL(14,2),
    card_difference DECIMAL(14,2),
    difference_reason VARCHAR(500),
    denominations VARCHAR(500),          -- số tờ theo mệnh giá, JSON
    left_in_drawer DECIMAL(14,2),        -- tiền mặt để lại cho ca sau
    final_drop DECIMAL(14,2),            -- tiền mặt nộp quỹ lúc đóng ca
    reviewed_by BIGINT,
    reviewed_at DATETIME,
    review_note VARCHAR(500),
    FOREIGN KEY (branch_id) REFERENCES branch(id) ON DELETE CASCADE,
    FOREIGN KEY (work_shift_id) REFERENCES work_shift(id),
    FOREIGN KEY (employee_id) REFERENCES users(id)
);

-- 15. Bảng Thu/Chi Két Trong Ca (chi lặt vặt, nộp quỹ giữa ca, bổ sung tiền lẻ; xóa mềm)
CREATE TABLE IF NOT EXISTS shift_cash_movement (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    shift_session_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL,           -- EXPENSE | DROP | FLOAT_IN
    category VARCHAR(50),
    amount DECIMAL(14,2) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    counterpart VARCHAR(100),            -- người nhận tiền nộp quỹ / người đưa tiền lẻ
    created_by BIGINT NOT NULL,
    created_by_name VARCHAR(100),
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    deleted_at DATETIME,
    deleted_by BIGINT,
    FOREIGN KEY (shift_session_id) REFERENCES shift_session(id) ON DELETE CASCADE
);

-- 16. Bảng Xếp Ca (quản lý xếp nhân viên vào từng ca của từng ngày; ca mở ra thuộc về người được xếp)
CREATE TABLE IF NOT EXISTS shift_assignment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    branch_id BIGINT NOT NULL,
    work_shift_id BIGINT NOT NULL,
    shift_date DATE NOT NULL,
    staff_member_id BIGINT NOT NULL,
    assigned_by BIGINT,
    assigned_at DATETIME,
    UNIQUE KEY uk_assignment_slot (branch_id, work_shift_id, shift_date),
    FOREIGN KEY (branch_id) REFERENCES branch(id) ON DELETE CASCADE,
    FOREIGN KEY (work_shift_id) REFERENCES work_shift(id),
    FOREIGN KEY (staff_member_id) REFERENCES staff_member(id)
);
