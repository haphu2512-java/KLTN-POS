import React, { useState } from 'react';
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

  // Shared props cho mọi page
  const sharedProps = {
    currentUser,
    branchId,
    isOnline: isBackendOnline,
    isWsConnected,
    onLogout: handleLogout,
    ...nav, // onNavigateToPos, onNavigateToInventory, onNavigateToForecast, ...
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
          <ForecastPage
            {...sharedProps}
          />
        }
      />
      <Route
        path="/report"
        element={
          <ReportPage
            {...sharedProps}
          />
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
