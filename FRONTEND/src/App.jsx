import { useState } from 'react';
import { HashRouter, Navigate, useLocation } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import AppShell from './components/layout/AppShell';
import KeepAlivePage from './components/layout/KeepAlivePage';
import { ToastHost } from './components/common';
import useAppNavigate from './hooks/useAppNavigate';
import LoginPage from './pages/auth/LoginPage';
import PosPage from './pages/pos/PosPage';
import InventoryPage from './pages/inventory/InventoryPage';
import ForecastPage from './pages/forecast/ForecastPage';
import ReportPage from './pages/report/ReportPage';
import PartnerPage from './pages/partner/PartnerPage';
import StaffPage from './pages/admin/StaffPage';
import ShiftsPage from './pages/shifts/ShiftsPage';
import PromotionPage from './pages/promotion/PromotionPage';
import AuditLogPage from './pages/audit/AuditLogPage';
import StockCountPage from './pages/stockcount/StockCountPage';
import PurchaseOrderPage from './pages/purchaseorder/PurchaseOrderPage';
import { PERMS, permissionsFor, homePathFor } from './utils/permissions';

/**
 * Wrapper component cung cấp navigation props cho từng page
 * thông qua React Router thay vì quản lý bằng useState.
 */
function AppRoutes() {
  const { user, branchId, branchName, isBackendOnline, isWsConnected, logout, isAuthenticated } = useAuth();
  const [isOfflineDemo, setIsOfflineDemo] = useState(false);
  const [visited, setVisited] = useState([]);
  const location = useLocation();
  const nav = useAppNavigate();

  // If user is not authenticated and hasn't selected offline demo, show Login Screen
  if (!isAuthenticated && !isOfflineDemo) {
    // Hết phiên / đăng xuất: bỏ các màn hình đã giữ để phiên sau bắt đầu sạch
    if (visited.length > 0) setVisited([]);
    return <LoginPage onSkipOffline={() => setIsOfflineDemo(true)} />;
  }

  const currentUser = user || {
    username: 'offline_cashier',
    fullName: 'Nhân viên (Ngoại Tuyến)',
    role: 'STAFF',
    branchId: 1,
    branchName: 'Chi nhánh 1 (demo)'
  };

  const handleLogout = () => {
    setIsOfflineDemo(false);
    setVisited([]);
    logout();
  };

  // Phân quyền theo bảng chung utils/permissions.js (khớp @PreAuthorize ở backend)
  const perms = permissionsFor(currentUser?.role, { offlineDemo: isOfflineDemo });
  const can = (perm) => perms.has(perm);
  const homePath = homePathFor(perms);

  // Shared props cho mọi page
  const sharedProps = {
    currentUser,
    can,
    // Chi nhánh của phiên đăng nhập (chế độ demo ngoại tuyến dùng chi nhánh 1)
    branchId: branchId || currentUser.branchId,
    branchName: branchName || currentUser.branchName,
    isOnline: isBackendOnline,
    isWsConnected,
    onLogout: handleLogout,
    // Trợ lý hỏi đáp cần máy chủ, không có ở chế độ demo ngoại tuyến
    assistantEnabled: !isOfflineDemo && isAuthenticated,
    ...nav, // onNavigateToPos, onNavigateToInventory, onNavigateToForecast, ...
    // Ẩn nút điều hướng tới trang không có quyền
    onNavigateToPos: can(PERMS.POS_SELL) ? nav.onNavigateToPos : undefined,
    onNavigateToInventory: can(PERMS.INVENTORY_VIEW) ? nav.onNavigateToInventory : undefined,
    onNavigateToForecast: can(PERMS.FORECAST_VIEW) ? nav.onNavigateToForecast : undefined,
    onNavigateToReport: can(PERMS.REPORT_VIEW) ? nav.onNavigateToReport : undefined,
    onNavigateToPartners:
      can(PERMS.CUSTOMER_VIEW) || can(PERMS.SUPPLIER_VIEW) ? nav.onNavigateToPartners : undefined,
    // Ca làm cần máy chủ (lịch sử ca), không có ở chế độ demo ngoại tuyến
    onNavigateToShifts: !isOfflineDemo && can(PERMS.SHIFTS_MANAGE) ? nav.onNavigateToShifts : undefined,
    onNavigateToStaff: can(PERMS.ACCOUNT_MANAGE) ? nav.onNavigateToStaff : undefined,
    // Khuyến mãi lưu trên máy chủ, không có ở chế độ demo ngoại tuyến
    onNavigateToPromotions: !isOfflineDemo && can(PERMS.PROMOTION_VIEW) ? nav.onNavigateToPromotions : undefined,
    // Nhật ký lưu trên máy chủ, không có ở chế độ demo ngoại tuyến
    onNavigateToAudit: !isOfflineDemo && can(PERMS.AUDIT_VIEW) ? nav.onNavigateToAudit : undefined,
    // Kiểm kê lưu trên máy chủ, không có ở chế độ demo ngoại tuyến
    onNavigateToStockCount: !isOfflineDemo && can(PERMS.INVENTORY_EDIT) ? nav.onNavigateToStockCount : undefined,
    onCreateStockInDraft: can(PERMS.INVENTORY_EDIT) ? nav.onCreateStockInDraft : undefined,
    // Đơn đặt hàng lưu trên máy chủ, không có ở chế độ demo ngoại tuyến
    onNavigateToPurchaseOrders: !isOfflineDemo && can(PERMS.INVENTORY_EDIT) ? nav.onNavigateToPurchaseOrders : undefined,
    onOpenPurchaseOrder: !isOfflineDemo && can(PERMS.INVENTORY_EDIT) ? nav.onOpenPurchaseOrder : undefined,
  };

  // Các màn hình của ứng dụng. Màn hình đã mở được GIỮ LẠI (ẩn đi) khi chuyển sang màn hình khác,
  // nên dữ liệu đang nhập (giỏ hàng, biểu mẫu, bộ lọc...) không bị mất khi quay lại.
  // Đăng xuất sẽ gỡ toàn bộ nên không lộ dữ liệu sang phiên sau.
  const pages = [
    { path: '/', key: 'pos', allowed: can(PERMS.POS_SELL), Page: PosPage },
    { path: '/inventory', key: 'inventory', allowed: can(PERMS.INVENTORY_VIEW), Page: InventoryPage },
    { path: '/forecast', key: 'forecast', allowed: can(PERMS.FORECAST_VIEW), Page: ForecastPage },
    { path: '/report', key: 'report', allowed: can(PERMS.REPORT_VIEW), Page: ReportPage },
    {
      path: '/partners',
      key: 'partners',
      allowed: can(PERMS.CUSTOMER_VIEW) || can(PERMS.SUPPLIER_VIEW),
      Page: PartnerPage,
    },
    { path: '/shifts', key: 'shifts', allowed: !isOfflineDemo && can(PERMS.SHIFTS_MANAGE), Page: ShiftsPage },
    { path: '/staff', key: 'staff', allowed: can(PERMS.ACCOUNT_MANAGE), Page: StaffPage },
    {
      path: '/promotions',
      key: 'promotions',
      allowed: !isOfflineDemo && can(PERMS.PROMOTION_VIEW),
      Page: PromotionPage,
    },
    {
      path: '/stock-counts',
      key: 'stockcount',
      allowed: !isOfflineDemo && can(PERMS.INVENTORY_EDIT),
      Page: StockCountPage,
    },
    {
      path: '/purchase-orders',
      key: 'purchaseorder',
      allowed: !isOfflineDemo && can(PERMS.INVENTORY_EDIT),
      Page: PurchaseOrderPage,
    },
    { path: '/audit', key: 'audit', allowed: !isOfflineDemo && can(PERMS.AUDIT_VIEW), Page: AuditLogPage },
  ];

  const current = pages.find((p) => p.path === location.pathname);
  // Đường dẫn lạ hoặc không đủ quyền thì về trang mở đầu của vai trò
  if (!current || !current.allowed) return <Navigate to={homePath} replace />;

  // Ghi nhận màn hình đã mở (cập nhật state ngay khi render, theo cách React khuyến nghị)
  if (!visited.includes(current.key)) setVisited([...visited, current.key]);
  const mounted = new Set([...visited, current.key]);

  return (
    <AppShell active={current.key} {...sharedProps}>
      {pages
        .filter((p) => p.allowed && mounted.has(p.key))
        .map(({ key, Page }) => (
          <KeepAlivePage key={key} active={key === current.key}>
            <Page {...sharedProps} />
          </KeepAlivePage>
        ))}
    </AppShell>
  );
}

export default function App() {
  return (
    <HashRouter>
      <AuthProvider>
        <AppRoutes />
        <ToastHost />
      </AuthProvider>
    </HashRouter>
  );
}
