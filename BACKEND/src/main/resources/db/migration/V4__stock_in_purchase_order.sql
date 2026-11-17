-- Phiếu nhập sinh ra từ nhận hàng theo đơn đặt hàng ghi lại mã đơn gốc, để lịch sử nhập kho truy ngược được về đơn.
-- Phiếu nhập tự do ở trang Kho hàng để trống cột này.
ALTER TABLE `stock_in` ADD COLUMN `purchase_order_id` bigint DEFAULT NULL;
CREATE INDEX `idx_stock_in_purchase_order` ON `stock_in` (`purchase_order_id`);

-- Phiếu nhập cũ tạo từ nhận hàng có ghi chú "Nhận hàng theo đơn đặt #<id>": điền lại mã đơn
UPDATE `stock_in`
SET `purchase_order_id` = CAST(SUBSTRING_INDEX(SUBSTRING(`note`, LOCATE('#', `note`) + 1), ' ', 1) AS UNSIGNED)
WHERE `purchase_order_id` IS NULL AND `note` LIKE 'Nhận hàng theo đơn đặt #%';
