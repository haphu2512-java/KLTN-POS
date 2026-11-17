-- =====================================================================
-- Nhà cung cấp và danh mục hàng mỗi NCC cung cấp.
--
-- Cách chạy (Docker, ở thư mục gốc repo):
--   docker exec -i pos-mysql mysql -uroot -p"$DB_ROOT_PASSWORD" pos_inventory < data/bhx/nha_cung_cap.sql
--
-- Dùng cho database đã có dữ liệu (đã nạp import_bhx_data.sql trước đây): thêm NCC còn thiếu và
-- gán danh mục để mọi danh mục sản phẩm đều có NCC. import_bhx_data.sql đã chứa đúng khối này ở cuối,
-- nên database mới nạp file đó thì không cần chạy file này.
-- Yêu cầu: backend đã chạy ít nhất một lần (có bảng supplier, supplier_category).
-- Sửa danh sách NCC thì sửa cả hai file cho khớp.
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- NHÀ CUNG CẤP VÀ DANH MỤC HÀNG CUNG CẤP (18 NCC, phủ đủ 71 danh mục sản phẩm)
-- Mỗi danh mục có ít nhất một NCC bán, để lập đơn từ AI, gửi đơn đặt hàng và phiếu nhập kho
-- đều chọn được NCC đúng danh mục (quy tắc SupplierCategoryRule ở máy chủ).
-- Chạy lại nhiều lần được: NCC trùng tên không thêm lại, chỉ bổ sung danh mục còn thiếu,
-- không xóa danh mục người dùng đã gán ở trang Đối tác.
-- ---------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS ncc_seed;
CREATE TEMPORARY TABLE ncc_seed (
  name     VARCHAR(150) COLLATE utf8mb4_unicode_ci NOT NULL,
  phone    VARCHAR(20)  COLLATE utf8mb4_unicode_ci,
  address  VARCHAR(255) COLLATE utf8mb4_unicode_ci,
  category VARCHAR(100) COLLATE utf8mb4_unicode_ci NOT NULL
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO ncc_seed (name, phone, address, category) VALUES
('Công ty CP Sữa Việt Nam (Vinamilk)', '02854155555', 'Q.7, TP.HCM', 'Sữa & Bơ'),
('Công ty CP Sữa Việt Nam (Vinamilk)', '02854155555', 'Q.7, TP.HCM', 'Sữa tươi'),
('Công ty CP Sữa Việt Nam (Vinamilk)', '02854155555', 'Q.7, TP.HCM', 'Sữa chua'),
('Công ty CP Sữa Việt Nam (Vinamilk)', '02854155555', 'Q.7, TP.HCM', 'Sữa chua uống'),
('Công ty CP Sữa Việt Nam (Vinamilk)', '02854155555', 'Q.7, TP.HCM', 'Sữa hạt, sữa đậu'),
('Công ty CP Sữa Việt Nam (Vinamilk)', '02854155555', 'Q.7, TP.HCM', 'Sữa bột & Sữa dinh dưỡng'),
('Công ty CP Sữa Việt Nam (Vinamilk)', '02854155555', 'Q.7, TP.HCM', 'Tráng miệng'),
('Công ty TNHH Nước Giải Khát Coca-Cola', '02838961000', 'Q.Thủ Đức, TP.HCM', 'Nước giải khát'),
('Công ty TNHH Nước Giải Khát Coca-Cola', '02838961000', 'Q.Thủ Đức, TP.HCM', 'Nước ngọt'),
('Công ty TNHH Nước Giải Khát Coca-Cola', '02838961000', 'Q.Thủ Đức, TP.HCM', 'Nước khoáng, nước suối'),
('Công ty TNHH Nước Giải Khát Coca-Cola', '02838961000', 'Q.Thủ Đức, TP.HCM', 'Nước tăng lực'),
('Công ty TNHH Nước Giải Khát Coca-Cola', '02838961000', 'Q.Thủ Đức, TP.HCM', 'Nước ép trái cây'),
('Công ty CP Acecook Việt Nam', '02838154064', 'Q.Tân Phú, TP.HCM', 'Thực phẩm khô'),
('Công ty CP Acecook Việt Nam', '02838154064', 'Q.Tân Phú, TP.HCM', 'Mì ăn liền'),
('Công ty CP Acecook Việt Nam', '02838154064', 'Q.Tân Phú, TP.HCM', 'Hủ tiếu, miến'),
('Công ty CP Acecook Việt Nam', '02838154064', 'Q.Tân Phú, TP.HCM', 'Phở, bún ăn liền'),
('Công ty CP Acecook Việt Nam', '02838154064', 'Q.Tân Phú, TP.HCM', 'Cháo gói, cháo tươi'),
('Công ty TNHH Dầu Thực Vật Cái Lân (Simply)', '02438641155', 'Nhà Bè, TP.HCM', 'Gia vị & Dầu ăn'),
('Công ty TNHH Dầu Thực Vật Cái Lân (Simply)', '02438641155', 'Nhà Bè, TP.HCM', 'Dầu ăn'),
('Công ty TNHH Quốc Tế Unilever Việt Nam', '02854135686', 'Q.7, TP.HCM', 'Hóa mỹ phẩm'),
('Công ty TNHH Quốc Tế Unilever Việt Nam', '02854135686', 'Q.7, TP.HCM', 'Dầu gội & Xả tóc'),
('Công ty TNHH Quốc Tế Unilever Việt Nam', '02854135686', 'Q.7, TP.HCM', 'Sữa tắm'),
('Công ty TNHH Quốc Tế Unilever Việt Nam', '02854135686', 'Q.7, TP.HCM', 'Kem đánh răng'),
('Công ty TNHH Quốc Tế Unilever Việt Nam', '02854135686', 'Q.7, TP.HCM', 'Nước xả vải'),
('Công ty TNHH Quốc Tế Unilever Việt Nam', '02854135686', 'Q.7, TP.HCM', 'Nước rửa chén'),
('Công ty TNHH Quốc Tế Unilever Việt Nam', '02854135686', 'Q.7, TP.HCM', 'Bột giặt & Nước giặt'),
('Công ty CP Mondelez Kinh Đô Việt Nam', '02837270838', 'Q.Bình Thạnh, TP.HCM', 'Bánh kẹo'),
('Công ty CP Mondelez Kinh Đô Việt Nam', '02837270838', 'Q.Bình Thạnh, TP.HCM', 'Bánh quy'),
('Công ty CP Mondelez Kinh Đô Việt Nam', '02837270838', 'Q.Bình Thạnh, TP.HCM', 'Bánh bông lan'),
('Công ty CP Mondelez Kinh Đô Việt Nam', '02837270838', 'Q.Bình Thạnh, TP.HCM', 'Bánh xốp'),
('Công ty CP Mondelez Kinh Đô Việt Nam', '02837270838', 'Q.Bình Thạnh, TP.HCM', 'Bánh quế'),
('Công ty CP Mondelez Kinh Đô Việt Nam', '02837270838', 'Q.Bình Thạnh, TP.HCM', 'Socola'),
('Công ty CP Mondelez Kinh Đô Việt Nam', '02837270838', 'Q.Bình Thạnh, TP.HCM', 'Kẹo Singum'),
('Công ty TNHH Thực phẩm Orion Vina', '02743767750', 'Bình Dương', 'Bánh Chocopie'),
('Công ty TNHH Thực phẩm Orion Vina', '02743767750', 'Bình Dương', 'Bánh snack'),
('Công ty TNHH Thực phẩm Orion Vina', '02743767750', 'Bình Dương', 'Bánh gạo'),
('Công ty TNHH Thực phẩm Orion Vina', '02743767750', 'Bình Dương', 'Bánh quy'),
('Công ty TNHH Thực phẩm Orion Vina', '02743767750', 'Bình Dương', 'Bánh bông lan'),
('Công ty CP Bibica', '02837177212', 'Q.Bình Tân, TP.HCM', 'Kẹo cứng'),
('Công ty CP Bibica', '02837177212', 'Q.Bình Tân, TP.HCM', 'Kẹo dẻo, kẹo marshmallow'),
('Công ty CP Bibica', '02837177212', 'Q.Bình Tân, TP.HCM', 'Kẹo các loại'),
('Công ty CP Bibica', '02837177212', 'Q.Bình Tân, TP.HCM', 'Socola'),
('Công ty CP Bibica', '02837177212', 'Q.Bình Tân, TP.HCM', 'Bánh quy'),
('Công ty CP Hàng Tiêu Dùng Masan', '02862563862', 'Q.1, TP.HCM', 'Nước mắm'),
('Công ty CP Hàng Tiêu Dùng Masan', '02862563862', 'Q.1, TP.HCM', 'Nước tương'),
('Công ty CP Hàng Tiêu Dùng Masan', '02862563862', 'Q.1, TP.HCM', 'Hạt nêm, gia vị'),
('Công ty CP Hàng Tiêu Dùng Masan', '02862563862', 'Q.1, TP.HCM', 'Đường & Muối'),
('Công ty CP Hàng Tiêu Dùng Masan', '02862563862', 'Q.1, TP.HCM', 'Gia vị & Dầu ăn'),
('Công ty CP Hàng Tiêu Dùng Masan', '02862563862', 'Q.1, TP.HCM', 'Cà phê hoà tan'),
('Tổng Công ty CP Bia - Rượu - NGK Sài Gòn (SABECO)', '02838294083', 'Q.1, TP.HCM', 'Bia & Nước có cồn'),
('Công ty CP Cà phê Trung Nguyên', '02839251852', 'Q.1, TP.HCM', 'Cà phê & Trà'),
('Công ty CP Cà phê Trung Nguyên', '02839251852', 'Q.1, TP.HCM', 'Cà phê hoà tan'),
('Công ty CP Cà phê Trung Nguyên', '02839251852', 'Q.1, TP.HCM', 'Trà khô, trà túi lọc'),
('Công ty CP Chăn nuôi C.P. Việt Nam', '02513836251', 'Đồng Nai', 'Thịt & Món mặn'),
('Công ty CP Chăn nuôi C.P. Việt Nam', '02513836251', 'Đồng Nai', 'Thịt heo'),
('Công ty CP Chăn nuôi C.P. Việt Nam', '02513836251', 'Đồng Nai', 'Thịt bò'),
('Công ty CP Chăn nuôi C.P. Việt Nam', '02513836251', 'Đồng Nai', 'Thịt gà, vịt'),
('Công ty CP Chăn nuôi C.P. Việt Nam', '02513836251', 'Đồng Nai', 'Trứng gà, vịt, cút'),
('Công ty CP Chăn nuôi C.P. Việt Nam', '02513836251', 'Đồng Nai', 'Tiệc tại gia'),
('Công ty TNHH MTV Vissan', '02835533999', 'Q.Bình Thạnh, TP.HCM', 'Thịt & Món mặn'),
('Công ty TNHH MTV Vissan', '02835533999', 'Q.Bình Thạnh, TP.HCM', 'Thịt heo'),
('Công ty TNHH MTV Vissan', '02835533999', 'Q.Bình Thạnh, TP.HCM', 'Thịt bò'),
('Công ty TNHH MTV Vissan', '02835533999', 'Q.Bình Thạnh, TP.HCM', 'Tiệc tại gia'),
('Công ty TNHH MTV Vissan', '02835533999', 'Q.Bình Thạnh, TP.HCM', 'Khô chế biến sẵn'),
('Hợp tác xã Rau sạch Đà Lạt', '02633822555', 'Đà Lạt, Lâm Đồng', 'Rau lá tươi'),
('Hợp tác xã Rau sạch Đà Lạt', '02633822555', 'Đà Lạt, Lâm Đồng', 'Rau tươi'),
('Hợp tác xã Rau sạch Đà Lạt', '02633822555', 'Đà Lạt, Lâm Đồng', 'Rau sống & Salad'),
('Hợp tác xã Rau sạch Đà Lạt', '02633822555', 'Đà Lạt, Lâm Đồng', 'Củ, quả tươi'),
('Hợp tác xã Rau sạch Đà Lạt', '02633822555', 'Đà Lạt, Lâm Đồng', 'Trái cây tươi'),
('Hợp tác xã Rau sạch Đà Lạt', '02633822555', 'Đà Lạt, Lâm Đồng', 'Món canh & Lẩu'),
('Công ty TNHH Thủy sản Bến Thành', '02838212345', 'Q.4, TP.HCM', 'Cá, hải sản tươi'),
('Công ty CP Thực phẩm Tân Tân', '02838155025', 'Q.Tân Bình, TP.HCM', 'Hạt khô'),
('Công ty CP Thực phẩm Tân Tân', '02838155025', 'Q.Tân Bình, TP.HCM', 'Hạt khô & Hạt dinh dưỡng'),
('Công ty CP Thực phẩm Tân Tân', '02838155025', 'Q.Tân Bình, TP.HCM', 'Trái cây sấy'),
('Công ty CP Thực phẩm Tân Tân', '02838155025', 'Q.Tân Bình, TP.HCM', 'Đậu, nấm & Đồ khô'),
('Công ty CP Thực phẩm Tân Tân', '02838155025', 'Q.Tân Bình, TP.HCM', 'Rong biển'),
('Công ty CP Thực phẩm Tân Tân', '02838155025', 'Q.Tân Bình, TP.HCM', 'Cơm cháy, bánh tráng'),
('Công ty CP Thực phẩm Tân Tân', '02838155025', 'Q.Tân Bình, TP.HCM', 'Ngũ cốc, yến mạch'),
('Công ty CP Thực phẩm Tân Tân', '02838155025', 'Q.Tân Bình, TP.HCM', 'Khô chế biến sẵn'),
('Công ty CP Lương thực Phương Nam', '02838292222', 'Q.1, TP.HCM', 'Gạo, nếp'),
('Công ty CP Lương thực Phương Nam', '02838292222', 'Q.1, TP.HCM', 'Đậu, nấm & Đồ khô'),
('Công ty CP Giấy Sài Gòn', '02743589876', 'Bình Dương', 'Giấy vệ sinh & Khăn giấy'),
('Công ty CP Giấy Sài Gòn', '02743589876', 'Bình Dương', 'Màng bọc & Túi rác');

INSERT INTO `supplier` (`name`, `phone`, `address`)
SELECT DISTINCT t.name, t.phone, t.address FROM ncc_seed t
WHERE NOT EXISTS (SELECT 1 FROM `supplier` s WHERE s.name = t.name);

INSERT IGNORE INTO `supplier_category` (`supplier_id`, `category`)
SELECT s.id, t.category FROM ncc_seed t JOIN `supplier` s ON s.name = t.name;

DROP TEMPORARY TABLE ncc_seed;

-- Kiểm tra: danh mục sản phẩm chưa có NCC nào bán (kết quả rỗng là đủ)
SELECT DISTINCT p.category AS `Danh mục chưa có NCC`
FROM `product` p
WHERE p.category IS NOT NULL AND p.category <> ''
  AND NOT EXISTS (SELECT 1 FROM `supplier_category` sc WHERE sc.category = p.category);
