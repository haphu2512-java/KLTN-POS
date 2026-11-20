-- =====================================================================
-- DỮ LIỆU MẪU ĐẦY ĐỦ CHO DEMO / BẢO VỆ KHÓA LUẬN
--
-- Sinh lịch sử kinh doanh liên tục ~6 tháng, kết thúc đúng lúc chạy file
-- (hôm nay: các ca đã kết thúc), để mọi màn hình đều có số liệu thật:
--   Tổng quan, Báo cáo + Excel, Dự báo AI, Kho hàng, Hạn sử dụng, Kiểm kê,
--   Đặt hàng NCC, Nhập kho, Điều chuyển, Ca làm + Lịch phân ca, Hóa đơn,
--   Trả hàng, Khách hàng thành viên + Sổ điểm, Khuyến mãi, Nhật ký hệ thống.
--
-- Cách chạy (Docker, ở thư mục gốc repo):
--   docker exec -i pos-mysql mysql -uroot -p"$DB_ROOT_PASSWORD" --default-character-set=utf8mb4 pos_inventory < data/demo/du_lieu_mau_day_du.sql
-- Mất khoảng 1-3 phút. Chạy lại vào tối hôm trước buổi báo cáo để dữ liệu
-- kéo dài tới đúng ngày báo cáo (mọi ngày tháng tính theo CURDATE()/NOW()).
--
-- Yêu cầu: backend đã chạy ít nhất 1 lần (có bảng, chi nhánh, ca làm, tài khoản),
-- đã nạp data/bhx/import_bhx_data.sql (sản phẩm) và data/bhx/nha_cung_cap.sql (NCC + danh mục).
--
-- An toàn khi chạy lại:
--   * Mọi dòng do file này tạo được ghi id vào bảng demo_seed_rows. Lần chạy sau
--     xóa đúng các dòng đó rồi tạo lại, không đụng hóa đơn / phiếu nhập thật.
--   * Cũng xóa dữ liệu '[DEMO]' của file cũ lich_su_ban_90_ngay.sql nếu có.
--   * Tài khoản, nhân viên, khách hàng mẫu được giữ lại (không xóa), điểm khách
--     được tính lại từ sổ điểm.
--   * Đặt lại tồn kho của các sản phẩm có lịch sử bán, xóa cache dự báo.
--
-- Tài khoản mẫu thêm (cùng mật khẩu với tài khoản 'cashier' = SEED_DEFAULT_PASSWORD):
--   quanly (MANAGER, mọi chi nhánh), thungan.cn<id chi nhánh> (STAFF) nếu chi nhánh chưa có tài khoản STAFF.
-- =====================================================================

SET NAMES utf8mb4;
SET SESSION cte_max_recursion_depth = 100000;
SET SESSION group_concat_max_len = 10000;

-- ----------------------------- THAM SỐ -------------------------------
SET @demo_days     = 180;           -- số ngày lịch sử (tính lùi từ hôm nay)
SET @per_category  = 4;             -- số sản phẩm mỗi danh mục có lịch sử bán
SET @inv_per_day   = 150;           -- số hóa đơn trung bình/ngày ở chi nhánh chính (chi nhánh khác ~72%)
SET @member_rate   = 0.38;          -- tỷ lệ hóa đơn có khách thành viên (khi khách đã đăng ký)
SET @n_customers   = 420;           -- số khách thành viên mẫu
SET @plan_until    = '2026-12-31';  -- xếp lịch phân ca tới ngày này (qua ngày bảo vệ 23/12)
-- ---------------------------------------------------------------------

SET @now   = NOW();
SET @today = CURDATE();
SET @start = @today - INTERVAL @demo_days DAY;

CREATE TABLE IF NOT EXISTS demo_seed_rows (
  tbl    VARCHAR(40) NOT NULL,
  row_id BIGINT      NOT NULL,
  PRIMARY KEY (tbl, row_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================================
-- 1. DỌN DỮ LIỆU MẪU CŨ
-- =====================================================================
DROP TABLE IF EXISTS demo_tmp_del_inv;
CREATE TABLE demo_tmp_del_inv (id BIGINT PRIMARY KEY);
INSERT IGNORE INTO demo_tmp_del_inv SELECT row_id FROM demo_seed_rows WHERE tbl = 'invoice';
INSERT IGNORE INTO demo_tmp_del_inv SELECT id FROM invoice WHERE note = '[DEMO]';

DELETE x FROM invoice_return_detail x JOIN invoice_return r ON r.id = x.return_id JOIN demo_tmp_del_inv t ON t.id = r.invoice_id;
DELETE r FROM invoice_return r JOIN demo_tmp_del_inv t ON t.id = r.invoice_id;
DELETE p FROM point_transaction p JOIN demo_tmp_del_inv t ON t.id = p.invoice_id;
DELETE p FROM point_transaction p JOIN demo_seed_rows s ON s.tbl = 'point_transaction' AND s.row_id = p.id;
DELETE d FROM invoice_detail d JOIN demo_tmp_del_inv t ON t.id = d.invoice_id;
DELETE i FROM invoice i JOIN demo_tmp_del_inv t ON t.id = i.id;
DROP TABLE demo_tmp_del_inv;

DELETE m FROM shift_cash_movement m JOIN demo_seed_rows s ON s.tbl = 'shift_cash_movement' AND s.row_id = m.id;
DELETE x FROM shift_session x JOIN demo_seed_rows s ON s.tbl = 'shift_session' AND s.row_id = x.id;
DELETE FROM shift_session WHERE opening_note = '[DEMO]';
DELETE x FROM shift_assignment x JOIN demo_seed_rows s ON s.tbl = 'shift_assignment' AND s.row_id = x.id;

DELETE d FROM stock_in_detail d JOIN demo_seed_rows s ON s.tbl = 'stock_in' AND s.row_id = d.stock_in_id;
DELETE x FROM stock_in x JOIN demo_seed_rows s ON s.tbl = 'stock_in' AND s.row_id = x.id;
DELETE d FROM stock_in_detail d JOIN stock_in x ON x.id = d.stock_in_id WHERE x.note LIKE '[DEMO]%';
DELETE FROM stock_in WHERE note LIKE '[DEMO]%';
DELETE l FROM purchase_order_line l JOIN demo_seed_rows s ON s.tbl = 'purchase_order' AND s.row_id = l.purchase_order_id;
DELETE x FROM purchase_order x JOIN demo_seed_rows s ON s.tbl = 'purchase_order' AND s.row_id = x.id;
DELETE l FROM purchase_order_line l JOIN purchase_order o ON o.id = l.purchase_order_id WHERE o.note LIKE '[DEMO]%';
DELETE FROM purchase_order WHERE note LIKE '[DEMO]%';

DELETE l FROM stock_count_line l JOIN demo_seed_rows s ON s.tbl = 'stock_count' AND s.row_id = l.stock_count_id;
DELETE x FROM stock_count x JOIN demo_seed_rows s ON s.tbl = 'stock_count' AND s.row_id = x.id;
DELETE x FROM stock_transfer x JOIN demo_seed_rows s ON s.tbl = 'stock_transfer' AND s.row_id = x.id;
DELETE x FROM audit_log x JOIN demo_seed_rows s ON s.tbl = 'audit_log' AND s.row_id = x.id;
DELETE x FROM promotion x JOIN demo_seed_rows s ON s.tbl = 'promotion' AND s.row_id = x.id;

-- Tài khoản, nhân viên, khách hàng mẫu được giữ lại để không làm mất dữ liệu thật gắn với họ
DELETE FROM demo_seed_rows WHERE tbl NOT IN ('users', 'staff_member', 'customer');
DELETE FROM forecast_result;

DROP TABLE IF EXISTS demo_tmp_num, demo_tmp_branch, demo_tmp_staff, demo_tmp_shift, demo_tmp_day, demo_tmp_hour,
  demo_tmp_prod, demo_tmp_week, demo_tmp_pslot, demo_tmp_cust, demo_tmp_cslot, demo_tmp_sess, demo_tmp_inv,
  demo_tmp_line, demo_tmp_promo, demo_tmp_ret, demo_tmp_move, demo_tmp_sold, demo_tmp_po, demo_tmp_po_line,
  demo_tmp_sc, demo_tmp_names;

-- Bảng số 0..9999 dùng để nhân dòng
CREATE TABLE demo_tmp_num (i INT PRIMARY KEY);
INSERT INTO demo_tmp_num WITH RECURSIVE n AS (SELECT 0 AS i UNION ALL SELECT i + 1 FROM n WHERE i < 9999) SELECT i FROM n;

-- =====================================================================
-- 2. TÀI KHOẢN, CHI NHÁNH, NHÂN VIÊN
-- =====================================================================
SET @pw = (SELECT password FROM users WHERE username = 'cashier' LIMIT 1);
SET @pw = COALESCE(@pw, (SELECT password FROM users WHERE role = 'ADMIN' ORDER BY id LIMIT 1));
SET @admin = (SELECT id FROM users WHERE role = 'ADMIN' AND active = 1 ORDER BY id LIMIT 1);

SET @mx = (SELECT COALESCE(MAX(id), 0) FROM users);
INSERT IGNORE INTO users (active, created_at, full_name, password, role, username)
VALUES (1, @start - INTERVAL 20 DAY, 'Trần Thị Mai', @pw, 'MANAGER', 'quanly');
INSERT IGNORE INTO demo_seed_rows SELECT 'users', id FROM users WHERE id > @mx;
SET @mgr = (SELECT id FROM users WHERE username = 'quanly');
SET @mgr_name = (SELECT full_name FROM users WHERE id = @mgr);
INSERT IGNORE INTO user_branch (user_id, branch_id) SELECT @mgr, id FROM branch WHERE active = 1;

CREATE TABLE demo_tmp_branch AS
SELECT b.id AS bid, b.name AS bname,
       ROW_NUMBER() OVER (ORDER BY b.id) AS k,
       CAST(NULL AS SIGNED) AS emp_id, CAST(NULL AS CHAR(100)) AS emp_user, CAST(1 AS DOUBLE) AS factor
FROM branch b WHERE b.active = 1;
UPDATE demo_tmp_branch SET factor = IF(k = 1, 1.0, 0.72);

-- Tài khoản quầy STAFF cho chi nhánh chưa có
SET @mx = (SELECT COALESCE(MAX(id), 0) FROM users);
INSERT IGNORE INTO users (active, created_at, full_name, password, role, username)
SELECT 1, @start - INTERVAL 20 DAY, ELT(1 + MOD(b.k - 1, 4), 'Lê Thị Hồng', 'Phạm Văn Nam', 'Đỗ Thị Thu', 'Võ Minh Khang'),
       @pw, 'STAFF', CONCAT('thungan.cn', b.bid)
FROM demo_tmp_branch b
WHERE NOT EXISTS (SELECT 1 FROM users u JOIN user_branch ub ON ub.user_id = u.id
                  WHERE ub.branch_id = b.bid AND u.role = 'STAFF' AND u.active = 1);
INSERT IGNORE INTO demo_seed_rows SELECT 'users', id FROM users WHERE id > @mx;
INSERT IGNORE INTO user_branch (user_id, branch_id)
SELECT u.id, b.bid FROM demo_tmp_branch b JOIN users u ON u.username = CONCAT('thungan.cn', b.bid);

UPDATE demo_tmp_branch b SET emp_id = (
  SELECT u.id FROM users u JOIN user_branch ub ON ub.user_id = u.id
  WHERE ub.branch_id = b.bid AND u.role = 'STAFF' AND u.active = 1 ORDER BY u.id LIMIT 1);
UPDATE demo_tmp_branch b JOIN users u ON u.id = b.emp_id SET b.emp_user = u.username;

-- Nhân viên đứng ca (bảng staff_member): 5 người mỗi chi nhánh
SET @mx = (SELECT COALESCE(MAX(id), 0) FROM staff_member);
INSERT INTO staff_member (active, branch_id, created_at, full_name, phone, position)
SELECT 1, b.bid, @start - INTERVAL 15 DAY, x.full_name,
       CONCAT('09', 1 + MOD(b.k, 8), LPAD(MOD(b.bid * 7919 + x.n * 104729, 10000000), 7, '0')),
       x.pos
FROM demo_tmp_branch b
JOIN (
  SELECT 1 AS n, 'Nguyễn Thị Lan'  AS a, 'Bùi Thanh Hà'    AS c, 'Trưởng ca' AS pos UNION ALL
  SELECT 2,      'Trần Minh Tuấn',      'Lý Gia Hân',          'Thu ngân'           UNION ALL
  SELECT 3,      'Lê Hoàng Yến',        'Huỳnh Đức Thịnh',     'Thu ngân'           UNION ALL
  SELECT 4,      'Phạm Quốc Bảo',       'Đặng Ngọc Trâm',      'Thu ngân'           UNION ALL
  SELECT 5,      'Võ Thị Kim Ngân',     'Ngô Tấn Phát',        'Bán hàng - Kho'
) y ON 1 = 1
JOIN LATERAL (SELECT y.n AS n, IF(MOD(b.k, 2) = 1, y.a, y.c) AS full_name, y.pos AS pos) x ON 1 = 1
WHERE NOT EXISTS (SELECT 1 FROM staff_member s WHERE s.branch_id = b.bid AND s.full_name = x.full_name);
INSERT IGNORE INTO demo_seed_rows SELECT 'staff_member', id FROM staff_member WHERE id > @mx;

CREATE TABLE demo_tmp_staff AS
SELECT s.branch_id AS bid, s.id AS sid, s.full_name AS sname,
       ROW_NUMBER() OVER (PARTITION BY s.branch_id ORDER BY s.id) AS idx
FROM staff_member s
JOIN demo_seed_rows r ON r.tbl = 'staff_member' AND r.row_id = s.id
JOIN demo_tmp_branch b ON b.bid = s.branch_id
WHERE s.active = 1;
ALTER TABLE demo_tmp_branch ADD COLUMN nstaff INT;
UPDATE demo_tmp_branch b SET nstaff = (SELECT COUNT(*) FROM demo_tmp_staff s WHERE s.bid = b.bid);

-- Ca làm: ca riêng của chi nhánh nếu có, không thì ca mặc định
CREATE TABLE demo_tmp_shift AS
SELECT b.bid, w.id AS ws_id, w.name AS ws_name, w.start_time, w.end_time,
       ROW_NUMBER() OVER (PARTITION BY b.bid ORDER BY w.sort_order, w.start_time) AS k
FROM demo_tmp_branch b
JOIN work_shift w ON w.active = 1
 AND (w.branch_id = b.bid
      OR (w.branch_id IS NULL AND NOT EXISTS (SELECT 1 FROM work_shift w2 WHERE w2.active = 1 AND w2.branch_id = b.bid)))
WHERE w.end_time > w.start_time;

DELETE FROM demo_tmp_branch WHERE emp_id IS NULL OR nstaff = 0
   OR NOT EXISTS (SELECT 1 FROM demo_tmp_shift s WHERE s.bid = demo_tmp_branch.bid);

-- =====================================================================
-- 3. NGÀY, GIỜ CAO ĐIỂM, MÙA VỤ
-- =====================================================================
CREATE TABLE demo_tmp_day (d INT PRIMARY KEY, dt DATE, factor DOUBLE);
INSERT INTO demo_tmp_day (d, dt, factor)
SELECT n.i, @start + INTERVAL n.i DAY,
       -- thứ trong tuần (CN=1 ... T7=7)
       ELT(DAYOFWEEK(@start + INTERVAL n.i DAY), 1.25, 0.92, 0.90, 0.95, 0.97, 1.06, 1.22)
       -- cửa hàng tăng trưởng dần ~25% sau 6 tháng
       * (0.86 + 0.24 * n.i / @demo_days)
       -- đầu tháng nhận lương mua nhiều, cuối tháng chậm lại
       * CASE WHEN DAY(@start + INTERVAL n.i DAY) <= 5 THEN 1.08
              WHEN DAY(@start + INTERVAL n.i DAY) IN (15, 16) THEN 1.05
              WHEN DAY(@start + INTERVAL n.i DAY) >= 27 THEN 0.95 ELSE 1 END
       -- ngày lễ
       * CASE DATE_FORMAT(@start + INTERVAL n.i DAY, '%m-%d')
              WHEN '09-01' THEN 1.35 WHEN '09-02' THEN 0.75 WHEN '09-03' THEN 0.85
              WHEN '10-19' THEN 1.12 WHEN '10-20' THEN 1.25
              WHEN '11-19' THEN 1.10 WHEN '11-20' THEN 1.18
              WHEN '11-27' THEN 1.15 WHEN '11-28' THEN 1.20
              WHEN '12-24' THEN 1.40 WHEN '12-25' THEN 1.30 WHEN '12-31' THEN 1.45
              WHEN '01-01' THEN 0.70 ELSE 1 END
       -- thời tiết, ngẫu nhiên
       * (0.88 + 0.24 * RAND())
FROM demo_tmp_num n WHERE n.i <= @demo_days;

-- Phân bố giờ mua hàng (tổng 100): cao điểm sáng 7h, trưa 11-12h, tối 17-20h
CREATE TABLE demo_tmp_hour (h INT PRIMARY KEY, w INT, lo INT, hi INT);
INSERT INTO demo_tmp_hour (h, w) VALUES
 (6,3),(7,8),(8,6),(9,4),(10,4),(11,7),(12,7),(13,3),(14,3),(15,4),(16,7),(17,10),(18,12),(19,11),(20,8),(21,3);
UPDATE demo_tmp_hour h JOIN (
  SELECT h, SUM(w) OVER (ORDER BY h) - w AS lo, SUM(w) OVER (ORDER BY h) AS hi FROM demo_tmp_hour
) c ON c.h = h.h SET h.lo = c.lo, h.hi = c.hi;

-- =====================================================================
-- 4. SẢN PHẨM CÓ LỊCH SỬ BÁN: @per_category sản phẩm mỗi danh mục
-- =====================================================================
CREATE TABLE demo_tmp_prod AS
SELECT x.pid, x.cat, x.price, x.cost, x.unit, x.pname, x.barcode,
       CRC32(CONCAT('speed', x.pid)) % 100 AS h_speed,
       CRC32(CONCAT('trend', x.pid)) % 100 AS h_trend,
       CRC32(CONCAT('rate',  x.pid)) % 1000 / 1000 AS h_rate,
       CAST(0 AS DOUBLE) AS base, CAST(0 AS DOUBLE) AS trend, 0 AS launch_week, 1 AS pack,
       CAST(NULL AS SIGNED) AS sup_id, CAST(NULL AS SIGNED) AS shelf_days
FROM (
  SELECT p.id AS pid, p.category AS cat, p.sale_price AS price,
         COALESCE(p.cost_price, ROUND(p.sale_price * 0.8, 0)) AS cost, p.unit, p.name AS pname, p.barcode,
         ROW_NUMBER() OVER (PARTITION BY p.category ORDER BY CRC32(CONCAT('pick', p.id))) AS rn
  FROM product p
  WHERE p.active = 1 AND p.sale_price > 0
) x
WHERE x.rn <= @per_category;
ALTER TABLE demo_tmp_prod ADD PRIMARY KEY (pid);

UPDATE demo_tmp_prod SET
  -- ~15% bán chạy, ~35% trung bình, ~50% bán chậm; hàng giá cao bán ít hơn
  base = CASE WHEN h_speed < 15 THEN 10 + 6 * h_rate
              WHEN h_speed < 50 THEN 2 + 2 * h_rate
              ELSE 0.3 + 0.6 * h_rate END
         * LEAST(1.6, GREATEST(0.15, POW(30000 / price, 0.8))),
  -- ~12% đang tăng (+60% sau 6 tháng), ~6% đang giảm (-35%)
  trend = CASE WHEN h_trend < 12 THEN 0.6 WHEN h_trend < 18 THEN -0.35 ELSE 0 END,
  -- ~4% là hàng mới về giữa kỳ (dự báo ít lịch sử)
  launch_week = IF(h_trend BETWEEN 95 AND 98, FLOOR(@demo_days / 7) - 6, 0),
  pack = CASE WHEN price >= 150000 THEN 1 WHEN price >= 50000 THEN 3 ELSE 6 END,
  shelf_days = CASE
      WHEN cat IN ('Sữa chua', 'Sữa chua uống', 'Tráng miệng') THEN 60
      WHEN cat IN ('Sữa tươi', 'Sữa hạt, sữa đậu') THEN 150
      WHEN cat LIKE 'Bánh%' OR cat LIKE 'Kẹo%' OR cat IN ('Socola', 'Tiệc tại gia') THEN 210
      WHEN cat IN ('Mì ăn liền', 'Hủ tiếu, miến', 'Phở, bún ăn liền', 'Cháo gói, cháo tươi') THEN 180
      WHEN cat LIKE 'Nước%' OR cat LIKE 'Bia%' OR cat LIKE 'Cà phê%' OR cat LIKE 'Trà%' THEN 300
      WHEN cat IN ('Rau lá tươi', 'Rau tươi', 'Củ, quả tươi', 'Trái cây tươi', 'Rau sống & Salad', 'Cá, hải sản tươi',
                   'Thịt heo', 'Thịt bò', 'Thịt gà, vịt', 'Thịt & Món mặn', 'Trứng gà, vịt, cút') THEN NULL
      ELSE 540 END;

-- NCC bán danh mục của sản phẩm (NCC có id nhỏ nhất)
UPDATE demo_tmp_prod p SET sup_id = (SELECT MIN(sc.supplier_id) FROM supplier_category sc WHERE sc.category = p.cat);

-- Trọng số bán theo tuần: xu hướng, mùa vụ theo danh mục, hàng mới
CREATE TABLE demo_tmp_week AS
SELECT n.i AS w, @start + INTERVAL (n.i * 7 + 3) DAY AS mid
FROM demo_tmp_num n WHERE n.i <= FLOOR(@demo_days / 7);

CREATE TABLE demo_tmp_pslot (w INT, slot INT, pid BIGINT, multi TINYINT, PRIMARY KEY (w, slot));
INSERT INTO demo_tmp_pslot (w, slot, pid, multi)
SELECT c.w, n.i, c.pid, c.multi
FROM (
  SELECT z.*, FLOOR(10000 * (SUM(z.wt) OVER (PARTITION BY z.w ORDER BY z.pid) - z.wt) / SUM(z.wt) OVER (PARTITION BY z.w)) AS lo,
              FLOOR(10000 * SUM(z.wt) OVER (PARTITION BY z.w ORDER BY z.pid) / SUM(z.wt) OVER (PARTITION BY z.w)) AS hi
  FROM (
    SELECT wk.w, p.pid,
           (p.cat LIKE 'Nước%' OR p.cat LIKE 'Bia%' OR p.cat = 'Mì ăn liền' OR p.cat LIKE 'Sữa chua%') AS multi,
           IF(wk.w < p.launch_week, 0,
             p.base * (1 + p.trend * DATEDIFF(wk.mid, @start) / @demo_days)
             * CASE
                 WHEN p.cat LIKE 'Bia%' THEN ELT(MONTH(wk.mid), 1.5, 1.2, 0.9, 1.0, 1.15, 1.25, 1.25, 1.2, 1.0, 0.95, 1.0, 1.45)
                 WHEN p.cat LIKE 'Nước%' THEN ELT(MONTH(wk.mid), 1.1, 1.0, 1.0, 1.15, 1.3, 1.35, 1.3, 1.25, 1.05, 0.95, 0.9, 1.05)
                 WHEN p.cat LIKE 'Bánh%' OR p.cat LIKE 'Kẹo%' OR p.cat IN ('Socola', 'Tiệc tại gia', 'Hạt khô', 'Trái cây sấy',
                      'Hạt khô & Hạt dinh dưỡng')
                      THEN ELT(MONTH(wk.mid), 1.6, 1.3, 0.9, 0.9, 0.9, 0.95, 0.95, 0.95, 1.0, 1.05, 1.15, 1.45)
                 WHEN p.cat IN ('Mì ăn liền', 'Hủ tiếu, miến', 'Phở, bún ăn liền', 'Cháo gói, cháo tươi')
                      THEN ELT(MONTH(wk.mid), 0.9, 0.9, 0.95, 0.95, 1.0, 1.05, 1.1, 1.2, 1.25, 1.2, 1.05, 0.95)
                 WHEN p.cat LIKE 'Sữa%' THEN ELT(MONTH(wk.mid), 1.0, 1.0, 1.0, 1.0, 1.0, 1.1, 1.1, 1.1, 1.18, 1.05, 1.05, 1.0)
                 ELSE 1 END) AS wt
    FROM demo_tmp_week wk CROSS JOIN demo_tmp_prod p
  ) z
) c
JOIN demo_tmp_num n ON n.i >= c.lo AND n.i < c.hi;

-- =====================================================================
-- 5. KHÁCH HÀNG THÀNH VIÊN
-- =====================================================================
CREATE TABLE demo_tmp_names (k INT PRIMARY KEY, full_name VARCHAR(100), phone VARCHAR(20), home INT, joined DATETIME);
INSERT INTO demo_tmp_names (k, full_name, phone, home, joined)
SELECT n.i,
       CONCAT_WS(' ',
         ELT(1 + MOD(n.i * 7, 14), 'Nguyễn', 'Trần', 'Lê', 'Phạm', 'Hoàng', 'Huỳnh', 'Phan', 'Vũ', 'Võ', 'Đặng', 'Bùi', 'Đỗ', 'Hồ', 'Ngô'),
         IF(MOD(n.i, 2) = 0,
            ELT(1 + MOD(n.i * 3, 8), 'Thị', 'Ngọc', 'Thu', 'Thanh', 'Mỹ', 'Kim', 'Bảo', 'Phương'),
            ELT(1 + MOD(n.i * 3, 8), 'Văn', 'Minh', 'Quốc', 'Đức', 'Hoàng', 'Gia', 'Anh', 'Thành')),
         IF(MOD(n.i, 2) = 0,
            ELT(1 + MOD(FLOOR(n.i / 2) * 11, 20), 'Lan', 'Hương', 'Trang', 'Linh', 'Ngân', 'Vy', 'Thảo', 'Hà', 'My', 'Nhung',
                'Uyên', 'Trâm', 'Hạnh', 'Quyên', 'Yến', 'Phương', 'Tâm', 'Diễm', 'Như', 'Hiền'),
            ELT(1 + MOD(FLOOR(n.i / 2) * 11, 20), 'Hùng', 'Dũng', 'Tuấn', 'Khoa', 'Long', 'Phúc', 'Huy', 'Nam', 'Tài', 'Khang',
                'Bình', 'Sơn', 'Trí', 'Thắng', 'Lộc', 'Hải', 'Vinh', 'Kiên', 'Đạt', 'Quân'))),
       CONCAT(ELT(1 + MOD(n.i, 10), '090', '093', '097', '098', '091', '094', '086', '032', '035', '038'),
              LPAD(MOD(n.i * 7919 + 1234567, 10000000), 7, '0')),
       1 + MOD(n.i * 13, (SELECT COUNT(*) FROM demo_tmp_branch)),
       -- 35% là khách cũ (đăng ký trước kỳ), còn lại đăng ký rải rác trong kỳ
       IF(CRC32(CONCAT('old', n.i)) % 100 < 35,
          @start - INTERVAL (10 + CRC32(CONCAT('od', n.i)) % 300) DAY,
          TIMESTAMP(@start + INTERVAL (CRC32(CONCAT('jd', n.i)) % @demo_days) DAY, '08:00:00'))
FROM demo_tmp_num n WHERE n.i >= 1 AND n.i <= @n_customers;

SET @mx = (SELECT COALESCE(MAX(id), 0) FROM customer);
INSERT IGNORE INTO customer (address, created_at, name, phone, points)
SELECT ELT(1 + MOD(k, 8), 'Q.1, TP.HCM', 'Q.3, TP.HCM', 'Q.10, TP.HCM', 'Q.5, TP.HCM', 'Bình Thạnh, TP.HCM',
           'Phú Nhuận, TP.HCM', 'Tân Bình, TP.HCM', NULL),
       joined, full_name, phone, 0
FROM demo_tmp_names;
INSERT IGNORE INTO demo_seed_rows SELECT 'customer', id FROM customer WHERE id > @mx;

CREATE TABLE demo_tmp_cust AS
SELECT c.id AS cid, n.home, n.joined,
       1 / POW(ROW_NUMBER() OVER (ORDER BY CRC32(CONCAT('loyal', c.id))), 0.7) AS wt
FROM customer c
JOIN demo_seed_rows r ON r.tbl = 'customer' AND r.row_id = c.id
JOIN demo_tmp_names n ON n.phone = c.phone;

-- Mỗi chi nhánh 2000 "ô": khách ở gần chi nhánh nào thì hay mua ở đó
CREATE TABLE demo_tmp_cslot (bid BIGINT, slot INT, cid BIGINT, joined DATETIME, PRIMARY KEY (bid, slot));
INSERT INTO demo_tmp_cslot (bid, slot, cid, joined)
SELECT c.bid, n.i, c.cid, c.joined
FROM (
  SELECT z.*, FLOOR(2000 * (SUM(z.w2) OVER (PARTITION BY z.bid ORDER BY z.cid) - z.w2) / SUM(z.w2) OVER (PARTITION BY z.bid)) AS lo,
              FLOOR(2000 * SUM(z.w2) OVER (PARTITION BY z.bid ORDER BY z.cid) / SUM(z.w2) OVER (PARTITION BY z.bid)) AS hi
  FROM (SELECT b.bid, c.cid, c.joined, c.wt * IF(c.home = b.k, 1, 0.12) AS w2
        FROM demo_tmp_branch b CROSS JOIN demo_tmp_cust c) z
) c
JOIN demo_tmp_num n ON n.i >= c.lo AND n.i < c.hi;

-- =====================================================================
-- 6. KHUYẾN MÃI (theo các dịp lễ năm 2026)
-- =====================================================================
SET @mx = (SELECT COALESCE(MAX(id), 0) FROM promotion);
INSERT INTO promotion (active, branch_id, category, created_at, created_by, description, end_at, max_discount_amount,
                       member_only, min_customer_points, min_order_amount, name, start_at, type, updated_at, updated_by, discount_value)
VALUES
 (1, NULL, NULL,        '2026-05-25 09:00:00', @mgr, 'Giảm 15.000đ cho khách thành viên, đơn từ 250.000đ', NULL, NULL,
  1, NULL, 250000, 'Ưu đãi thành viên thân thiết', '2026-06-01 00:00:00', 'FIXED_AMOUNT', NULL, NULL, 15000),
 (1, NULL, 'Nước ngọt', '2026-05-28 09:00:00', @mgr, 'Giảm 10% nước ngọt các loại mùa hè', '2026-09-01 00:00:00', 30000,
  0, NULL, NULL, 'Hè sảng khoái - Nước ngọt giảm 10%', '2026-06-01 00:00:00', 'PERCENT', NULL, NULL, 10),
 (1, NULL, 'Mì ăn liền', '2026-08-10 09:00:00', @mgr, 'Giảm 5.000đ khi mua mì ăn liền từ 50.000đ', '2026-10-01 00:00:00', NULL,
  0, NULL, 50000, 'Mùa mưa ấm bụng - Mì ăn liền', '2026-08-15 00:00:00', 'FIXED_AMOUNT', NULL, NULL, 5000),
 (1, NULL, NULL,        '2026-08-25 09:00:00', @mgr, 'Giảm 10% (tối đa 50.000đ) cho đơn từ 200.000đ', '2026-09-03 00:00:00', 50000,
  0, NULL, 200000, 'Mừng Quốc khánh 2/9', '2026-08-29 00:00:00', 'PERCENT', NULL, NULL, 10),
 (1, (SELECT MAX(bid) FROM demo_tmp_branch), 'Sữa bột & Sữa dinh dưỡng', '2026-09-10 09:00:00', @mgr,
  'Giảm 7% sữa bột, sữa dinh dưỡng mùa tựu trường (chỉ chi nhánh mới)', '2026-10-16 00:00:00', 50000,
  0, NULL, NULL, 'Tựu trường - Sữa cho bé', '2026-09-15 00:00:00', 'PERCENT', NULL, NULL, 7),
 (1, NULL, 'Socola',    '2026-10-12 09:00:00', @mgr, 'Giảm 15% socola dịp 20/10', '2026-10-22 00:00:00', NULL,
  0, NULL, NULL, 'Ngày Phụ nữ Việt Nam 20/10', '2026-10-18 00:00:00', 'PERCENT', NULL, NULL, 15),
 (1, NULL, NULL,        '2026-11-12 09:00:00', @mgr, 'Giảm 8% (tối đa 40.000đ) cho đơn từ 150.000đ', '2026-11-22 00:00:00', 40000,
  0, NULL, 150000, 'Tri ân Nhà giáo 20/11', '2026-11-17 00:00:00', 'PERCENT', NULL, NULL, 8),
 (1, NULL, NULL,        '2026-11-15 09:00:00', @mgr, 'Giảm 12% (tối đa 60.000đ) cho đơn từ 300.000đ', '2026-12-01 00:00:00', 60000,
  0, NULL, 300000, 'Black Friday cuối tuần', '2026-11-27 00:00:00', 'PERCENT', NULL, NULL, 12),
 (1, NULL, 'Bánh quy',  '2026-11-18 09:00:00', @mgr, 'Giảm 10% bánh quy, hộp quà Giáng sinh', '2026-12-27 00:00:00', NULL,
  0, NULL, NULL, 'Giáng sinh an lành', '2026-12-20 00:00:00', 'PERCENT', NULL, NULL, 10),
 (1, NULL, NULL,        '2026-11-18 09:30:00', @mgr, 'Giảm 30.000đ cho đơn từ 300.000đ', '2027-01-03 00:00:00', NULL,
  0, NULL, 300000, 'Chào năm mới 2027', '2026-12-30 00:00:00', 'FIXED_AMOUNT', NULL, NULL, 30000),
 (0, NULL, 'Bánh snack', '2026-07-01 09:00:00', @mgr, 'Thử nghiệm giảm 5% bánh snack (đã tắt)', '2026-08-01 00:00:00', NULL,
  0, NULL, NULL, 'Thử nghiệm snack tháng 7', '2026-07-01 00:00:00', 'PERCENT', '2026-07-10 18:00:00', @mgr, 5);
INSERT IGNORE INTO demo_seed_rows SELECT 'promotion', id FROM promotion WHERE id > @mx;

-- =====================================================================
-- 7. CA LÀM ĐÃ ĐÓNG (mọi ca đã kết thúc tới thời điểm chạy file, trừ ca đã có thật)
-- =====================================================================
CREATE TABLE demo_tmp_sess AS
SELECT b.bid, s.ws_id, s.k AS shift_k, dd.d, dd.dt,
       TIMESTAMP(dd.dt, s.start_time) - INTERVAL FLOOR(3 + RAND() * 10) MINUTE AS opened_at,
       TIMESTAMP(dd.dt, s.end_time) + INTERVAL FLOOR(2 + RAND() * 12) MINUTE AS closed_at,
       TIMESTAMP(dd.dt, s.start_time) AS t_start, TIMESTAMP(dd.dt, s.end_time) AS t_end,
       st.sid, st.sname, b.emp_id,
       CAST(NULL AS SIGNED) AS sess_id
FROM demo_tmp_day dd
CROSS JOIN demo_tmp_branch b
JOIN demo_tmp_shift s ON s.bid = b.bid
JOIN demo_tmp_staff st ON st.bid = b.bid
 AND st.idx = 1 + MOD(DATEDIFF(dd.dt, '2026-01-05') + (s.k - 1) * 2 + IF(MOD(DATEDIFF(dd.dt, '2026-01-05'), 14) >= 7, 1, 0), b.nstaff)
WHERE TIMESTAMP(dd.dt, s.end_time) + INTERVAL 15 MINUTE <= @now
  -- bỏ ca đã có ca thật (mở trên POS) cùng chi nhánh, ngày, ca
  AND NOT EXISTS (SELECT 1 FROM shift_session x WHERE x.branch_id = b.bid AND x.shift_date = dd.dt AND x.work_shift_id = s.ws_id);
ALTER TABLE demo_tmp_sess ADD INDEX (bid, dt, ws_id);

SET @mx = (SELECT COALESCE(MAX(id), 0) FROM shift_session);
INSERT INTO shift_session
  (branch_id, work_shift_id, shift_date, employee_id, staff_member_id, employee_name, status,
   opened_at, closed_at, closed_by, opening_cash,
   cash_sales, vietqr_sales, card_sales, non_cash_sales, invoice_count,
   cash_refunds, vietqr_refunds, card_refunds, non_cash_refunds, refunded_count,
   expenses, drops, float_in)
SELECT bid, ws_id, dt, emp_id, sid, sname, 'CLOSED', opened_at, closed_at, emp_id, 1000000,
       0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
FROM demo_tmp_sess ORDER BY opened_at, bid;
INSERT IGNORE INTO demo_seed_rows SELECT 'shift_session', id FROM shift_session WHERE id > @mx;
UPDATE demo_tmp_sess t JOIN shift_session s
  ON s.id > @mx AND s.branch_id = t.bid AND s.shift_date = t.dt AND s.work_shift_id = t.ws_id
SET t.sess_id = s.id;

-- =====================================================================
-- 8. HÓA ĐƠN
-- =====================================================================
CREATE TABLE demo_tmp_inv (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  d INT, bid BIGINT, dt DATE, r_hour DOUBLE, r_min DOUBLE, r_lines DOUBLE, r_pay DOUBLE, r_cust DOUBLE, r_disc DOUBLE,
  r_redeem DOUBLE, r_cash DOUBLE,
  h INT, created_at DATETIME, sess_id BIGINT, ws_id BIGINT, emp_id BIGINT, w INT, cid BIGINT,
  nline INT, pm VARCHAR(10), inv_id BIGINT,
  INDEX (inv_id)
);
INSERT INTO demo_tmp_inv (d, bid, dt, r_hour, r_min, r_lines, r_pay, r_cust, r_disc, r_redeem, r_cash, w, emp_id)
SELECT dd.d, b.bid, dd.dt, RAND(), RAND(), RAND(), RAND(), RAND(), RAND(), RAND(), RAND(), FLOOR(dd.d / 7), b.emp_id
FROM demo_tmp_day dd
CROSS JOIN demo_tmp_branch b
JOIN demo_tmp_num n ON n.i < ROUND(@inv_per_day * b.factor * dd.factor);

UPDATE demo_tmp_inv v JOIN demo_tmp_hour h ON FLOOR(v.r_hour * 100) >= h.lo AND FLOOR(v.r_hour * 100) < h.hi
SET v.h = h.h, v.created_at = TIMESTAMP(v.dt, MAKETIME(h.h, FLOOR(v.r_min * 60), FLOOR(RAND() * 60)));

-- Gắn vào ca đang mở lúc bán; hóa đơn ngoài giờ ca hoặc của ca chưa kết thúc thì bỏ
UPDATE demo_tmp_inv v
JOIN demo_tmp_sess s ON s.bid = v.bid AND s.dt = v.dt AND v.created_at >= s.t_start AND v.created_at < s.t_end
SET v.sess_id = s.sess_id, v.ws_id = s.ws_id;
DELETE FROM demo_tmp_inv WHERE sess_id IS NULL OR created_at IS NULL;

-- Số dòng hàng mỗi hóa đơn (trung bình ~2,8) và phương thức thanh toán (VietQR tăng dần theo thời gian)
UPDATE demo_tmp_inv SET
  nline = CASE WHEN r_lines < 0.30 THEN 1 WHEN r_lines < 0.55 THEN 2 WHEN r_lines < 0.73 THEN 3
               WHEN r_lines < 0.85 THEN 4 WHEN r_lines < 0.93 THEN 5 ELSE 6 + FLOOR(RAND() * 5) END,
  pm = CASE WHEN r_pay < 0.27 + 0.15 * d / @demo_days THEN 'VIETQR'
            WHEN r_pay < 0.37 + 0.15 * d / @demo_days THEN 'CARD'
            ELSE 'CASH' END;

-- Khách thành viên: chỉ sau ngày khách đăng ký
UPDATE demo_tmp_inv v
JOIN demo_tmp_cslot c ON c.bid = v.bid AND c.slot = FLOOR(v.r_cust / @member_rate * 2000)
SET v.cid = c.cid
WHERE v.r_cust < @member_rate AND c.joined <= v.created_at;

SET @mx_inv = (SELECT COALESCE(MAX(id), 0) FROM invoice);
INSERT INTO invoice
  (client_request_id, created_at, branch_id, customer_id, employee_id, shift_session_id, note,
   payment_method, status, subtotal, discount, promotion_discount, points_discount,
   points_earned, points_redeemed, refunded_amount, total_amount)
SELECT CONCAT('DEMO-', v.id), v.created_at, v.bid, v.cid, v.emp_id, v.sess_id, NULL,
       v.pm, 'PAID', 0, 0, 0, 0, 0, 0, NULL, 0
FROM demo_tmp_inv v ORDER BY v.created_at, v.id;
INSERT IGNORE INTO demo_seed_rows SELECT 'invoice', id FROM invoice WHERE id > @mx_inv;
UPDATE demo_tmp_inv v JOIN invoice i ON i.client_request_id = CONCAT('DEMO-', v.id) SET v.inv_id = i.id;

-- Dòng hàng: chọn sản phẩm theo trọng số của tuần, số lượng 1-3 (nước, bia, mì, sữa chua hay mua theo lốc)
CREATE TABLE demo_tmp_line AS
SELECT v.inv_id, v.w, FLOOR(RAND() * 10000) AS slot,
       1 + (RAND() < 0.20) + (RAND() < 0.06) AS qty, RAND() AS r_multi
FROM demo_tmp_inv v
JOIN demo_tmp_num n ON n.i < v.nline;
ALTER TABLE demo_tmp_line ADD COLUMN pid BIGINT;
UPDATE demo_tmp_line l JOIN demo_tmp_pslot s ON s.w = l.w AND s.slot = l.slot
SET l.pid = s.pid, l.qty = l.qty + IF(s.multi AND l.r_multi < 0.15, 1 + FLOOR(l.r_multi * 26), 0);

INSERT INTO invoice_detail (invoice_id, product_id, quantity, returned_quantity, unit_price, cost_price, total_price)
SELECT l.inv_id, l.pid, SUM(l.qty), 0, p.price, p.cost, SUM(l.qty) * p.price
FROM demo_tmp_line l JOIN demo_tmp_prod p ON p.pid = l.pid
WHERE l.pid IS NOT NULL
GROUP BY l.inv_id, l.pid, p.price, p.cost;

UPDATE invoice i
JOIN (SELECT invoice_id, SUM(total_price) AS s FROM invoice_detail WHERE invoice_id > @mx_inv GROUP BY invoice_id) t
  ON t.invoice_id = i.id
SET i.subtotal = t.s, i.total_amount = t.s
WHERE i.id > @mx_inv;
-- Hóa đơn không có dòng (sản phẩm hàng mới chưa bán) thì bỏ
DELETE i FROM invoice i
WHERE i.id > @mx_inv AND NOT EXISTS (SELECT 1 FROM invoice_detail d WHERE d.invoice_id = i.id);
DELETE r FROM demo_seed_rows r LEFT JOIN invoice i ON i.id = r.row_id WHERE r.tbl = 'invoice' AND i.id IS NULL;

-- Cập nhật thống kê chỉ mục sau khi thêm nhiều dòng để các câu sau chọn đúng chỉ mục
ANALYZE TABLE invoice, invoice_detail, shift_session;

-- Khuyến mãi: POS tự chọn chương trình giảm nhiều nhất mà đơn đủ điều kiện
CREATE TABLE demo_tmp_promo AS
SELECT q.inv_id, q.promo_id, q.pname, q.amount,
       ROW_NUMBER() OVER (PARTITION BY q.inv_id ORDER BY q.amount DESC, q.promo_id) AS rn
FROM (
  SELECT i.id AS inv_id, p.id AS promo_id, p.name AS pname,
         FLOOR(LEAST(base.amt,
               IF(p.type = 'PERCENT', LEAST(FLOOR(base.amt * p.discount_value / 100), COALESCE(p.max_discount_amount, 1e12)),
                  p.discount_value))) AS amount
  FROM invoice i
  JOIN demo_seed_rows r ON r.tbl = 'promotion'
  JOIN promotion p ON p.id = r.row_id AND p.active = 1
  JOIN LATERAL (
    SELECT COALESCE(SUM(d.total_price), 0) AS amt
    FROM invoice_detail d JOIN product pr ON pr.id = d.product_id
    WHERE d.invoice_id = i.id AND (p.category IS NULL OR pr.category = p.category)
  ) base ON 1 = 1
  WHERE i.id > @mx_inv
    AND i.created_at >= p.start_at AND (p.end_at IS NULL OR i.created_at < p.end_at)
    AND (p.branch_id IS NULL OR p.branch_id = i.branch_id)
    AND (p.member_only = 0 OR i.customer_id IS NOT NULL)
    AND (p.min_order_amount IS NULL OR base.amt >= p.min_order_amount)
) q
WHERE q.amount > 0;
UPDATE invoice i JOIN demo_tmp_promo q ON q.inv_id = i.id AND q.rn = 1
SET i.promotion_id = q.promo_id, i.promotion_name = q.pname, i.promotion_discount = q.amount;

-- Giảm tay: làm tròn tiền lẻ (~2%), khách mua nhiều giảm 5% (~1%), giảm 10% có quản lý duyệt PIN (~0,3%)
UPDATE invoice i JOIN demo_tmp_inv v ON v.inv_id = i.id
SET i.discount = CASE
      WHEN v.r_disc < 0.003 AND i.subtotal - i.promotion_discount >= 300000
           THEN FLOOR((i.subtotal - i.promotion_discount) * 0.10 / 1000) * 1000
      WHEN v.r_disc < 0.013 AND i.subtotal - i.promotion_discount >= 200000
           THEN FLOOR((i.subtotal - i.promotion_discount) * 0.05 / 1000) * 1000
      WHEN v.r_disc >= 0.013 AND v.r_disc < 0.033 AND MOD(i.subtotal - i.promotion_discount, 1000) > 0
           THEN MOD(i.subtotal - i.promotion_discount, 1000)
      ELSE 0 END,
    i.discount_reason = CASE
      WHEN v.r_disc < 0.003 AND i.subtotal - i.promotion_discount >= 300000 THEN 'Khách quen mua số lượng lớn'
      WHEN v.r_disc < 0.013 AND i.subtotal - i.promotion_discount >= 200000 THEN 'Khách mua nhiều, giảm 5%'
      WHEN v.r_disc >= 0.013 AND v.r_disc < 0.033 AND MOD(i.subtotal - i.promotion_discount, 1000) > 0 THEN 'Làm tròn tiền lẻ'
      ELSE NULL END,
    i.discount_approved_by = IF(v.r_disc < 0.003 AND i.subtotal - i.promotion_discount >= 300000, @mgr, NULL);
UPDATE invoice SET total_amount = subtotal - promotion_discount - discount WHERE id > @mx_inv;

-- =====================================================================
-- 9. TÍCH ĐIỂM / ĐỔI ĐIỂM theo đúng thứ tự thời gian (1đ / 10.000đ, 1 điểm = 1.000đ,
--    dùng điểm tối đa 50% đơn, tối thiểu 10 điểm). Điểm cộng tính sau khi trừ điểm.
-- =====================================================================
UPDATE customer c JOIN demo_seed_rows r ON r.tbl = 'customer' AND r.row_id = c.id
SET c.points = COALESCE((SELECT SUM(p.points) FROM point_transaction p WHERE p.customer_id = c.id), 0);

DROP PROCEDURE IF EXISTS demo_seed_loyalty;
DELIMITER $$
CREATE PROCEDURE demo_seed_loyalty()
BEGIN
  DECLARE done INT DEFAULT 0;
  DECLARE v_id, v_cid, v_emp BIGINT;
  DECLARE v_after, v_total DECIMAL(14,2);
  DECLARE v_at DATETIME(6);
  DECLARE v_r DOUBLE;
  DECLARE v_bal, v_red, v_earn INT;
  DECLARE cur CURSOR FOR
    SELECT i.id, i.customer_id, i.total_amount, i.created_at, i.employee_id, v.r_redeem
    FROM invoice i JOIN demo_tmp_inv v ON v.inv_id = i.id
    WHERE i.customer_id IS NOT NULL
    ORDER BY i.created_at, i.id;
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;
  OPEN cur;
  lp: LOOP
    FETCH cur INTO v_id, v_cid, v_after, v_at, v_emp, v_r;
    IF done = 1 THEN LEAVE lp; END IF;
    SELECT points INTO v_bal FROM customer WHERE id = v_cid;
    SET v_red = 0;
    IF v_bal >= 40 AND v_r < 0.30 THEN
      SET v_red = LEAST(v_bal, FLOOR(FLOOR(v_after * 0.5) / 1000));
      IF v_red < 10 THEN SET v_red = 0; END IF;
    END IF;
    SET v_total = v_after - v_red * 1000;
    SET v_earn = FLOOR(v_total / 10000);
    IF v_red > 0 THEN
      INSERT INTO point_transaction (balance_after, created_at, created_by, customer_id, invoice_id, note, points, type)
      VALUES (v_bal - v_red, v_at, v_emp, v_cid, v_id, NULL, -v_red, 'REDEEM');
    END IF;
    IF v_earn > 0 THEN
      INSERT INTO point_transaction (balance_after, created_at, created_by, customer_id, invoice_id, note, points, type)
      VALUES (v_bal - v_red + v_earn, v_at, v_emp, v_cid, v_id, NULL, v_earn, 'EARN');
    END IF;
    UPDATE customer SET points = v_bal - v_red + v_earn WHERE id = v_cid;
    UPDATE invoice SET points_redeemed = v_red, points_discount = v_red * 1000, points_earned = v_earn, total_amount = v_total
    WHERE id = v_id;
  END LOOP;
  CLOSE cur;
END$$
DELIMITER ;

SET @mx_pt = (SELECT COALESCE(MAX(id), 0) FROM point_transaction);
START TRANSACTION;
CALL demo_seed_loyalty();
COMMIT;
DROP PROCEDURE demo_seed_loyalty;

-- Điều chỉnh điểm tay (vài khách, quản lý ghi lý do)
INSERT INTO point_transaction (balance_after, created_at, created_by, customer_id, invoice_id, note, points, type)
SELECT c.points + 20, @now - INTERVAL (5 + c.id % 50) MINUTE, @mgr, c.id, NULL, 'Tặng điểm sinh nhật khách thân thiết', 20, 'ADJUST'
FROM customer c JOIN demo_tmp_cust t ON t.cid = c.id
WHERE CRC32(CONCAT('adj', c.id)) % 100 < 3;
UPDATE customer c JOIN point_transaction p ON p.customer_id = c.id AND p.id > @mx_pt AND p.type = 'ADJUST'
SET c.points = c.points + p.points;
INSERT IGNORE INTO demo_seed_rows SELECT 'point_transaction', id FROM point_transaction WHERE id > @mx_pt;

-- Ngày đăng ký của khách không muộn hơn lần mua đầu tiên
UPDATE customer c
JOIN (SELECT customer_id, MIN(created_at) AS first_at FROM invoice WHERE id > @mx_inv AND customer_id IS NOT NULL GROUP BY customer_id) f
  ON f.customer_id = c.id
SET c.created_at = LEAST(c.created_at, f.first_at - INTERVAL 3 MINUTE);

-- Tiền khách đưa và tiền thối (tiền mặt: làm tròn lên 10.000đ / 50.000đ / 100.000đ hoặc đưa vừa đủ)
UPDATE invoice i JOIN demo_tmp_inv v ON v.inv_id = i.id
SET i.received_amount = IF(i.payment_method = 'CASH',
      CASE WHEN v.r_cash < 0.25 THEN i.total_amount
           WHEN v.r_cash < 0.55 OR i.total_amount >= 400000 THEN CEIL(i.total_amount / 10000) * 10000
           WHEN v.r_cash < 0.80 THEN CEIL(i.total_amount / 50000) * 50000
           ELSE CEIL(i.total_amount / 100000) * 100000 END,
      i.total_amount);
UPDATE invoice SET change_amount = received_amount - total_amount WHERE id > @mx_inv;

-- =====================================================================
-- 10. TRẢ HÀNG (~0,6% hóa đơn khách lẻ, trong 2-40 giờ sau khi mua, có quản lý duyệt)
-- =====================================================================
CREATE TABLE demo_tmp_ret AS
SELECT i.id AS inv_id, i.branch_id AS bid, i.payment_method AS pm, i.created_at,
       i.created_at + INTERVAL FLOOR(120 + RAND() * 2280) MINUTE AS target,
       RAND() < 0.25 AS full_return, RAND() AS r_reason,
       CAST(NULL AS SIGNED) AS sess_id, CAST(NULL AS DATETIME) AS ret_at, CAST(NULL AS SIGNED) AS emp_id,
       CAST(NULL AS SIGNED) AS ret_id
FROM invoice i
WHERE i.id > @mx_inv AND i.customer_id IS NULL AND i.promotion_id IS NULL AND i.discount = 0
  AND RAND() < 0.006;
UPDATE demo_tmp_ret r SET r.sess_id = (
  SELECT s.sess_id FROM demo_tmp_sess s WHERE s.bid = r.bid AND s.t_end > r.target ORDER BY s.t_end LIMIT 1);
UPDATE demo_tmp_ret r JOIN demo_tmp_sess s ON s.sess_id = r.sess_id
SET r.ret_at = GREATEST(r.target, s.t_start + INTERVAL FLOOR(5 + RAND() * 60) MINUTE), r.emp_id = s.emp_id;
DELETE FROM demo_tmp_ret WHERE sess_id IS NULL OR ret_at IS NULL OR ret_at >= @now;
DELETE r FROM demo_tmp_ret r JOIN demo_tmp_sess s ON s.sess_id = r.sess_id WHERE r.ret_at >= s.t_end;

-- Dòng trả: trả hết hóa đơn, hoặc 1 dòng (1-2 đơn vị)
DROP TABLE IF EXISTS demo_tmp_ret_line;
CREATE TABLE demo_tmp_ret_line AS
SELECT r.inv_id, d.id AS detail_id, d.product_id, d.unit_price,
       IF(r.full_return, d.quantity, LEAST(d.quantity, 1 + (CRC32(CONCAT('rq', d.id)) % 10 < 3))) AS qty
FROM demo_tmp_ret r
JOIN invoice_detail d ON d.invoice_id = r.inv_id
WHERE r.full_return
   OR d.id = (SELECT d2.id FROM invoice_detail d2 WHERE d2.invoice_id = r.inv_id ORDER BY CRC32(CONCAT('rl', d2.id)) LIMIT 1);

SET @mx_ret = (SELECT COALESCE(MAX(id), 0) FROM invoice_return);
INSERT INTO invoice_return (approved_by, branch_id, created_at, created_by, invoice_id, payment_method,
                            points_returned, points_revoked, reason, refund_amount, shift_session_id)
SELECT @mgr, r.bid, r.ret_at, r.emp_id, r.inv_id, r.pm, 0, 0,
       ELT(1 + FLOOR(r.r_reason * 5), 'Hàng lỗi bao bì, móp méo', 'Khách đổi ý, hàng còn nguyên', 'Sản phẩm gần hết hạn',
           'Thu ngân quét nhầm sản phẩm', 'Khách mua trùng'),
       (SELECT SUM(l.qty * l.unit_price) FROM demo_tmp_ret_line l WHERE l.inv_id = r.inv_id), r.sess_id
FROM demo_tmp_ret r ORDER BY r.ret_at;
UPDATE demo_tmp_ret r JOIN invoice_return x ON x.id > @mx_ret AND x.invoice_id = r.inv_id SET r.ret_id = x.id;

INSERT INTO invoice_return_detail (invoice_detail_id, product_id, product_name, quantity, refund_amount, unit_price, return_id)
SELECT l.detail_id, l.product_id, p.name, l.qty, l.qty * l.unit_price, l.unit_price, r.ret_id
FROM demo_tmp_ret_line l JOIN demo_tmp_ret r ON r.inv_id = l.inv_id JOIN product p ON p.id = l.product_id;

UPDATE invoice_detail d JOIN demo_tmp_ret_line l ON l.detail_id = d.id SET d.returned_quantity = l.qty;

UPDATE invoice i
JOIN demo_tmp_ret r ON r.inv_id = i.id
JOIN invoice_return x ON x.id = r.ret_id
JOIN (SELECT invoice_id, SUM(quantity - returned_quantity) AS remain FROM invoice_detail
      WHERE invoice_id IN (SELECT inv_id FROM demo_tmp_ret) GROUP BY invoice_id) q ON q.invoice_id = i.id
SET i.refunded_amount = x.refund_amount,
    i.status = IF(q.remain = 0, 'REFUNDED', 'PARTIALLY_REFUNDED'),
    i.refunded_at = IF(q.remain = 0, x.created_at, NULL),
    i.refunded_by = IF(q.remain = 0, x.created_by, NULL),
    i.refund_shift_session_id = IF(q.remain = 0, x.shift_session_id, NULL),
    i.note = IF(q.remain = 0, CONCAT('[ĐÃ HOÀN TRẢ | Lý do trả: ', x.reason, ']'),
                CONCAT('[HOÀN TRẢ MỘT PHẦN ', FORMAT(x.refund_amount, 0, 'vi_VN'), 'đ | Lý do trả: ', x.reason, ']'));

-- =====================================================================
-- 11. TỔNG KẾT CA: doanh thu theo phương thức, hoàn tiền, thu chi két, chốt tiền
-- =====================================================================
UPDATE shift_session ss
JOIN demo_tmp_sess t ON t.sess_id = ss.id
LEFT JOIN (
  SELECT shift_session_id AS sid,
         SUM(IF(payment_method = 'CASH',   total_amount, 0)) AS cash,
         SUM(IF(payment_method = 'VIETQR', total_amount, 0)) AS qr,
         SUM(IF(payment_method = 'CARD',   total_amount, 0)) AS card,
         COUNT(*) AS cnt
  FROM invoice WHERE id > @mx_inv GROUP BY shift_session_id
) s ON s.sid = ss.id
LEFT JOIN (
  SELECT shift_session_id AS sid,
         SUM(IF(payment_method = 'CASH',   refund_amount, 0)) AS cash,
         SUM(IF(payment_method = 'VIETQR', refund_amount, 0)) AS qr,
         SUM(IF(payment_method = 'CARD',   refund_amount, 0)) AS card,
         COUNT(*) AS cnt
  FROM invoice_return WHERE id > @mx_ret GROUP BY shift_session_id
) r ON r.sid = ss.id
SET ss.cash_sales = COALESCE(s.cash, 0), ss.vietqr_sales = COALESCE(s.qr, 0), ss.card_sales = COALESCE(s.card, 0),
    ss.non_cash_sales = COALESCE(s.qr, 0) + COALESCE(s.card, 0), ss.invoice_count = COALESCE(s.cnt, 0),
    ss.cash_refunds = COALESCE(r.cash, 0), ss.vietqr_refunds = COALESCE(r.qr, 0), ss.card_refunds = COALESCE(r.card, 0),
    ss.non_cash_refunds = COALESCE(r.qr, 0) + COALESCE(r.card, 0), ss.refunded_count = COALESCE(r.cnt, 0);

-- Thu chi két trong ca
CREATE TABLE demo_tmp_move (sess_id BIGINT, type VARCHAR(10), amount DECIMAL(14,2), category VARCHAR(50),
                            counterpart VARCHAR(100), reason VARCHAR(255), created_at DATETIME, created_by BIGINT, created_by_name VARCHAR(100));
INSERT INTO demo_tmp_move
SELECT t.sess_id, 'EXPENSE', ELT(1 + x.k, 35000, 60000, 120000, 45000, 200000, 80000), ELT(1 + x.k, 'Vật tư', 'Vận chuyển', 'Sửa chữa', 'Vật tư', 'Vệ sinh', 'Khác'),
       ELT(1 + x.k, 'Cửa hàng bao bì Minh Phát', 'Xe ôm công nghệ', 'Thợ điện Tuấn', 'Đại lý đá viên Hòa Bình', 'Dịch vụ vệ sinh Sạch Xanh', NULL),
       ELT(1 + x.k, 'Mua túi nilon, băng keo', 'Phí giao hàng bổ sung kho', 'Sửa đèn quầy thu ngân', 'Mua đá lạnh cho tủ nước',
           'Vệ sinh máy lạnh cửa hàng', 'Mua văn phòng phẩm'),
       t.t_start + INTERVAL FLOOR(30 + RAND() * 120) MINUTE, t.emp_id, t.sname
FROM demo_tmp_sess t
JOIN (SELECT sess_id, CRC32(CONCAT('ex', sess_id)) % 6 AS k FROM demo_tmp_sess) x ON x.sess_id = t.sess_id
WHERE CRC32(CONCAT('exp', t.sess_id)) % 100 < 10;
INSERT INTO demo_tmp_move
SELECT t.sess_id, 'FLOAT_IN', 500000, 'Tiền lẻ', 'Quỹ chính', 'Nhận thêm tiền lẻ từ quỹ chính',
       t.t_start + INTERVAL FLOOR(10 + RAND() * 30) MINUTE, t.emp_id, t.sname
FROM demo_tmp_sess t WHERE RAND() < 0.03;
-- Két vượt hạn mức 5 triệu thì nộp bớt về quỹ chính giữa ca
INSERT INTO demo_tmp_move
SELECT x.sess_id, 'DROP', x.amt, 'Nộp quỹ', 'Quỹ chính', 'Nộp bớt tiền về quỹ chính (két vượt hạn mức)',
       x.t_start + INTERVAL TIMESTAMPDIFF(MINUTE, x.t_start, x.t_end) * 2 DIV 3 MINUTE, x.emp_id, x.sname
FROM (
  SELECT t.sess_id, t.t_start, t.t_end, t.emp_id, t.sname,
         FLOOR((ss.opening_cash + ss.cash_sales - ss.cash_refunds - COALESCE(m.ex, 0) + COALESCE(m.fl, 0) - 1500000) / 500000) * 500000 AS amt
  FROM demo_tmp_sess t JOIN shift_session ss ON ss.id = t.sess_id
  LEFT JOIN (SELECT sess_id, SUM(IF(type = 'EXPENSE', amount, 0)) AS ex, SUM(IF(type = 'FLOAT_IN', amount, 0)) AS fl
             FROM demo_tmp_move GROUP BY sess_id) m ON m.sess_id = t.sess_id
  WHERE ss.opening_cash + ss.cash_sales - ss.cash_refunds - COALESCE(m.ex, 0) + COALESCE(m.fl, 0) > 5000000
) x WHERE x.amt > 0;

SET @mx = (SELECT COALESCE(MAX(id), 0) FROM shift_cash_movement);
INSERT INTO shift_cash_movement (amount, category, counterpart, created_at, created_by, created_by_name, reason, shift_session_id, type)
SELECT amount, category, counterpart, created_at, created_by, created_by_name, reason, sess_id, type FROM demo_tmp_move ORDER BY created_at;
INSERT IGNORE INTO demo_seed_rows SELECT 'shift_cash_movement', id FROM shift_cash_movement WHERE id > @mx;

UPDATE shift_session ss
JOIN demo_tmp_sess t ON t.sess_id = ss.id
LEFT JOIN (
  SELECT sess_id, SUM(IF(type = 'EXPENSE', amount, 0)) AS ex, SUM(IF(type = 'DROP', amount, 0)) AS dr,
         SUM(IF(type = 'FLOAT_IN', amount, 0)) AS fl
  FROM demo_tmp_move GROUP BY sess_id
) m ON m.sess_id = ss.id
SET ss.expenses = COALESCE(m.ex, 0), ss.drops = COALESCE(m.dr, 0), ss.float_in = COALESCE(m.fl, 0),
    ss.expected_cash = ss.opening_cash + ss.cash_sales - ss.cash_refunds - COALESCE(m.ex, 0) - COALESCE(m.dr, 0) + COALESCE(m.fl, 0);

-- Chốt ca: ~4% ca lệch tiền mặt nhỏ (ca gần đây chờ duyệt, ca cũ quản lý đã duyệt)
UPDATE shift_session ss
JOIN demo_tmp_sess t ON t.sess_id = ss.id
SET ss.cash_difference = IF(CRC32(CONCAT('df', ss.id)) % 1000 < 40,
                            ELT(1 + CRC32(CONCAT('dv', ss.id)) % 6, -10000, -20000, -5000, 10000, -50000, -2000), 0);
UPDATE shift_session ss
JOIN demo_tmp_sess t ON t.sess_id = ss.id
SET ss.closing_cash = ss.expected_cash + ss.cash_difference,
    ss.counted_vietqr = ss.vietqr_sales - ss.vietqr_refunds, ss.counted_card = ss.card_sales - ss.card_refunds,
    ss.vietqr_difference = 0, ss.card_difference = 0,
    ss.left_in_drawer = LEAST(1000000, ss.expected_cash + ss.cash_difference),
    ss.final_drop = ss.expected_cash + ss.cash_difference - LEAST(1000000, ss.expected_cash + ss.cash_difference),
    ss.difference_reason = IF(ss.cash_difference = 0, NULL,
        IF(ss.cash_difference > 0, 'Khách không lấy tiền thối', 'Thối nhầm tiền cho khách giờ cao điểm')),
    ss.status = IF(ss.cash_difference <> 0 AND ss.closed_at >= @now - INTERVAL 3 DAY, 'PENDING_REVIEW', 'CLOSED'),
    ss.reviewed_by = IF(ss.cash_difference <> 0 AND ss.closed_at < @now - INTERVAL 3 DAY, @mgr, NULL),
    ss.reviewed_at = IF(ss.cash_difference <> 0 AND ss.closed_at < @now - INTERVAL 3 DAY, TIMESTAMP(DATE(ss.closed_at) + INTERVAL 1 DAY, '08:30:00'), NULL),
    ss.review_note = IF(ss.cash_difference <> 0 AND ss.closed_at < @now - INTERVAL 3 DAY,
        IF(ss.cash_difference > 0, 'Đã kiểm tra, nộp phần dư vào quỹ', 'Đã xem camera, nhân viên bù tiền thiếu'), NULL);

-- Bàn giao két: ca sau nhận đúng số ca trước để lại
UPDATE shift_session ss
JOIN (
  SELECT s.id, LAG(s.left_in_drawer) OVER (PARTITION BY s.branch_id ORDER BY s.opened_at) AS prev_left
  FROM shift_session s JOIN demo_tmp_sess t ON t.sess_id = s.id
) h ON h.id = ss.id
SET ss.handover_expected = h.prev_left,
    ss.handover_difference = IF(h.prev_left IS NULL, NULL, ss.opening_cash - h.prev_left),
    ss.handover_reason = IF(h.prev_left IS NOT NULL AND ss.opening_cash <> h.prev_left, 'Bổ sung tiền lẻ từ quỹ chính cho đủ 1.000.000đ', NULL);

-- =====================================================================
-- 12. LỊCH PHÂN CA: các ca đã làm + lịch tới @plan_until
-- =====================================================================
SET @mx = (SELECT COALESCE(MAX(id), 0) FROM shift_assignment);
INSERT IGNORE INTO shift_assignment (assigned_at, assigned_by, branch_id, shift_date, staff_member_id, work_shift_id)
SELECT TIMESTAMP(t.dt - INTERVAL (DAYOFWEEK(t.dt) + 1) DAY, '17:00:00'), @mgr, t.bid, t.dt, t.sid, t.ws_id
FROM demo_tmp_sess t;
INSERT IGNORE INTO shift_assignment (assigned_at, assigned_by, branch_id, shift_date, staff_member_id, work_shift_id)
SELECT LEAST(@now, TIMESTAMP(x.dt - INTERVAL (DAYOFWEEK(x.dt) + 1) DAY, '17:00:00')), @mgr, b.bid, x.dt, st.sid, s.ws_id
FROM (SELECT @today + INTERVAL n.i DAY AS dt FROM demo_tmp_num n WHERE @today + INTERVAL n.i DAY <= @plan_until) x
CROSS JOIN demo_tmp_branch b
JOIN demo_tmp_shift s ON s.bid = b.bid
JOIN demo_tmp_staff st ON st.bid = b.bid
 AND st.idx = 1 + MOD(DATEDIFF(x.dt, '2026-01-05') + (s.k - 1) * 2 + IF(MOD(DATEDIFF(x.dt, '2026-01-05'), 14) >= 7, 1, 0), b.nstaff);
INSERT IGNORE INTO demo_seed_rows SELECT 'shift_assignment', id FROM shift_assignment WHERE id > @mx;

-- =====================================================================
-- 13. ĐẶT HÀNG NCC + NHẬP KHO HẰNG TUẦN (số đặt ≈ số bán tuần trước, làm tròn theo lốc/thùng)
-- =====================================================================
CREATE TABLE demo_tmp_sold AS
SELECT i.branch_id AS bid, d.product_id AS pid, FLOOR(DATEDIFF(DATE(i.created_at), @start) / 7) AS w,
       SUM(d.quantity - d.returned_quantity) AS qty
FROM invoice i JOIN invoice_detail d ON d.invoice_id = i.id
WHERE i.id > @mx_inv AND i.status <> 'REFUNDED'
GROUP BY i.branch_id, d.product_id, FLOOR(DATEDIFF(DATE(i.created_at), @start) / 7);
ALTER TABLE demo_tmp_sold ADD INDEX (bid, w);

-- Mỗi chi nhánh, mỗi NCC, mỗi tuần một đơn; NCC giao sau 2 ngày
CREATE TABLE demo_tmp_po AS
SELECT x.*, x.ordered_at + INTERVAL 30 MINUTE AS sent_at,
       TIMESTAMP(DATE(x.ordered_at) + INTERVAL 2 DAY, MAKETIME(7 + MOD(x.sid, 3), 15 * MOD(x.sid, 4), 0)) AS received_at,
       CAST(NULL AS CHAR(20)) AS status, CAST(NULL AS SIGNED) AS po_id, CAST(NULL AS SIGNED) AS si_id,
       IF(CRC32(CONCAT('fc', x.bid, '-', x.sid, '-', x.w)) % 100 < 35, 'FORECAST', 'MANUAL') AS source
FROM (
  SELECT DISTINCT s.bid, p.sup_id AS sid, s.w,
         TIMESTAMP(@start + INTERVAL (s.w * 7 + 7 + MOD(p.sup_id, 5)) DAY, MAKETIME(9 + MOD(p.sup_id, 3), 0, 0)) AS ordered_at
  FROM demo_tmp_sold s JOIN demo_tmp_prod p ON p.pid = s.pid
  WHERE p.sup_id IS NOT NULL
) x
WHERE x.ordered_at <= @now;
UPDATE demo_tmp_po SET status = IF(received_at <= @now, 'RECEIVED', 'SENT');
-- Một đơn bị NCC hủy, một đơn giao thiếu đang chờ giao bù
UPDATE demo_tmp_po SET status = 'CANCELLED'
WHERE (bid, sid, w) = (SELECT bid, sid, w FROM (SELECT bid, sid, w FROM demo_tmp_po WHERE w = 6 ORDER BY bid, sid LIMIT 1) z);
UPDATE demo_tmp_po SET status = 'PARTIALLY_RECEIVED'
WHERE (bid, sid, w) = (SELECT bid, sid, w FROM (SELECT bid, sid, w FROM demo_tmp_po WHERE status = 'RECEIVED'
                                                ORDER BY received_at DESC, bid, sid LIMIT 1) z);

CREATE TABLE demo_tmp_po_line AS
SELECT o.bid, o.sid, o.w, s.pid, p.pname, p.barcode, p.cat, p.cost, p.pack, p.shelf_days,
       CEIL(s.qty / p.pack) * p.pack AS ordered_qty,
       CEIL(s.qty / p.pack) * p.pack AS received_qty,
       s.qty AS last_week
FROM demo_tmp_po o
JOIN demo_tmp_sold s ON s.bid = o.bid AND s.w = o.w
JOIN demo_tmp_prod p ON p.pid = s.pid AND p.sup_id = o.sid;
UPDATE demo_tmp_po_line l JOIN demo_tmp_po o ON o.bid = l.bid AND o.sid = l.sid AND o.w = l.w
SET l.received_qty = CASE WHEN o.status IN ('SENT', 'CANCELLED') THEN 0
                          WHEN o.status = 'PARTIALLY_RECEIVED' AND l.ordered_qty >= 2 * l.pack THEN l.ordered_qty - l.pack
                          ELSE l.ordered_qty END;

SET @mx_po = (SELECT COALESCE(MAX(id), 0) FROM purchase_order);
INSERT INTO purchase_order (branch_id, supplier_id, supplier_name, status, source, note, expected_date,
                            created_by, created_at, sent_at, closed_at)
SELECT o.bid, o.sid, s.name, o.status, o.source,
       CASE WHEN o.status = 'CANCELLED' THEN 'Đặt hàng định kỳ tuần - NCC báo hết hàng, hủy đơn'
            WHEN o.source = 'FORECAST' THEN 'Lập từ đề xuất AI dự báo 7 ngày'
            ELSE 'Đặt hàng định kỳ tuần' END,
       DATE(o.received_at), @mgr, o.ordered_at, o.sent_at,
       CASE WHEN o.status = 'RECEIVED' THEN o.received_at WHEN o.status = 'CANCELLED' THEN o.sent_at + INTERVAL 1 DAY END
FROM demo_tmp_po o JOIN supplier s ON s.id = o.sid
ORDER BY o.ordered_at, o.bid, o.sid;
INSERT IGNORE INTO demo_seed_rows SELECT 'purchase_order', id FROM purchase_order WHERE id > @mx_po;
UPDATE demo_tmp_po o JOIN purchase_order p
  ON p.id > @mx_po AND p.branch_id = o.bid AND p.supplier_id = o.sid AND p.created_at = o.ordered_at
SET o.po_id = p.id;

INSERT INTO purchase_order_line (purchase_order_id, product_id, product_name, barcode, category, ordered_qty, received_qty,
                                 suggested_qty, forecast_note, unit_cost)
SELECT o.po_id, l.pid, l.pname, l.barcode, l.cat, l.ordered_qty, l.received_qty,
       IF(o.source = 'FORECAST', l.ordered_qty, NULL),
       IF(o.source = 'FORECAST', CONCAT('Bán 7 ngày qua: ', l.last_week, ', dự báo 7 ngày tới ~', ROUND(l.last_week * 1.05)), NULL),
       l.cost
FROM demo_tmp_po_line l JOIN demo_tmp_po o ON o.bid = l.bid AND o.sid = l.sid AND o.w = l.w;

-- Phiếu nhập kho sinh ra khi nhận hàng, ghi mã đơn gốc, có số lô và hạn dùng
SET @mx_si = (SELECT COALESCE(MAX(id), 0) FROM stock_in);
INSERT INTO stock_in (branch_id, supplier_id, purchase_order_id, employee_id, total_cost, note, created_at)
SELECT o.bid, o.sid, o.po_id, @mgr,
       (SELECT SUM(l.received_qty * l.cost) FROM demo_tmp_po_line l WHERE l.bid = o.bid AND l.sid = o.sid AND l.w = o.w),
       CONCAT('Nhận hàng theo đơn đặt #', o.po_id, IF(o.status = 'PARTIALLY_RECEIVED', ' - NCC giao thiếu, hẹn giao bù', '')),
       o.received_at
FROM demo_tmp_po o WHERE o.status IN ('RECEIVED', 'PARTIALLY_RECEIVED')
ORDER BY o.received_at, o.bid, o.sid;
-- Một vài phiếu nhập tự do (hàng tươi mua chợ đầu mối, không qua đơn đặt)
INSERT INTO stock_in (branch_id, supplier_id, purchase_order_id, employee_id, total_cost, note, created_at)
SELECT b.bid, (SELECT MIN(sc.supplier_id) FROM supplier_category sc WHERE sc.category = 'Rau lá tươi'), NULL, @mgr, 0,
       'Nhập bổ sung rau củ buổi sáng', TIMESTAMP(@start + INTERVAL n.i DAY, '06:20:00')
FROM demo_tmp_branch b JOIN demo_tmp_num n ON MOD(n.i, 10) = 3 AND n.i < @demo_days;
INSERT IGNORE INTO demo_seed_rows SELECT 'stock_in', id FROM stock_in WHERE id > @mx_si;
UPDATE demo_tmp_po o JOIN stock_in s ON s.id > @mx_si AND s.purchase_order_id = o.po_id SET o.si_id = s.id;

INSERT INTO stock_in_detail (stock_in_id, product_id, quantity, unit_cost, lot_number, expiry_date)
SELECT o.si_id, l.pid, l.received_qty, l.cost,
       IF(l.shelf_days IS NULL, NULL, CONCAT('L', DATE_FORMAT(o.received_at, '%y%m%d'), '-', l.pid)),
       IF(l.shelf_days IS NULL, NULL, DATE(o.received_at) + INTERVAL (l.shelf_days - CRC32(CONCAT('ex', o.si_id, l.pid)) % 15) DAY)
FROM demo_tmp_po_line l JOIN demo_tmp_po o ON o.bid = l.bid AND o.sid = l.sid AND o.w = l.w
WHERE o.si_id IS NOT NULL AND l.received_qty > 0;
INSERT INTO stock_in_detail (stock_in_id, product_id, quantity, unit_cost, lot_number, expiry_date)
SELECT s.id, p.pid, 10 + CRC32(CONCAT('fr', s.id, p.pid)) % 20, p.cost, NULL, NULL
FROM stock_in s JOIN demo_tmp_prod p ON p.cat IN ('Rau lá tươi', 'Rau tươi', 'Củ, quả tươi', 'Rau sống & Salad')
WHERE s.id > @mx_si AND s.purchase_order_id IS NULL;
UPDATE stock_in s SET total_cost = (SELECT COALESCE(SUM(d.quantity * d.unit_cost), 0) FROM stock_in_detail d WHERE d.stock_in_id = s.id)
WHERE s.id > @mx_si AND s.purchase_order_id IS NULL;
DELETE s FROM stock_in s WHERE s.id > @mx_si AND NOT EXISTS (SELECT 1 FROM stock_in_detail d WHERE d.stock_in_id = s.id);

-- =====================================================================
-- 14. ĐIỀU CHUYỂN GIỮA CHI NHÁNH (khoảng 10 ngày một lần)
-- =====================================================================
SET @b_main = (SELECT bid FROM demo_tmp_branch WHERE k = 1);
SET @b_2    = (SELECT bid FROM demo_tmp_branch WHERE k = 2);
SET @mx = (SELECT COALESCE(MAX(id), 0) FROM stock_transfer);
INSERT INTO stock_transfer (client_request_id, created_at, employee_id, from_branch_id, note, product_id, quantity, to_branch_id)
SELECT NULL, TIMESTAMP(@start + INTERVAL n.i DAY, '14:30:00') + INTERVAL k.k * 3 MINUTE, @mgr,
       IF(MOD(n.i, 30) = 15, @b_2, @b_main), 'Điều chuyển bổ sung hàng bán chạy', p.pid, 6 * (1 + MOD(n.i + k.k, 3)),
       IF(MOD(n.i, 30) = 15, @b_main, @b_2)
FROM demo_tmp_num n
JOIN (SELECT 0 AS k UNION ALL SELECT 1 UNION ALL SELECT 2) k
JOIN (SELECT pid, ROW_NUMBER() OVER (ORDER BY base DESC, pid) AS rk FROM demo_tmp_prod) p ON p.rk = 1 + MOD(n.i * 7 + k.k * 11, 30)
WHERE @b_2 IS NOT NULL AND MOD(n.i, 10) = 5 AND n.i < @demo_days;
INSERT IGNORE INTO demo_seed_rows SELECT 'stock_transfer', id FROM stock_transfer WHERE id > @mx;

-- =====================================================================
-- 15. TỒN KHO CUỐI: theo số ngày đủ bán để có đủ mức cảnh báo
--     ~15% còn 0,5-2 ngày (cần nhập gấp), ~20% còn 3-5 ngày, còn lại 8-20 ngày
-- =====================================================================
INSERT INTO inventory (product_id, branch_id, quantity, min_threshold)
SELECT a.pid, a.bid,
       GREATEST(0, ROUND(a.avg_qty * CASE
            WHEN CRC32(CONCAT('cover', a.bid, '-', a.pid)) % 100 < 15
                 THEN 0.5 + 1.5 * (CRC32(CONCAT('c2', a.bid, '-', a.pid)) % 100) / 100
            WHEN CRC32(CONCAT('cover', a.bid, '-', a.pid)) % 100 < 35
                 THEN 3 + 2 * (CRC32(CONCAT('c2', a.bid, '-', a.pid)) % 100) / 100
            ELSE 8 + 12 * (CRC32(CONCAT('c2', a.bid, '-', a.pid)) % 100) / 100 END)
            + IF(CRC32(CONCAT('cover', a.bid, '-', a.pid)) % 100 < 15,
                 IF(CRC32(CONCAT('c3', a.bid, '-', a.pid)) % 3 = 0, 0, 1),
                 4 + CRC32(CONCAT('c3', a.bid, '-', a.pid)) % 6)),
       GREATEST(3, CEIL(a.avg_qty * 3))
FROM (
  SELECT b.bid, p.pid, COALESCE(q.qty, 0) / 14 AS avg_qty
  FROM demo_tmp_branch b
  CROSS JOIN demo_tmp_prod p
  LEFT JOIN (
    SELECT i.branch_id AS bid, d.product_id AS pid, SUM(d.quantity - d.returned_quantity) AS qty
    FROM invoice i JOIN invoice_detail d ON d.invoice_id = i.id
    WHERE i.id > @mx_inv AND i.created_at >= @today - INTERVAL 14 DAY AND i.created_at < @today AND i.status <> 'REFUNDED'
    GROUP BY i.branch_id, d.product_id
  ) q ON q.bid = b.bid AND q.pid = p.pid
) a
ON DUPLICATE KEY UPDATE quantity = VALUES(quantity), min_threshold = VALUES(min_threshold);

-- Hạn dùng: 8 mặt hàng ở chi nhánh chính có lô sắp hết hạn (2 mặt hàng đã quá hạn) để demo trang Hạn sử dụng.
-- Tồn hiện tại được coi là thuộc lô hạn xa nhất (FEFO), nên dời hạn mọi lô của mặt hàng đó về trước mốc chọn.
DROP TABLE IF EXISTS demo_tmp_exp;
CREATE TABLE demo_tmp_exp AS
SELECT z.pid, IF(z.rk <= 2, @today - INTERVAL z.rk DAY, @today + INTERVAL (2 * z.rk) DAY) AS target
FROM (
  SELECT p.pid, ROW_NUMBER() OVER (ORDER BY CRC32(CONCAT('exp', p.pid))) AS rk
  FROM demo_tmp_prod p
  JOIN inventory inv ON inv.product_id = p.pid AND inv.branch_id = @b_main AND inv.quantity BETWEEN 3 AND 60
  WHERE p.shelf_days IS NOT NULL AND p.shelf_days <= 210
    AND EXISTS (SELECT 1 FROM stock_in s JOIN stock_in_detail d ON d.stock_in_id = s.id
                WHERE s.id > @mx_si AND s.branch_id = @b_main AND d.product_id = p.pid)
) z WHERE z.rk <= 8;
UPDATE stock_in_detail d
JOIN stock_in s ON s.id = d.stock_in_id AND s.id > @mx_si AND s.branch_id = @b_main
JOIN demo_tmp_exp e ON e.pid = d.product_id
JOIN (SELECT d2.product_id, MAX(s2.created_at) AS last_at FROM stock_in s2 JOIN stock_in_detail d2 ON d2.stock_in_id = s2.id
      WHERE s2.id > @mx_si AND s2.branch_id = @b_main GROUP BY d2.product_id) l ON l.product_id = d.product_id
SET d.expiry_date = e.target - INTERVAL DATEDIFF(l.last_at, s.created_at) DAY;
-- Tồn của các mặt hàng này không vượt lô mới nhất, để chỉ lô đó còn hàng
UPDATE inventory inv
JOIN demo_tmp_exp e ON e.pid = inv.product_id
JOIN (SELECT d2.product_id, d2.quantity, ROW_NUMBER() OVER (PARTITION BY d2.product_id ORDER BY s2.created_at DESC) AS rn
      FROM stock_in s2 JOIN stock_in_detail d2 ON d2.stock_in_id = s2.id
      WHERE s2.id > @mx_si AND s2.branch_id = @b_main) l ON l.product_id = inv.product_id AND l.rn = 1
SET inv.quantity = LEAST(inv.quantity, l.quantity)
WHERE inv.branch_id = @b_main;
DROP TABLE demo_tmp_exp;

-- Đơn nháp lập từ AI hôm nay cho mặt hàng sắp hết (chưa gửi NCC)
SET @mx = (SELECT COALESCE(MAX(id), 0) FROM purchase_order);
INSERT INTO purchase_order (branch_id, supplier_id, supplier_name, status, source, note, expected_date, created_by, created_at)
SELECT x.bid, x.sup_id, s.name, 'DRAFT', 'FORECAST', 'Lập từ đề xuất AI dự báo 7 ngày', NULL, @mgr,
       TIMESTAMP(@today, '07:45:00') + INTERVAL x.sup_id MINUTE
FROM (
  SELECT inv.branch_id AS bid, p.sup_id, COUNT(*) AS n,
         ROW_NUMBER() OVER (PARTITION BY inv.branch_id ORDER BY COUNT(*) DESC, p.sup_id) AS rk
  FROM inventory inv JOIN demo_tmp_prod p ON p.pid = inv.product_id
  WHERE p.sup_id IS NOT NULL AND inv.quantity <= inv.min_threshold AND inv.branch_id IN (SELECT bid FROM demo_tmp_branch)
  GROUP BY inv.branch_id, p.sup_id
) x JOIN supplier s ON s.id = x.sup_id
WHERE x.rk = 1;
INSERT IGNORE INTO demo_seed_rows SELECT 'purchase_order', id FROM purchase_order WHERE id > @mx;
INSERT INTO purchase_order_line (purchase_order_id, product_id, product_name, barcode, category, ordered_qty, received_qty,
                                 suggested_qty, forecast_note, unit_cost)
SELECT o.id, p.pid, p.pname, p.barcode, p.cat, GREATEST(p.pack, CEIL(inv.min_threshold * 2 / p.pack) * p.pack), 0,
       GREATEST(p.pack, CEIL(inv.min_threshold * 2 / p.pack) * p.pack),
       CONCAT('Tồn ', inv.quantity, ', dưới điểm đặt hàng lại ', inv.min_threshold), p.cost
FROM purchase_order o
JOIN demo_tmp_prod p ON p.sup_id = o.supplier_id
JOIN inventory inv ON inv.product_id = p.pid AND inv.branch_id = o.branch_id AND inv.quantity <= inv.min_threshold
WHERE o.id > @mx;

-- =====================================================================
-- 16. KIỂM KÊ: cuối mỗi tháng đã duyệt + 1 phiếu chờ duyệt hôm qua
-- =====================================================================
CREATE TABLE demo_tmp_sc AS
SELECT b.bid, LAST_DAY(@start + INTERVAL n.i MONTH) AS dt, CAST('APPROVED' AS CHAR(20)) AS status,
       CAST(CONCAT('Kiểm kê định kỳ cuối tháng ', DATE_FORMAT(LAST_DAY(@start + INTERVAL n.i MONTH), '%m/%Y')) AS CHAR(255)) AS note,
       CAST(NULL AS SIGNED) AS sc_id
FROM demo_tmp_branch b JOIN demo_tmp_num n ON n.i < 8
WHERE LAST_DAY(@start + INTERVAL n.i MONTH) < @today;
INSERT INTO demo_tmp_sc SELECT @b_main, @today - INTERVAL 1 DAY, 'SUBMITTED', 'Kiểm kê nhóm sữa và đồ uống', NULL;

SET @mx_sc = (SELECT COALESCE(MAX(id), 0) FROM stock_count);
INSERT INTO stock_count (branch_id, created_at, created_by, note, status, submitted_at, approved_at, approved_by, variance_value)
SELECT bid, TIMESTAMP(dt, '21:15:00'), (SELECT emp_id FROM demo_tmp_branch b WHERE b.bid = c.bid), note, status,
       TIMESTAMP(dt, '22:05:00'), IF(status = 'APPROVED', TIMESTAMP(dt + INTERVAL 1 DAY, '08:10:00'), NULL),
       IF(status = 'APPROVED', @mgr, NULL), NULL
FROM demo_tmp_sc c ORDER BY dt, bid;
INSERT IGNORE INTO demo_seed_rows SELECT 'stock_count', id FROM stock_count WHERE id > @mx_sc;
UPDATE demo_tmp_sc c JOIN stock_count s ON s.id > @mx_sc AND s.branch_id = c.bid AND s.created_at = TIMESTAMP(c.dt, '21:15:00')
SET c.sc_id = s.id;

INSERT INTO stock_count_line (stock_count_id, product_id, product_name, barcode, system_qty, counted_qty, reason, unit_cost)
SELECT z.sc_id, z.pid, z.pname, z.barcode, z.sys_qty,
       GREATEST(0, z.sys_qty + z.diff), IF(z.diff = 0, NULL, z.reason), z.cost
FROM (
  SELECT c.sc_id, p.pid, p.pname, p.barcode, p.cost,
         IF(c.status = 'SUBMITTED', COALESCE(inv.quantity, 0), 8 + CRC32(CONCAT('sq', c.sc_id, p.pid)) % 40) AS sys_qty,
         CASE WHEN CRC32(CONCAT('df', c.sc_id, p.pid)) % 100 < 7 THEN -1
              WHEN CRC32(CONCAT('df', c.sc_id, p.pid)) % 100 < 11 THEN -2
              WHEN CRC32(CONCAT('df', c.sc_id, p.pid)) % 100 < 13 THEN -3
              WHEN CRC32(CONCAT('df', c.sc_id, p.pid)) % 100 < 15 THEN 1 ELSE 0 END AS diff,
         ELT(1 + CRC32(CONCAT('rs', c.sc_id, p.pid)) % 4, 'DAMAGED', 'LOST', 'EXPIRED', 'MISCOUNT') AS reason,
         ROW_NUMBER() OVER (PARTITION BY c.sc_id ORDER BY CRC32(CONCAT('pk', c.sc_id, p.pid))) AS rn
  FROM demo_tmp_sc c
  JOIN demo_tmp_prod p ON c.status = 'APPROVED' OR p.cat LIKE 'Sữa%' OR p.cat LIKE 'Nước%'
  LEFT JOIN inventory inv ON inv.product_id = p.pid AND inv.branch_id = c.bid
) z
WHERE z.rn <= 30;
-- Số tăng thì lý do là đếm nhầm lần trước
UPDATE stock_count_line l JOIN stock_count c ON c.id = l.stock_count_id AND c.id > @mx_sc
SET l.reason = 'MISCOUNT' WHERE l.counted_qty > l.system_qty;
UPDATE stock_count c SET variance_value = (
  SELECT COALESCE(SUM((l.counted_qty - l.system_qty) * l.unit_cost), 0) FROM stock_count_line l WHERE l.stock_count_id = c.id)
WHERE c.id > @mx_sc AND c.status = 'APPROVED';

-- =====================================================================
-- 17. NHẬT KÝ HỆ THỐNG
-- =====================================================================
SET @mx = (SELECT COALESCE(MAX(id), 0) FROM audit_log);
INSERT INTO audit_log (action, actor_id, actor_role, actor_username, branch_id, created_at, entity_id, entity_type, ip_address, summary)
SELECT 'SHIFT_OPEN', t.emp_id, 'STAFF', b.emp_user, t.bid, t.opened_at, t.sess_id, 'SHIFT', CONCAT('192.168.1.', 10 + b.k),
       CONCAT(s.ws_name, ' - ', t.sname, ', tiền đầu ca 1.000.000đ')
FROM demo_tmp_sess t JOIN demo_tmp_branch b ON b.bid = t.bid JOIN demo_tmp_shift s ON s.bid = t.bid AND s.ws_id = t.ws_id;
INSERT INTO audit_log (action, actor_id, actor_role, actor_username, branch_id, created_at, entity_id, entity_type, ip_address, summary)
SELECT IF(ss.cash_difference = 0, 'SHIFT_CLOSE', 'SHIFT_CASH_VARIANCE'), t.emp_id, 'STAFF', b.emp_user, t.bid, ss.closed_at, ss.id, 'SHIFT',
       CONCAT('192.168.1.', 10 + b.k),
       CONCAT('Ca #', ss.id, ': tiền mặt dự kiến ', FORMAT(ss.expected_cash, 0, 'vi_VN'), 'đ, đếm được ',
              FORMAT(ss.closing_cash, 0, 'vi_VN'), 'đ, chênh lệch ', FORMAT(ss.cash_difference, 0, 'vi_VN'), 'đ',
              IF(ss.cash_difference = 0, '', CONCAT(' (QR 0đ, thẻ 0đ) - lý do: ', ss.difference_reason)))
FROM demo_tmp_sess t JOIN shift_session ss ON ss.id = t.sess_id JOIN demo_tmp_branch b ON b.bid = t.bid;
INSERT INTO audit_log (action, actor_id, actor_role, actor_username, branch_id, created_at, entity_id, entity_type, ip_address, summary)
SELECT 'SHIFT_REVIEW', @mgr, 'MANAGER', 'quanly', ss.branch_id, ss.reviewed_at, ss.id, 'SHIFT', '192.168.1.5',
       CONCAT('Duyệt ca #', ss.id, ' lệch ', FORMAT(ss.cash_difference, 0, 'vi_VN'), 'đ: ', ss.review_note)
FROM shift_session ss JOIN demo_tmp_sess t ON t.sess_id = ss.id WHERE ss.reviewed_at IS NOT NULL;
INSERT INTO audit_log (action, actor_id, actor_role, actor_username, branch_id, created_at, entity_id, entity_type, ip_address, summary)
SELECT 'PURCHASE_ORDER', @mgr, 'MANAGER', 'quanly', o.branch_id, o.sent_at, o.id, 'PURCHASE_ORDER', '192.168.1.5',
       CONCAT('Gửi đơn đặt hàng #', o.id, ' cho NCC ', o.supplier_name)
FROM purchase_order o WHERE o.id > @mx_po AND o.sent_at IS NOT NULL;
INSERT INTO audit_log (action, actor_id, actor_role, actor_username, branch_id, created_at, entity_id, entity_type, ip_address, summary)
SELECT 'STOCK_IN', @mgr, 'MANAGER', 'quanly', s.branch_id, s.created_at, s.id, 'STOCK_IN', '192.168.1.5',
       CONCAT('Phiếu nhập #', s.id, IF(s.purchase_order_id IS NULL, '', CONCAT(' (đơn đặt #', s.purchase_order_id, ')')), ': ',
              (SELECT COUNT(*) FROM stock_in_detail d WHERE d.stock_in_id = s.id), ' mặt hàng, tổng ',
              FORMAT(s.total_cost, 0, 'vi_VN'), 'đ', IF(sp.name IS NULL, '', CONCAT(' - NCC ', sp.name)))
FROM stock_in s LEFT JOIN supplier sp ON sp.id = s.supplier_id WHERE s.id > @mx_si;
INSERT INTO audit_log (action, actor_id, actor_role, actor_username, branch_id, created_at, entity_id, entity_type, ip_address, summary)
SELECT 'REFUND', x.created_by, 'STAFF', b.emp_user, x.branch_id, x.created_at, x.invoice_id, 'INVOICE', CONCAT('192.168.1.', 10 + b.k),
       CONCAT('Hóa đơn #', x.invoice_id, IF(i.status = 'REFUNDED', ' hoàn hết', ' hoàn một phần'), ' - hoàn ',
              FORMAT(x.refund_amount, 0, 'vi_VN'), 'đ - lý do: ', x.reason, ' (quản lý #', @mgr, ' duyệt PIN)')
FROM invoice_return x JOIN demo_tmp_ret r ON r.ret_id = x.id JOIN invoice i ON i.id = x.invoice_id JOIN demo_tmp_branch b ON b.bid = x.branch_id;
INSERT INTO audit_log (action, actor_id, actor_role, actor_username, branch_id, created_at, entity_id, entity_type, ip_address, summary)
SELECT 'DISCOUNT_APPROVED', @mgr, 'MANAGER', 'quanly', i.branch_id, i.created_at, i.id, 'INVOICE', '192.168.1.5',
       CONCAT('Duyệt giảm tay ', FORMAT(i.discount, 0, 'vi_VN'), 'đ cho hóa đơn - lý do: ', i.discount_reason)
FROM invoice i WHERE i.id > @mx_inv AND i.discount_approved_by IS NOT NULL;
INSERT INTO audit_log (action, actor_id, actor_role, actor_username, branch_id, created_at, entity_id, entity_type, ip_address, summary)
SELECT 'STOCK_ADJUSTMENT', @mgr, 'MANAGER', 'quanly', c.branch_id, c.approved_at, c.id, 'STOCK_COUNT', '192.168.1.5',
       CONCAT('Duyệt phiếu kiểm kê #', c.id, ': ',
              COALESCE((SELECT GROUP_CONCAT(CONCAT(l.product_name, ' ', IF(l.counted_qty > l.system_qty, '+', ''), l.counted_qty - l.system_qty)
                                            SEPARATOR ', ')
                        FROM stock_count_line l WHERE l.stock_count_id = c.id AND l.counted_qty <> l.system_qty), 'không chênh lệch'),
              ' - giá trị ', FORMAT(c.variance_value, 0, 'vi_VN'), 'đ')
FROM stock_count c WHERE c.id > @mx_sc AND c.status = 'APPROVED';
INSERT INTO audit_log (action, actor_id, actor_role, actor_username, branch_id, created_at, entity_id, entity_type, ip_address, summary)
SELECT 'PROMOTION_CHANGE', @mgr, 'MANAGER', 'quanly', p.branch_id, p.created_at, p.id, 'PROMOTION', '192.168.1.5',
       CONCAT('Tạo khuyến mãi "', p.name, '"')
FROM promotion p JOIN demo_seed_rows r ON r.tbl = 'promotion' AND r.row_id = p.id;
INSERT INTO audit_log (action, actor_id, actor_role, actor_username, branch_id, created_at, entity_id, entity_type, ip_address, summary)
SELECT 'STOCK_TRANSFER', @mgr, 'MANAGER', 'quanly', t.from_branch_id, t.created_at, t.product_id, 'PRODUCT', '192.168.1.5',
       CONCAT('Chuyển ', t.quantity, ' SP #', t.product_id, ' từ chi nhánh ', t.from_branch_id, ' sang ', t.to_branch_id)
FROM stock_transfer t JOIN demo_seed_rows r ON r.tbl = 'stock_transfer' AND r.row_id = t.id;
INSERT INTO audit_log (action, actor_id, actor_role, actor_username, branch_id, created_at, entity_id, entity_type, ip_address, summary)
SELECT 'LOGIN_FAILED', NULL, NULL, b.emp_user, NULL, TIMESTAMP(@start + INTERVAL n.i DAY, '06:02:00'), NULL, 'USER',
       CONCAT('192.168.1.', 10 + b.k), CONCAT('Đăng nhập sai mật khẩu: ', b.emp_user)
FROM demo_tmp_branch b JOIN demo_tmp_num n ON MOD(n.i * 7 + b.k, 23) = 0 AND n.i < @demo_days;
INSERT IGNORE INTO demo_seed_rows SELECT 'audit_log', id FROM audit_log WHERE id > @mx;

-- =====================================================================
-- 18. DỌN BẢNG TẠM, TÓM TẮT
-- =====================================================================
DROP TABLE IF EXISTS demo_tmp_num, demo_tmp_branch, demo_tmp_staff, demo_tmp_shift, demo_tmp_day, demo_tmp_hour,
  demo_tmp_prod, demo_tmp_week, demo_tmp_pslot, demo_tmp_cust, demo_tmp_cslot, demo_tmp_sess, demo_tmp_inv,
  demo_tmp_line, demo_tmp_promo, demo_tmp_ret, demo_tmp_ret_line, demo_tmp_move, demo_tmp_sold, demo_tmp_po, demo_tmp_po_line,
  demo_tmp_sc, demo_tmp_names;

SELECT tbl AS bang, COUNT(*) AS so_dong_mau FROM demo_seed_rows GROUP BY tbl ORDER BY tbl;
SELECT COUNT(*) AS so_hoa_don, FORMAT(SUM(total_amount - COALESCE(refunded_amount, 0)), 0) AS doanh_thu_thuan,
       MIN(DATE(created_at)) AS tu_ngay, MAX(created_at) AS den_luc
FROM invoice i JOIN demo_seed_rows r ON r.tbl = 'invoice' AND r.row_id = i.id;
