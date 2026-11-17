-- =====================================================================
-- Dữ liệu bán hàng mẫu 90 ngày cho demo (Dự báo AI, Lập đơn từ đề xuất AI,
-- Báo cáo, Xuất Excel).
--
-- Cách chạy (Docker, ở thư mục gốc repo):
--   docker exec -i pos-mysql mysql -uroot -p"$DB_ROOT_PASSWORD" pos_inventory < data/demo/lich_su_ban_90_ngay.sql
--
-- Script này:
--   * CHỈ THÊM hóa đơn và ca làm mẫu (đánh dấu note/opening_note = '[DEMO]').
--     Không xóa sản phẩm, tài khoản, khách hàng hay hóa đơn thật.
--   * Chạy lại nhiều lần được: lần sau sẽ xóa dữ liệu '[DEMO]' cũ rồi tạo lại.
--   * Đặt lại số tồn kho của các sản phẩm được chọn để có đủ mức
--     "cần nhập gấp / sắp hết / còn đủ" khi demo.
--   * Xóa bảng cache dự báo (forecast_result) để trang Dự báo tính lại ngay,
--     không cần khởi động lại backend.
--
-- Yêu cầu: backend đã chạy ít nhất 1 lần (đã có bảng, chi nhánh, ca làm,
-- tài khoản và sản phẩm). Hôm nay không có dữ liệu, chuỗi kết thúc hôm qua.
-- Muốn đổi số ngày hoặc số sản phẩm: sửa @demo_days và @demo_max_products.
-- =====================================================================

SET NAMES utf8mb4;
SET @demo_days         = 90;   -- số ngày lịch sử
SET @demo_max_products = 120;  -- số sản phẩm tối đa có lịch sử bán
SET SESSION cte_max_recursion_depth = 10000;

-- ---------------------------------------------------------------------
-- 1. Dọn dữ liệu demo cũ (nếu đã chạy trước đó)
-- ---------------------------------------------------------------------
DELETE d FROM invoice_detail d JOIN invoice i ON i.id = d.invoice_id WHERE i.note = '[DEMO]';
DELETE FROM invoice WHERE note = '[DEMO]';
DELETE FROM shift_session WHERE opening_note = '[DEMO]';
DELETE FROM forecast_result;

DROP TABLE IF EXISTS demo_tmp_prod, demo_tmp_branch, demo_tmp_shift, demo_tmp_day,
                     demo_tmp_qty, demo_tmp_line, demo_tmp_inv;

-- ---------------------------------------------------------------------
-- 2. Chọn sản phẩm và gán "tính cách" bán hàng cố định cho từng sản phẩm
--    (băm theo id nên chạy lại vẫn ra cùng nhóm bán nhanh/chậm)
-- ---------------------------------------------------------------------
CREATE TABLE demo_tmp_prod AS
SELECT p.id AS pid,
       p.sale_price AS price,
       COALESCE(p.cost_price, ROUND(p.sale_price * 0.8, 0)) AS cost,
       CRC32(CONCAT('speed', p.id)) % 100 AS h_speed,
       CRC32(CONCAT('rate',  p.id)) % 1000 / 1000 AS h_rate,
       CRC32(CONCAT('trend', p.id)) % 100 AS h_trend,
       CRC32(CONCAT('wknd',  p.id)) % 1000 / 1000 AS h_wknd,
       CAST(0 AS DOUBLE) AS base, CAST(0 AS DOUBLE) AS trend, CAST(1 AS DOUBLE) AS wknd
FROM product p
WHERE p.active = 1 AND p.sale_price > 0
ORDER BY p.id;

-- Giới hạn số sản phẩm
CREATE TABLE demo_tmp_keep AS SELECT pid FROM demo_tmp_prod ORDER BY pid LIMIT 0;
SET @sql = CONCAT('INSERT INTO demo_tmp_keep SELECT pid FROM demo_tmp_prod ORDER BY pid LIMIT ', @demo_max_products);
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
DELETE FROM demo_tmp_prod WHERE pid NOT IN (SELECT pid FROM demo_tmp_keep);
DROP TABLE demo_tmp_keep;

UPDATE demo_tmp_prod SET
  -- ~15% bán nhanh (8-16/ngày), ~35% trung bình (2-6/ngày), ~50% bán chậm (0.2-1.2/ngày)
  base  = CASE WHEN h_speed < 15 THEN 8   + 8   * h_rate
               WHEN h_speed < 50 THEN 2   + 4   * h_rate
               ELSE                   0.2 + 1.0 * h_rate END,
  -- ~12% đang tăng (+80% sau 90 ngày), ~6% đang giảm (-40%)
  trend = CASE WHEN h_trend < 12 THEN 0.8
               WHEN h_trend < 18 THEN -0.4
               ELSE 0 END,
  -- cuối tuần bán nhiều hơn 0-50%
  wknd  = 1 + 0.5 * h_wknd;

-- ---------------------------------------------------------------------
-- 3. Chi nhánh, nhân viên đứng ca, ca làm
-- ---------------------------------------------------------------------
CREATE TABLE demo_tmp_branch AS
SELECT b.id AS bid,
       CAST(CASE WHEN b.id = (SELECT MIN(id) FROM branch WHERE active = 1) THEN 1.0 ELSE 0.7 END AS DOUBLE) AS factor,
       CAST(NULL AS SIGNED) AS emp_id,
       CAST(NULL AS CHAR(100)) AS emp_name
FROM branch b
WHERE b.active = 1;

-- Ưu tiên nhân viên (không phải ADMIN/MANAGER) thuộc chi nhánh, nếu không có thì lấy ADMIN đầu tiên
UPDATE demo_tmp_branch t SET emp_id = (
  SELECT u.id FROM users u JOIN user_branch ub ON ub.user_id = u.id
  WHERE ub.branch_id = t.bid AND u.active = 1
  ORDER BY (u.role IN ('ADMIN', 'MANAGER')), u.id LIMIT 1);
UPDATE demo_tmp_branch t SET emp_id = (
  SELECT u.id FROM users u WHERE u.active = 1
  ORDER BY (u.role <> 'ADMIN'), u.id LIMIT 1)
WHERE emp_id IS NULL;
UPDATE demo_tmp_branch t JOIN users u ON u.id = t.emp_id SET t.emp_name = u.full_name;

-- Ca riêng của chi nhánh nếu có, không thì dùng ca mặc định (branch_id NULL)
CREATE TABLE demo_tmp_shift AS
SELECT b.bid, w.id AS ws_id, w.start_time, w.end_time,
       ROW_NUMBER() OVER (PARTITION BY b.bid ORDER BY w.sort_order, w.start_time) AS k
FROM demo_tmp_branch b
JOIN work_shift w ON w.active = 1
 AND ( w.branch_id = b.bid
       OR (w.branch_id IS NULL AND NOT EXISTS (
             SELECT 1 FROM work_shift w2 WHERE w2.active = 1 AND w2.branch_id = b.bid)) )
WHERE w.end_time > w.start_time;

ALTER TABLE demo_tmp_branch ADD COLUMN nshift INT;
UPDATE demo_tmp_branch b SET nshift = (SELECT COUNT(*) FROM demo_tmp_shift s WHERE s.bid = b.bid);
DELETE FROM demo_tmp_branch WHERE nshift = 0 OR emp_id IS NULL;

-- ---------------------------------------------------------------------
-- 4. Các ngày lịch sử (từ hôm qua lùi về @demo_days ngày)
-- ---------------------------------------------------------------------
CREATE TABLE demo_tmp_day (d INT PRIMARY KEY, dt DATE, wknd TINYINT);
INSERT INTO demo_tmp_day (d, dt, wknd)
WITH RECURSIVE n AS (SELECT 1 AS d UNION ALL SELECT d + 1 FROM n WHERE d < @demo_days)
SELECT d, CURDATE() - INTERVAL d DAY,
       DAYOFWEEK(CURDATE() - INTERVAL d DAY) IN (1, 7)
FROM n;

-- ---------------------------------------------------------------------
-- 5. Số lượng bán mỗi sản phẩm / chi nhánh / ngày (có xu hướng + cuối tuần + nhiễu)
-- ---------------------------------------------------------------------
CREATE TABLE demo_tmp_qty AS
SELECT x.d, x.bid, x.pid, x.qty,
       1 + FLOOR(RAND() * 3) AS chunk   -- mỗi dòng hóa đơn 1-3 đơn vị
FROM (
  SELECT dd.d, b.bid, p.pid,
         FLOOR( p.base * b.factor
                * (1 + p.trend * (@demo_days - dd.d) / @demo_days)
                * IF(dd.wknd, p.wknd, 1)
                * (0.55 + 0.9 * RAND())
                + RAND() ) AS qty
  FROM demo_tmp_day dd
  CROSS JOIN demo_tmp_branch b
  CROSS JOIN demo_tmp_prod p
) x
WHERE x.qty > 0;

-- Tách thành các dòng hóa đơn
CREATE TABLE demo_tmp_line AS
WITH RECURSIVE n AS (SELECT 1 AS i UNION ALL SELECT i + 1 FROM n WHERE i < 100)
SELECT q.d, q.bid, q.pid,
       LEAST(q.chunk, q.qty - (n.i - 1) * q.chunk) AS qty,
       RAND() AS r
FROM demo_tmp_qty q
JOIN n ON n.i <= CEIL(q.qty / q.chunk);

-- Gom dòng vào hóa đơn: trung bình ~3 dòng/hóa đơn, số dòng ngẫu nhiên
ALTER TABLE demo_tmp_line ADD COLUMN grp INT;
CREATE TABLE demo_tmp_inv AS
SELECT d, bid, COUNT(*) AS nline, CEIL(COUNT(*) / 3) AS ninv
FROM demo_tmp_line GROUP BY d, bid;
UPDATE demo_tmp_line l JOIN demo_tmp_inv v ON v.d = l.d AND v.bid = l.bid
SET l.grp = FLOOR(RAND() * v.ninv);
DROP TABLE demo_tmp_inv;

-- Mỗi hóa đơn: ca ngẫu nhiên, giờ ngẫu nhiên trong ca, phương thức thanh toán
CREATE TABLE demo_tmp_inv AS
SELECT g.d, g.bid, g.grp,
       CONCAT('DEMO-', g.d, '-', g.bid, '-', g.grp) AS req_id,
       1 + FLOOR(RAND() * b.nshift) AS k,
       RAND() AS r_time,
       RAND() AS r_pay,
       CAST(NULL AS SIGNED) AS ws_id,
       CAST(NULL AS DATETIME) AS created_at,
       CAST(NULL AS CHAR(10)) AS pm
FROM (SELECT DISTINCT d, bid, grp FROM demo_tmp_line) g
JOIN demo_tmp_branch b ON b.bid = g.bid;

UPDATE demo_tmp_inv v
JOIN demo_tmp_shift s ON s.bid = v.bid AND s.k = v.k
JOIN demo_tmp_day dd ON dd.d = v.d
SET v.ws_id = s.ws_id,
    v.created_at = TIMESTAMP(dd.dt, s.start_time)
                   + INTERVAL FLOOR(v.r_time * (TIME_TO_SEC(s.end_time) - TIME_TO_SEC(s.start_time) - 60)) SECOND,
    v.pm = CASE WHEN v.r_pay < 0.6 THEN 'CASH' WHEN v.r_pay < 0.9 THEN 'VIETQR' ELSE 'CARD' END;

-- ---------------------------------------------------------------------
-- 6. Ghi ca làm (đã đóng) cho mỗi ngày / chi nhánh / ca
-- ---------------------------------------------------------------------
INSERT INTO shift_session
  (branch_id, work_shift_id, shift_date, employee_id, employee_name, status,
   opened_at, closed_at, closed_by, opening_cash, opening_note,
   cash_sales, vietqr_sales, card_sales, non_cash_sales, invoice_count,
   cash_refunds, vietqr_refunds, card_refunds, non_cash_refunds, refunded_count,
   expenses, drops, float_in)
SELECT b.bid, s.ws_id, dd.dt, b.emp_id, b.emp_name, 'CLOSED',
       TIMESTAMP(dd.dt, s.start_time), TIMESTAMP(dd.dt, s.end_time), b.emp_id,
       1000000, '[DEMO]',
       0, 0, 0, 0, 0,
       0, 0, 0, 0, 0,
       0, 0, 0
FROM demo_tmp_day dd
CROSS JOIN demo_tmp_branch b
JOIN demo_tmp_shift s ON s.bid = b.bid;

-- ---------------------------------------------------------------------
-- 7. Ghi hóa đơn và chi tiết
-- ---------------------------------------------------------------------
INSERT INTO invoice
  (client_request_id, created_at, branch_id, employee_id, shift_session_id, note,
   payment_method, status, subtotal, discount, promotion_discount, points_discount,
   points_earned, points_redeemed, refunded_amount, total_amount)
SELECT v.req_id, v.created_at, v.bid, b.emp_id, ss.id, '[DEMO]',
       v.pm, 'PAID', 0, 0, 0, 0,
       0, 0, 0, 0
FROM demo_tmp_inv v
JOIN demo_tmp_branch b ON b.bid = v.bid
JOIN demo_tmp_day dd ON dd.d = v.d
JOIN shift_session ss ON ss.opening_note = '[DEMO]' AND ss.branch_id = v.bid
                     AND ss.shift_date = dd.dt AND ss.work_shift_id = v.ws_id;

INSERT INTO invoice_detail (invoice_id, product_id, quantity, returned_quantity,
                            unit_price, cost_price, total_price)
SELECT i.id, l.pid, SUM(l.qty), 0, p.price, p.cost, SUM(l.qty) * p.price
FROM demo_tmp_line l
JOIN demo_tmp_prod p ON p.pid = l.pid
JOIN invoice i ON i.client_request_id = CONCAT('DEMO-', l.d, '-', l.bid, '-', l.grp)
GROUP BY i.id, l.pid, p.price, p.cost;

-- Tổng tiền hóa đơn, tiền khách đưa (tiền mặt làm tròn lên 10.000đ) và tiền thối
UPDATE invoice i
JOIN (SELECT invoice_id, SUM(total_price) AS s FROM invoice_detail GROUP BY invoice_id) t
  ON t.invoice_id = i.id
SET i.subtotal = t.s,
    i.total_amount = t.s,
    i.received_amount = IF(i.payment_method = 'CASH', CEIL(t.s / 10000) * 10000, t.s),
    i.change_amount   = IF(i.payment_method = 'CASH', CEIL(t.s / 10000) * 10000 - t.s, 0)
WHERE i.note = '[DEMO]';

-- Tổng kết ca: khớp tiền, không chênh lệch
UPDATE shift_session ss
LEFT JOIN (
  SELECT shift_session_id AS sid,
         SUM(IF(payment_method = 'CASH',   total_amount, 0)) AS cash,
         SUM(IF(payment_method = 'VIETQR', total_amount, 0)) AS qr,
         SUM(IF(payment_method = 'CARD',   total_amount, 0)) AS card,
         COUNT(*) AS cnt
  FROM invoice WHERE note = '[DEMO]' GROUP BY shift_session_id
) t ON t.sid = ss.id
SET ss.cash_sales      = COALESCE(t.cash, 0),
    ss.vietqr_sales    = COALESCE(t.qr, 0),
    ss.card_sales      = COALESCE(t.card, 0),
    ss.non_cash_sales  = COALESCE(t.qr, 0) + COALESCE(t.card, 0),
    ss.invoice_count   = COALESCE(t.cnt, 0),
    ss.expected_cash   = ss.opening_cash + COALESCE(t.cash, 0),
    ss.closing_cash    = ss.opening_cash + COALESCE(t.cash, 0),
    ss.cash_difference = 0,
    ss.counted_vietqr  = COALESCE(t.qr, 0),
    ss.counted_card    = COALESCE(t.card, 0),
    ss.vietqr_difference = 0,
    ss.card_difference = 0,
    ss.left_in_drawer  = ss.opening_cash,
    ss.final_drop      = COALESCE(t.cash, 0)
WHERE ss.opening_note = '[DEMO]';

-- ---------------------------------------------------------------------
-- 8. Đặt tồn kho cuối kỳ theo số ngày đủ bán (để demo có đủ mức cảnh báo)
--    ~15% còn 0,5-2 ngày (cần nhập gấp), ~20% còn 3-5 ngày, còn lại 8-20 ngày
-- ---------------------------------------------------------------------
INSERT INTO inventory (product_id, branch_id, quantity, min_threshold)
SELECT a.pid, a.bid,
       GREATEST(0, ROUND(a.avg_qty * CASE
            WHEN CRC32(CONCAT('cover', a.bid, '-', a.pid)) % 100 < 15
                 THEN 0.5 + 1.5 * (CRC32(CONCAT('c2', a.bid, '-', a.pid)) % 100) / 100
            WHEN CRC32(CONCAT('cover', a.bid, '-', a.pid)) % 100 < 35
                 THEN 3 + 2 * (CRC32(CONCAT('c2', a.bid, '-', a.pid)) % 100) / 100
            ELSE 8 + 12 * (CRC32(CONCAT('c2', a.bid, '-', a.pid)) % 100) / 100 END)),
       10
FROM (
  SELECT b.bid, p.pid, COALESCE(SUM(q.qty), 0) / 14 AS avg_qty
  FROM demo_tmp_branch b
  CROSS JOIN demo_tmp_prod p
  LEFT JOIN demo_tmp_qty q ON q.bid = b.bid AND q.pid = p.pid AND q.d <= 14
  GROUP BY b.bid, p.pid
) a
ON DUPLICATE KEY UPDATE quantity = VALUES(quantity);

-- ---------------------------------------------------------------------
-- 9. Dọn bảng tạm và in tóm tắt
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS demo_tmp_prod, demo_tmp_branch, demo_tmp_shift, demo_tmp_day,
                     demo_tmp_qty, demo_tmp_line, demo_tmp_inv;

SELECT COUNT(DISTINCT i.branch_id)          AS so_chi_nhanh,
       COUNT(DISTINCT d.product_id)         AS so_san_pham,
       COUNT(DISTINCT i.id)                 AS so_hoa_don,
       SUM(d.quantity)                      AS tong_so_luong,
       FORMAT(SUM(d.total_price), 0)        AS tong_doanh_thu,
       MIN(DATE(i.created_at))              AS tu_ngay,
       MAX(DATE(i.created_at))              AS den_ngay
FROM invoice i JOIN invoice_detail d ON d.invoice_id = i.id
WHERE i.note = '[DEMO]';
