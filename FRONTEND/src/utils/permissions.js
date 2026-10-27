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
  CUSTOMER_VIEW: 'customer.view', // Xem, thêm khách hàng
  SUPPLIER_VIEW: 'supplier.view', // Xem, thêm nhà cung cấp
  ACCOUNT_MANAGE: 'account.manage', // Tài khoản, chi nhánh
};

const ALL = Object.values(PERMS);

const ROLE_PERMS = {
  ADMIN: ALL,
  MANAGER: ALL.filter((p) => p !== PERMS.SHIFTS_SCHEDULE && p !== PERMS.ACCOUNT_MANAGE),
  CASHIER: [PERMS.POS_SELL, PERMS.INVENTORY_VIEW, PERMS.CUSTOMER_VIEW],
  WAREHOUSE: [
    PERMS.INVENTORY_VIEW,
    PERMS.INVENTORY_EDIT,
    PERMS.FORECAST_VIEW,
    PERMS.SUPPLIER_VIEW,
  ],
};

// Chế độ demo ngoại tuyến (thu ngân giả lập) dùng dữ liệu mẫu, mở thêm các màn hình để trình diễn
const OFFLINE_DEMO_EXTRA = [PERMS.REPORT_VIEW, PERMS.FORECAST_VIEW, PERMS.INVENTORY_EDIT, PERMS.SUPPLIER_VIEW];

/** Tập quyền của người dùng; vai trò lạ không có quyền nào */
export function permissionsFor(role, { offlineDemo = false } = {}) {
  const perms = new Set(ROLE_PERMS[role] || []);
  if (offlineDemo) OFFLINE_DEMO_EXTRA.forEach((p) => perms.add(p));
  return perms;
}

/** Trang mở đầu: người bán hàng vào POS, còn lại vào Kho hàng */
export function homePathFor(perms) {
  return perms.has(PERMS.POS_SELL) ? '/' : '/inventory';
}
