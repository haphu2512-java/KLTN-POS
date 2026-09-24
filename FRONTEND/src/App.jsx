import React, { useState } from 'react';
import { AuthProvider, useAuth } from './context/AuthContext';
import LoginPage from './pages/auth/LoginPage';
import PosPage from './pages/pos/PosPage';
import InventoryPage from './pages/inventory/InventoryPage';
import ForecastPage from './pages/forecast/ForecastPage';
import ReportPage from './pages/report/ReportPage';

function MainApp() {
  const { user, branchId, isBackendOnline, isWsConnected, logout, isAuthenticated } = useAuth();
  const [currentScreen, setCurrentScreen] = useState('pos'); // 'pos' | 'inventory' | 'forecast' | 'report'
  const [isOfflineDemo, setIsOfflineDemo] = useState(false);

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

  if (currentScreen === 'inventory') {
    return (
      <InventoryPage
        currentUser={currentUser}
        branchId={branchId}
        isOnline={isBackendOnline}
        isWsConnected={isWsConnected}
        onNavigateToPos={() => setCurrentScreen('pos')}
        onNavigateToForecast={() => setCurrentScreen('forecast')}
        onNavigateToReport={() => setCurrentScreen('report')}
        onLogout={handleLogout}
      />
    );
  }

  if (currentScreen === 'forecast') {
    return (
      <ForecastPage
        currentUser={currentUser}
        branchId={branchId}
        isOnline={isBackendOnline}
        isWsConnected={isWsConnected}
        onNavigateToPos={() => setCurrentScreen('pos')}
        onNavigateToInventory={() => setCurrentScreen('inventory')}
        onNavigateToReport={() => setCurrentScreen('report')}
        onLogout={handleLogout}
      />
    );
  }

  if (currentScreen === 'report') {
    return (
      <ReportPage
        currentUser={currentUser}
        branchId={branchId}
        isOnline={isBackendOnline}
        onNavigateToPos={() => setCurrentScreen('pos')}
        onNavigateToInventory={() => setCurrentScreen('inventory')}
        onNavigateToForecast={() => setCurrentScreen('forecast')}
        onLogout={handleLogout}
      />
    );
  }

  return (
    <PosPage
      currentUser={currentUser}
      branchId={branchId}
      isOnline={isBackendOnline}
      isWsConnected={isWsConnected}
      onNavigateToInventory={() => setCurrentScreen('inventory')}
      onNavigateToForecast={() => setCurrentScreen('forecast')}
      onNavigateToReport={() => setCurrentScreen('report')}
      onLogout={handleLogout}
    />
  );
}

export default function App() {
  return (
    <AuthProvider>
      <MainApp />
    </AuthProvider>
  );
}
