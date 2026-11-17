-- Danh mục sản phẩm nhà cung cấp cung cấp: lập đơn từ đề xuất AI chỉ đưa mặt hàng đúng danh mục vào đơn của NCC.
CREATE TABLE IF NOT EXISTS `supplier_category` (
  `supplier_id` bigint NOT NULL,
  `category` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`supplier_id`, `category`),
  CONSTRAINT `fk_supplier_category_supplier` FOREIGN KEY (`supplier_id`) REFERENCES `supplier` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Danh mục của mặt hàng trên đơn đặt hàng, để gợi ý NCC có bán danh mục đó.
ALTER TABLE `purchase_order_line` ADD COLUMN `category` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL;
