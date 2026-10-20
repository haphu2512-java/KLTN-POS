import { useState } from 'react';
import { HashRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import useAppNavigate from './hooks/useAppNavigate';
import LoginPage from './pages/auth/LoginPage';
import PosPage from './pages/pos/PosPage';
import InventoryPage from './pages/inventory/InventoryPage';
import ForecastPage from './pages/forecast/ForecastPage';
import ReportPage from './pages/report/ReportPage';
import PartnerPage from './pages/partner/PartnerPage';
import StaffPage from './pages/admin/StaffPage';

/**
 * Wrapper component cung cấp navigation props cho từng page
 * thông qua React Router thay vì quản lý bằng useState.
 */
function AppRoutes() {
  const { user, branchId, isBackendOnline, isWsConnected, logout, isAuthenticated } = useAuth();
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
    branchId: branchId || 1
  };

  const handleLogout = () => {
    setIsOfflineDemo(false);
    logout();
  };

  // Phân quyền giống backend: báo cáo cho ADMIN/MANAGER, dự báo thêm WAREHOUSE.
  // Chế độ demo ngoại tuyến dùng dữ liệu mẫu nên vẫn cho xem để trình diễn.
  const role = currentUser?.role;
  const canViewReports = isOfflineDemo || role === 'ADMIN' || role === 'MANAGER';
  const canViewForecast = canViewReports || role === 'WAREHOUSE';

  // Shared props cho mọi page
  const sharedProps = {
    currentUser,
    branchId,
    isOnline: isBackendOnline,
    isWsConnected,
    onLogout: handleLogout,
    ...nav, // onNavigateToPos, onNavigateToInventory, onNavigateToForecast, ...
    // Ẩn nút điều hướng tới trang không có quyền
    onNavigateToForecast: canViewForecast ? nav.onNavigateToForecast : undefined,
    onNavigateToReport: canViewReports ? nav.onNavigateToReport : undefined,
  };

  return (
    <Routes>
      <Route
        path="/"
        element={
          <PosPage
            {...sharedProps}
          />
        }
      />
      <Route
        path="/inventory"
        element={
          <InventoryPage
            {...sharedProps}
          />
        }
      />
      <Route
        path="/forecast"
        element={
          canViewForecast
            ? <ForecastPage {...sharedProps} />
            : <Navigate to="/" replace />
        }
      />
      <Route
        path="/report"
        element={
          canViewReports
            ? <ReportPage {...sharedProps} />
            : <Navigate to="/" replace />
        }
      />
      <Route
        path="/partners"
        element={
          <PartnerPage
            {...sharedProps}
          />
        }
      />
      <Route
        path="/staff"
        element={
          currentUser?.role === 'ADMIN'
            ? <StaffPage {...sharedProps} />
            : <Navigate to="/" replace />
        }
      />
      {/* Fallback: redirect unknown routes to POS */}
      <Route path="*" element={<Navigate to="/" replace />} />
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
