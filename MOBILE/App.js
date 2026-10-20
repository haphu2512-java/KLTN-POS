import React, { useState, useEffect, useCallback, useRef } from 'react';
import {
  StyleSheet,
  View,
  SafeAreaView,
  StatusBar,
  Platform,
  Alert
} from 'react-native';
import HeaderBar from './src/components/HeaderBar';
import ScannerView from './src/components/ScannerView';
import ProductResultSheet from './src/components/ProductResultSheet';
import OfflineQueueModal from './src/components/OfflineQueueModal';
import SettingsModal from './src/components/SettingsModal';
import HistoryModal from './src/components/HistoryModal';
import { AppConfig } from './src/api/config';
import { ScannerApi } from './src/api/scannerApi';
import { OfflineStorage } from './src/storage/offlineStorage';
import { triggerScanFeedback } from './src/utils/feedback';

export default function App() {
  const [isOnline, setIsOnline] = useState(false);
  const [scanMode, setScanMode] = useState('POS_REMOTE'); // 'POS_REMOTE' | 'PRICE_CHECK'
  const [isProcessing, setIsProcessing] = useState(false);
  const [scanResult, setScanResult] = useState(null);
  const [isResultOpen, setIsResultOpen] = useState(false);

  // Modals
  const [isQueueOpen, setIsQueueOpen] = useState(false);
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);
  const [isHistoryOpen, setIsHistoryOpen] = useState(false);

  // Data states
  const [queue, setQueue] = useState([]);
  const [history, setHistory] = useState([]);

  // Scan debounce lock to avoid multiple triggers on the same barcode
  const lastScannedBarcode = useRef('');
  const lastScanTimestamp = useRef(0);

  // Initial startup
  useEffect(() => {
    initializeApp();
    // Periodic heartbeat ping to detect online/offline status
    const interval = setInterval(checkConnectivity, 10000);
    return () => clearInterval(interval);
  }, []);

  const initializeApp = async () => {
    try {
      const settings = await AppConfig.getSettings();
      setScanMode(settings.scanMode || 'POS_REMOTE');

      // Pre-seed and load offline products cache
      await OfflineStorage.loadCachedProducts();

      // Refresh queues and history
      await refreshData();

      // Check server connectivity
      await checkConnectivity();
    } catch (e) {
      console.warn('Init error:', e);
    }
  };

  const checkConnectivity = async () => {
    try {
      const res = await ScannerApi.testConnection();
      setIsOnline(res.online);
    } catch {
      setIsOnline(false);
    }
  };

  const refreshData = async () => {
    const [q, h] = await Promise.all([
      OfflineStorage.getOfflineQueue(),
      OfflineStorage.getScanHistory()
    ]);
    setQueue(q);
    setHistory(h);
  };

  // Toggle Mode
  const handleToggleMode = async (mode) => {
    setScanMode(mode);
    await AppConfig.setScanMode(mode);
  };

  // Main Barcode Detected Handler
  const handleBarcodeDetected = useCallback(
    async (scannedCode) => {
      const clean = String(scannedCode || '').trim();
      if (!clean) return;

      const now = Date.now();
      // Throttle: ignore identical code within 1.8 seconds, or any scan within 800ms
      if (
        clean === lastScannedBarcode.current &&
        now - lastScanTimestamp.current < 1800
      ) {
        return;
      }
      if (now - lastScanTimestamp.current < 800) {
        return;
      }

      lastScannedBarcode.current = clean;
      lastScanTimestamp.current = now;

      setIsProcessing(true);

      try {
        const action = scanMode === 'POS_REMOTE' ? 'ADD_TO_CART' : 'LOOKUP_ONLY';
        const result = await ScannerApi.broadcastBarcode(clean, action);

        // Feedback sound and haptics
        await triggerScanFeedback(result.found);

        setScanResult({
          barcode: clean,
          ...result
        });
        setIsResultOpen(true);

        // Refresh offline queue & history
        await refreshData();
      } catch (err) {
        Alert.alert('Lỗi quét mã', err.message);
      } finally {
        setIsProcessing(false);
      }
    },
    [scanMode]
  );

  // Resend to POS button in bottom sheet
  const handleResendToPos = async (barcode) => {
    setIsProcessing(true);
    try {
      const res = await ScannerApi.broadcastBarcode(barcode, 'ADD_TO_CART');
      await triggerScanFeedback(true);
      setScanResult((prev) => ({
        ...prev,
        ...res,
        message: '✓ Đã bắn lại mã vào giỏ hàng POS!'
      }));
      await refreshData();
    } catch (e) {
      Alert.alert('Lỗi', e.message);
    } finally {
      setIsProcessing(false);
    }
  };

  return (
    <SafeAreaView style={styles.safeArea}>
      <StatusBar barStyle="light-content" backgroundColor="#0f172a" />
      <View style={styles.container}>
        {/* Top App Header with Status & Mode Switch */}
        <HeaderBar
          isOnline={isOnline}
          scanMode={scanMode}
          onToggleMode={handleToggleMode}
          queueCount={queue.length}
          onOpenQueue={() => setIsQueueOpen(true)}
          onOpenSettings={() => setIsSettingsOpen(true)}
          onOpenHistory={() => setIsHistoryOpen(true)}
        />

        {/* Live Camera Scanner Viewport & Manual Mock bar */}
        <ScannerView
          onBarcodeDetected={handleBarcodeDetected}
          isScanningActive={!isResultOpen && !isQueueOpen && !isSettingsOpen && !isHistoryOpen}
          isProcessing={isProcessing}
        />

        {/* Product Scanned Bottom Sheet Result */}
        <ProductResultSheet
          visible={isResultOpen}
          result={scanResult}
          scanMode={scanMode}
          onClose={() => setIsResultOpen(false)}
          onResendToPos={handleResendToPos}
        />

        {/* Offline Queue Modal */}
        <OfflineQueueModal
          visible={isQueueOpen}
          queue={queue}
          onClose={() => setIsQueueOpen(false)}
          onQueueUpdated={refreshData}
        />

        {/* Settings & Configuration Modal */}
        <SettingsModal
          visible={isSettingsOpen}
          onClose={() => setIsSettingsOpen(false)}
          onSettingsSaved={() => {
            checkConnectivity();
            refreshData();
          }}
        />

        {/* History Modal */}
        <HistoryModal
          visible={isHistoryOpen}
          history={history}
          onClose={() => setIsHistoryOpen(false)}
          onHistoryUpdated={refreshData}
          onSelectBarcode={(barcode) => handleBarcodeDetected(barcode)}
        />
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: '#0f172a',
    paddingTop: Platform.OS === 'android' ? StatusBar.currentHeight : 0
  },
  container: {
    flex: 1,
    backgroundColor: '#000'
  }
});
