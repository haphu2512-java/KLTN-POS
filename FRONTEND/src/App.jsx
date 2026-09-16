import React, { useState } from 'react';
import PosPage from './pages/pos/PosPage';
import InventoryPage from './pages/inventory/InventoryPage';
import ForecastPage from './pages/forecast/ForecastPage';

export default function App() {
  const [currentScreen, setCurrentScreen] = useState('pos'); // 'pos' | 'inventory' | 'forecast'

  if (currentScreen === 'inventory') {
    return (
      <InventoryPage
        onNavigateToPos={() => setCurrentScreen('pos')}
        onNavigateToForecast={() => setCurrentScreen('forecast')}
      />
    );
  }

  if (currentScreen === 'forecast') {
    return (
      <ForecastPage
        onNavigateToPos={() => setCurrentScreen('pos')}
        onNavigateToInventory={() => setCurrentScreen('inventory')}
      />
    );
  }

  return (
    <PosPage
      onNavigateToInventory={() => setCurrentScreen('inventory')}
      onNavigateToForecast={() => setCurrentScreen('forecast')}
    />
  );
}
