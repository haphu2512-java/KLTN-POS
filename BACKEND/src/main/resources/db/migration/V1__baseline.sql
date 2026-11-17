-- ===================================================================
-- V1: cấu trúc database đầy đủ của hệ thống POS (MySQL 8, utf8mb4)
-- Sinh từ schema Hibernate tạo cho các entity hiện tại; dùng làm mốc (baseline) cho Flyway.
-- Database đã có sẵn (tạo bằng ddl-auto=update) được đánh dấu là V1 mà không chạy lại file này
-- (spring.flyway.baseline-on-migrate=true). Thay đổi cấu trúc về sau thêm file V2__..., V3__...
-- ===================================================================

-- Bảng được tạo theo thứ tự chữ cái, có khóa ngoại trỏ tới bảng tạo sau nên tạm tắt kiểm tra khóa ngoại
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE `audit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `action` enum('CASH_MOVEMENT','DISCOUNT_APPROVED','DISCOUNT_PIN_FAILED','INVOICE_DISCOUNT','LOGIN_FAILED','LOGIN_LOCKED','PASSWORD_RESET','POINTS_ADJUST','PRICE_CHANGE','PRODUCT_CREATE','PRODUCT_DELETE','PROMOTION_CHANGE','PURCHASE_ORDER','REFUND','SHIFT_CASH_VARIANCE','SHIFT_CLOSE','SHIFT_OPEN','SHIFT_REVIEW','STOCK_ADJUSTMENT','STOCK_IN','STOCK_TRANSFER','USER_CREATE','USER_STATUS','USER_UPDATE') COLLATE utf8mb4_unicode_ci NOT NULL,
  `actor_id` bigint DEFAULT NULL,
  `actor_role` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `actor_username` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `branch_id` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `entity_id` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `entity_type` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `ip_address` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `summary` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_audit_created` (`created_at`),
  KEY `idx_audit_action` (`action`),
  KEY `idx_audit_branch` (`branch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `branch` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `active` bit(1) NOT NULL,
  `address` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `customer` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `address` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `points` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKo3uty20c6csmx5y4uk2tc5r4m` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `forecast_result` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `algorithm` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `calculated_at` datetime(6) NOT NULL,
  `current_stock` int NOT NULL,
  `daily_demand_forecast` double NOT NULL,
  `forecast_days` int DEFAULT NULL,
  `has_history` bit(1) NOT NULL,
  `history_days` int DEFAULT NULL,
  `intermittent` bit(1) DEFAULT NULL,
  `mae` double DEFAULT NULL,
  `moving_average7` double DEFAULT NULL,
  `quality` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reorder_point` double DEFAULT NULL,
  `rmse` double DEFAULT NULL,
  `safety_stock` double DEFAULT NULL,
  `std_dev` double DEFAULT NULL,
  `suggested_restock_qty` int NOT NULL,
  `total_predicted_demand` int NOT NULL,
  `urgency` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `branch_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_forecast_product_branch` (`product_id`,`branch_id`),
  KEY `FKqs4rqfq69p23ubb8p9pay16g5` (`branch_id`),
  CONSTRAINT `FKqfum9iib836a57ja6tf00elrq` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`),
  CONSTRAINT `FKqs4rqfq69p23ubb8p9pay16g5` FOREIGN KEY (`branch_id`) REFERENCES `branch` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `inventory` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `min_threshold` int NOT NULL,
  `quantity` int NOT NULL,
  `branch_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_branch` (`product_id`,`branch_id`),
  KEY `FKk2cskrccyjpsc9m1dhtsg20om` (`branch_id`),
  CONSTRAINT `FKk2cskrccyjpsc9m1dhtsg20om` FOREIGN KEY (`branch_id`) REFERENCES `branch` (`id`),
  CONSTRAINT `FKp7gj4l80fx8v0uap3b2crjwp5` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `invoice` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `change_amount` decimal(14,2) DEFAULT NULL,
  `client_request_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `discount` decimal(14,2) DEFAULT NULL,
  `discount_approved_by` bigint DEFAULT NULL,
  `discount_reason` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `employee_id` bigint DEFAULT NULL,
  `note` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `payment_method` enum('CARD','CASH','VIETQR') COLLATE utf8mb4_unicode_ci NOT NULL,
  `points_discount` decimal(14,2) DEFAULT NULL,
  `points_earned` int DEFAULT NULL,
  `points_redeemed` int DEFAULT NULL,
  `promotion_discount` decimal(14,2) DEFAULT NULL,
  `promotion_id` bigint DEFAULT NULL,
  `promotion_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `received_amount` decimal(14,2) DEFAULT NULL,
  `refund_shift_session_id` bigint DEFAULT NULL,
  `refunded_amount` decimal(14,2) DEFAULT NULL,
  `refunded_at` datetime(6) DEFAULT NULL,
  `refunded_by` bigint DEFAULT NULL,
  `shift_session_id` bigint DEFAULT NULL,
  `status` enum('CANCELLED','PAID','PARTIALLY_REFUNDED','REFUNDED') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `subtotal` decimal(14,2) DEFAULT NULL,
  `total_amount` decimal(14,2) NOT NULL,
  `branch_id` bigint NOT NULL,
  `customer_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKp5sjuhx12008j8r7yst64ebfj` (`client_request_id`),
  KEY `FKi9mh4nde5rvhs4ai5fo70mdoh` (`branch_id`),
  KEY `FK5e32ukwo9uknwhylogvta4po6` (`customer_id`),
  CONSTRAINT `FK5e32ukwo9uknwhylogvta4po6` FOREIGN KEY (`customer_id`) REFERENCES `customer` (`id`),
  CONSTRAINT `FKi9mh4nde5rvhs4ai5fo70mdoh` FOREIGN KEY (`branch_id`) REFERENCES `branch` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `invoice_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `cost_price` decimal(14,2) DEFAULT NULL,
  `quantity` int NOT NULL,
  `returned_quantity` int DEFAULT NULL,
  `total_price` decimal(14,2) NOT NULL,
  `unit_price` decimal(14,2) NOT NULL,
  `invoice_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKit1rbx4thcr6gx6bm3gxub3y4` (`invoice_id`),
  KEY `FKbe6c21nke5fy4m3vw00f23qsf` (`product_id`),
  CONSTRAINT `FKbe6c21nke5fy4m3vw00f23qsf` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`),
  CONSTRAINT `FKit1rbx4thcr6gx6bm3gxub3y4` FOREIGN KEY (`invoice_id`) REFERENCES `invoice` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `invoice_return` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `approved_by` bigint DEFAULT NULL,
  `branch_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `invoice_id` bigint DEFAULT NULL,
  `payment_method` enum('CARD','CASH','VIETQR') COLLATE utf8mb4_unicode_ci NOT NULL,
  `points_returned` int DEFAULT NULL,
  `points_revoked` int DEFAULT NULL,
  `reason` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `refund_amount` decimal(14,2) NOT NULL,
  `shift_session_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_return_invoice` (`invoice_id`),
  KEY `idx_return_shift` (`shift_session_id`),
  CONSTRAINT `FKgpsqv5n3qsdbmhsskbibbhior` FOREIGN KEY (`invoice_id`) REFERENCES `invoice` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `invoice_return_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `invoice_detail_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  `product_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `quantity` int NOT NULL,
  `refund_amount` decimal(14,2) NOT NULL,
  `unit_price` decimal(14,2) NOT NULL,
  `return_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKaib35fb0fa8hp1ty6ha9vuj5o` (`return_id`),
  CONSTRAINT `FKaib35fb0fa8hp1ty6ha9vuj5o` FOREIGN KEY (`return_id`) REFERENCES `invoice_return` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `point_transaction` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `balance_after` int NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `customer_id` bigint NOT NULL,
  `invoice_id` bigint DEFAULT NULL,
  `note` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `points` int NOT NULL,
  `type` enum('ADJUST','EARN','REDEEM','REFUND_EARN','REFUND_REDEEM') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_point_tx_customer` (`customer_id`,`created_at`),
  KEY `idx_point_tx_invoice` (`invoice_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `pos_terminal` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `branch_id` bigint DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `created_by` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `product` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `active` bit(1) NOT NULL,
  `barcode` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `category` varchar(150) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `cost_price` decimal(14,2) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `image_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `sale_price` decimal(14,2) NOT NULL,
  `unit` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK44c6umvphppa3226vhmagmviu` (`barcode`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `promotion` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `active` bit(1) NOT NULL,
  `branch_id` bigint DEFAULT NULL,
  `category` varchar(150) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `end_at` datetime(6) DEFAULT NULL,
  `max_discount_amount` decimal(14,2) DEFAULT NULL,
  `member_only` bit(1) NOT NULL,
  `min_customer_points` int DEFAULT NULL,
  `min_order_amount` decimal(14,2) DEFAULT NULL,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `start_at` datetime(6) DEFAULT NULL,
  `type` enum('FIXED_AMOUNT','PERCENT') COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `discount_value` decimal(14,2) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `purchase_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `branch_id` bigint NOT NULL,
  `closed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `expected_date` date DEFAULT NULL,
  `note` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `sent_at` datetime(6) DEFAULT NULL,
  `source` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` enum('CANCELLED','DRAFT','PARTIALLY_RECEIVED','RECEIVED','SENT') COLLATE utf8mb4_unicode_ci NOT NULL,
  `supplier_id` bigint DEFAULT NULL,
  `supplier_name` varchar(150) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `purchase_order_line` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `barcode` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `forecast_note` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `ordered_qty` int NOT NULL,
  `product_id` bigint NOT NULL,
  `product_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `received_qty` int NOT NULL,
  `suggested_qty` int DEFAULT NULL,
  `unit_cost` decimal(14,2) DEFAULT NULL,
  `purchase_order_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK210t80fsgdi4s4g7tlg9vdgkd` (`purchase_order_id`),
  CONSTRAINT `FK210t80fsgdi4s4g7tlg9vdgkd` FOREIGN KEY (`purchase_order_id`) REFERENCES `purchase_order` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `scanner_pairing` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `device_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `device_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `last_used_at` datetime(6) DEFAULT NULL,
  `username` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `terminal_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_scanner_pairing_device` (`device_id`),
  KEY `FKe1mob1mgknamjmlf3wcvtxy2a` (`terminal_id`),
  CONSTRAINT `FKe1mob1mgknamjmlf3wcvtxy2a` FOREIGN KEY (`terminal_id`) REFERENCES `pos_terminal` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `shift_assignment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `assigned_at` datetime(6) DEFAULT NULL,
  `assigned_by` bigint DEFAULT NULL,
  `branch_id` bigint NOT NULL,
  `shift_date` date NOT NULL,
  `staff_member_id` bigint NOT NULL,
  `work_shift_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_assignment_slot` (`branch_id`,`work_shift_id`,`shift_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `shift_cash_movement` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `amount` decimal(14,2) NOT NULL,
  `category` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `counterpart` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `created_by` bigint NOT NULL,
  `created_by_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `deleted_by` bigint DEFAULT NULL,
  `reason` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `shift_session_id` bigint NOT NULL,
  `type` enum('DROP','EXPENSE','FLOAT_IN') COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `shift_session` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `branch_id` bigint NOT NULL,
  `branch_open_key` bigint DEFAULT NULL,
  `card_difference` decimal(14,2) DEFAULT NULL,
  `card_refunds` decimal(14,2) DEFAULT NULL,
  `card_sales` decimal(14,2) DEFAULT NULL,
  `cash_difference` decimal(14,2) DEFAULT NULL,
  `cash_refunds` decimal(14,2) DEFAULT NULL,
  `cash_sales` decimal(14,2) DEFAULT NULL,
  `closed_at` datetime(6) DEFAULT NULL,
  `closed_by` bigint DEFAULT NULL,
  `closing_cash` decimal(14,2) DEFAULT NULL,
  `counted_card` decimal(14,2) DEFAULT NULL,
  `counted_vietqr` decimal(14,2) DEFAULT NULL,
  `denominations` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `difference_reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `drops` decimal(14,2) DEFAULT NULL,
  `employee_id` bigint NOT NULL,
  `employee_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `expected_cash` decimal(14,2) DEFAULT NULL,
  `expenses` decimal(14,2) DEFAULT NULL,
  `final_drop` decimal(14,2) DEFAULT NULL,
  `float_in` decimal(14,2) DEFAULT NULL,
  `handover_difference` decimal(14,2) DEFAULT NULL,
  `handover_expected` decimal(14,2) DEFAULT NULL,
  `handover_note` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `handover_reason` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `invoice_count` int DEFAULT NULL,
  `left_in_drawer` decimal(14,2) DEFAULT NULL,
  `non_cash_refunds` decimal(14,2) DEFAULT NULL,
  `non_cash_sales` decimal(14,2) DEFAULT NULL,
  `open_key` bigint DEFAULT NULL,
  `open_override_reason` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `opened_at` datetime(6) NOT NULL,
  `opening_cash` decimal(14,2) NOT NULL,
  `opening_note` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `refunded_count` int DEFAULT NULL,
  `review_note` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reviewed_at` datetime(6) DEFAULT NULL,
  `reviewed_by` bigint DEFAULT NULL,
  `shift_date` date NOT NULL,
  `staff_member_id` bigint DEFAULT NULL,
  `status` enum('CLOSED','OPEN','PENDING_REVIEW') COLLATE utf8mb4_unicode_ci NOT NULL,
  `vietqr_difference` decimal(14,2) DEFAULT NULL,
  `vietqr_refunds` decimal(14,2) DEFAULT NULL,
  `vietqr_sales` decimal(14,2) DEFAULT NULL,
  `work_shift_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKrvmvpirmmn53sojv65bas5jwh` (`branch_open_key`),
  UNIQUE KEY `UKje69pj6o96df3692gbics3upm` (`open_key`),
  KEY `FKjajwmxjntm3lkprb1tevhpwd` (`work_shift_id`),
  CONSTRAINT `FKjajwmxjntm3lkprb1tevhpwd` FOREIGN KEY (`work_shift_id`) REFERENCES `work_shift` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `staff_member` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `active` bit(1) NOT NULL,
  `branch_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `full_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `position` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `stock_count` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `approved_at` datetime(6) DEFAULT NULL,
  `approved_by` bigint DEFAULT NULL,
  `branch_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `note` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` enum('APPROVED','CANCELLED','DRAFT','SUBMITTED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `submitted_at` datetime(6) DEFAULT NULL,
  `variance_value` decimal(14,2) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `stock_count_line` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `barcode` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `counted_qty` int NOT NULL,
  `product_id` bigint NOT NULL,
  `product_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reason` enum('DAMAGED','EXPIRED','LOST','MISCOUNT','OTHER') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `system_qty` int NOT NULL,
  `unit_cost` decimal(14,2) DEFAULT NULL,
  `stock_count_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK31vbshaqnxtoaa6p98t4lmdjk` (`stock_count_id`,`product_id`),
  CONSTRAINT `FKo3kx4wmvxy0ctntrcex812r6k` FOREIGN KEY (`stock_count_id`) REFERENCES `stock_count` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `stock_in` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `client_request_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `employee_id` bigint DEFAULT NULL,
  `note` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `total_cost` decimal(14,2) DEFAULT NULL,
  `branch_id` bigint NOT NULL,
  `supplier_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKi3opaxwbr02qvc86p5cggvnj0` (`client_request_id`),
  KEY `FKgvibalkf7wiks4bfybsgmunha` (`branch_id`),
  KEY `FK8ff0kjbwll0njnpnsx4tl2jev` (`supplier_id`),
  CONSTRAINT `FK8ff0kjbwll0njnpnsx4tl2jev` FOREIGN KEY (`supplier_id`) REFERENCES `supplier` (`id`),
  CONSTRAINT `FKgvibalkf7wiks4bfybsgmunha` FOREIGN KEY (`branch_id`) REFERENCES `branch` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `stock_in_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `expiry_date` date DEFAULT NULL,
  `lot_number` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `quantity` int NOT NULL,
  `unit_cost` decimal(14,2) DEFAULT NULL,
  `product_id` bigint NOT NULL,
  `stock_in_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK321a3t0hn3y6xtmna4q57sv0m` (`product_id`),
  KEY `FK654xniu9jsmjqrkg74kniwy9e` (`stock_in_id`),
  CONSTRAINT `FK321a3t0hn3y6xtmna4q57sv0m` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`),
  CONSTRAINT `FK654xniu9jsmjqrkg74kniwy9e` FOREIGN KEY (`stock_in_id`) REFERENCES `stock_in` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `stock_transfer` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `client_request_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `employee_id` bigint DEFAULT NULL,
  `from_branch_id` bigint NOT NULL,
  `note` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `product_id` bigint NOT NULL,
  `quantity` int NOT NULL,
  `to_branch_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKklhgfi1n0mr413kumbj97i2gd` (`client_request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `supplier` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `address` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `name` varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL,
  `phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `user_branch` (
  `user_id` bigint NOT NULL,
  `branch_id` bigint NOT NULL,
  PRIMARY KEY (`user_id`,`branch_id`),
  CONSTRAINT `FKf4pqkg0chxgb9x5spf509lhea` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `active` bit(1) NOT NULL,
  `approval_pin_hash` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `full_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `password` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `role` enum('ADMIN','CASHIER','MANAGER','WAREHOUSE') COLLATE utf8mb4_unicode_ci NOT NULL,
  `username` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKr43af9ap4edm43mmtq01oddj6` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `work_shift` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `active` bit(1) NOT NULL,
  `branch_id` bigint DEFAULT NULL,
  `end_time` time(6) NOT NULL,
  `name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `sort_order` int NOT NULL,
  `start_time` time(6) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;
