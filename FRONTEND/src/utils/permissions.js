/**
 * Bảng quyền theo vai trò — nguồn duy nhất để ẩn menu, chặn route và ẩn nút.
 * Giữ khớp với @PreAuthorize ở backend; ẩn ở giao diện chỉ để gọn màn hình, backend mới là chốt chặn.
 */
export const PERMS = {
  POS_SELL: 'pos.sell', // Bán hàng, mở/đóng ca của mình, thu/chi két
  INVENTORY_VIEW: 'inventory.view', // Xem tồn kho, tra giá
  INVENTORY_EDIT: 'inventory.edit', // Nhập kho, điều chuyển, lịch sử phiếu nhập
  FORECAST_VIEW: 'forecast.view',
  SHIFTS_MANAGE: 'shifts.manage', // Lịch sử ca & thiếu/dư, xếp ca, nhân viên trực
  SHIFTS_SCHEDULE: 'shifts.schedule', // Giờ các ca (lịch mẫu)
  REPORT_VIEW: 'report.view', // Báo cáo, hoàn trả hóa đơn
  CUSTOMER_VIEW: 'customer.view', // Xem, thêm khách hàng, xem lịch sử điểm
  LOYALTY_ADJUST: 'loyalty.adjust', // Điều chỉnh điểm thành viên bằng tay
  SUPPLIER_VIEW: 'supplier.view', // Xem, thêm nhà cung cấp
  ACCOUNT_MANAGE: 'account.manage', // Tài khoản, chi nhánh
  PROMOTION_VIEW: 'promotion.view', // Xem chương trình khuyến mãi
  PROMOTION_MANAGE: 'promotion.manage', // Tạo, sửa, bật/tắt, xóa khuyến mãi
  AUDIT_VIEW: 'audit.view', // Xem nhật ký thao tác (MANAGER: chi nhánh của mình)
};

const ALL = Object.values(PERMS);

const ROLE_PERMS = {
  ADMIN: ALL,
  MANAGER: ALL.filter(
    (p) => p !== PERMS.SHIFTS_SCHEDULE && p !== PERMS.ACCOUNT_MANAGE && p !== PERMS.PROMOTION_MANAGE
  ),
  // Nhân viên bán hàng tại quầy; nghiệp vụ kho (nhập, điều chuyển, kiểm kê, đặt hàng, dự báo) do quản lý đảm nhận
  STAFF: [PERMS.POS_SELL, PERMS.INVENTORY_VIEW, PERMS.CUSTOMER_VIEW],
};

/** Hệ thống còn 3 vai trò; phiên đăng nhập cũ còn lưu CASHIER / WAREHOUSE thì coi là STAFF */
export const ROLES = ['ADMIN', 'MANAGER', 'STAFF'];
export const ROLE_LABELS = { ADMIN: 'Quản trị viên', MANAGER: 'Quản lý', STAFF: 'Nhân viên' };
const LEGACY_ROLES = { CASHIER: 'STAFF', WAREHOUSE: 'STAFF' };

export function normalizeRole(role) {
  return LEGACY_ROLES[role] || role;
}

// Chế độ demo ngoại tuyến (thu ngân giả lập) dùng dữ liệu mẫu, mở thêm các màn hình để trình diễn
const OFFLINE_DEMO_EXTRA = [PERMS.REPORT_VIEW, PERMS.FORECAST_VIEW, PERMS.INVENTORY_EDIT, PERMS.SUPPLIER_VIEW];

/** Tập quyền của người dùng; vai trò lạ không có quyền nào */
export function permissionsFor(role, { offlineDemo = false } = {}) {
  const perms = new Set(ROLE_PERMS[normalizeRole(role)] || []);
  if (offlineDemo) OFFLINE_DEMO_EXTRA.forEach((p) => perms.add(p));
  return perms;
}

/** Trang mở đầu: người bán hàng vào POS, còn lại vào Kho hàng */
export function homePathFor(perms) {
  return perms.has(PERMS.POS_SELL) ? '/' : '/inventory';
}
