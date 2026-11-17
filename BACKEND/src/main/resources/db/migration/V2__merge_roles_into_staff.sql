-- Hệ thống còn 3 vai trò: ADMIN, MANAGER, STAFF.
-- Thu ngân (CASHIER) và Thủ kho (WAREHOUSE) gộp thành STAFF; nghiệp vụ kho do MANAGER / ADMIN đảm nhận.
ALTER TABLE `users` MODIFY `role` VARCHAR(20) COLLATE utf8mb4_unicode_ci NOT NULL;
UPDATE `users` SET `role` = 'STAFF' WHERE `role` IN ('CASHIER', 'WAREHOUSE');
ALTER TABLE `users` MODIFY `role` ENUM('ADMIN','MANAGER','STAFF') COLLATE utf8mb4_unicode_ci NOT NULL;
