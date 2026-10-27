import { useState } from 'react';
import { HashRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import AppShell from './components/layout/AppShell';
import useAppNavigate from './hooks/useAppNavigate';
import LoginPage from './pages/auth/LoginPage';
import PosPage from './pages/pos/PosPage';
import InventoryPage from './pages/inventory/InventoryPage';
import ForecastPage from './pages/forecast/ForecastPage';
import ReportPage from './pages/report/ReportPage';
import PartnerPage from './pages/partner/PartnerPage';
import StaffPage from './pages/admin/StaffPage';
import ShiftsPage from './pages/shifts/ShiftsPage';
import { PERMS, permissionsFor, homePathFor } from './utils/permissions';

/**
 * Wrapper component cung cấp navigation props cho từng page
 * thông qua React Router thay vì quản lý bằng useState.
 */
function AppRoutes() {
  const { user, branchId, branchName, isBackendOnline, isWsConnected, logout, isAuthenticated } = useAuth();
  const [isOfflineDemo, setIsOfflineDemo] = useState(false);
  const nav = useAppNavigate();

  // If user is not authenticated and hasn't selected offline demo, show Login Screen
  if (!isAuthenticated && !isOfflineDemo) {
    return <LoginPage onSkipOffline={() => setIsOfflineDemo(true)} />;
  }

  const currentUser = user || {
    username: 'offline_cashier',
    fullName: 'Thu Ngân (Ngoại Tuyến)',
    role: 'CASHIER',
    branchId: 1,
    branchName: 'Chi nhánh 1 (demo)'
  };

  const handleLogout = () => {
    setIsOfflineDemo(false);
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
    onCreateStockInDraft: can(PERMS.INVENTORY_EDIT) ? nav.onCreateStockInDraft : undefined,
  };

  // Hàm trả về element (không phải component) để không bị mount lại mỗi lần render.
  // Không đủ quyền thì về trang mở đầu của vai trò.
  const shell = (active, allowed, page) =>
    allowed
      ? <AppShell active={active} {...sharedProps}>{page}</AppShell>
      : <Navigate to={homePath} replace />;

  return (
    <Routes>
      <Route path="/" element={shell("pos", can(PERMS.POS_SELL), <PosPage {...sharedProps} />)} />
      <Route
        path="/inventory"
        element={shell("inventory", can(PERMS.INVENTORY_VIEW), <InventoryPage {...sharedProps} />)}
      />
      <Route
        path="/forecast"
        element={shell("forecast", can(PERMS.FORECAST_VIEW), <ForecastPage {...sharedProps} />)}
      />
      <Route
        path="/report"
        element={shell("report", can(PERMS.REPORT_VIEW), <ReportPage {...sharedProps} />)}
      />
      <Route
        path="/partners"
        element={shell(
          "partners",
          can(PERMS.CUSTOMER_VIEW) || can(PERMS.SUPPLIER_VIEW),
          <PartnerPage {...sharedProps} />
        )}
      />
      <Route
        path="/shifts"
        element={shell("shifts", !isOfflineDemo && can(PERMS.SHIFTS_MANAGE), <ShiftsPage {...sharedProps} />)}
      />
      <Route
        path="/staff"
        element={shell("staff", can(PERMS.ACCOUNT_MANAGE), <StaffPage {...sharedProps} />)}
      />
      {/* Fallback: đường dẫn lạ về trang mở đầu của vai trò */}
      <Route path="*" element={<Navigate to={homePath} replace />} />
    </Routes>
  );
}

export default function App() {
  return (
    <HashRouter>
      <AuthProvider>
        <AppRoutes />
      </AuthProvider>
    </HashRouter>
  );
}
