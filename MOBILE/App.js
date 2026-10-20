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
import LoginScreen from './src/components/LoginScreen';
import { AppConfig } from './src/api/config';
import { ScannerApi, AuthError, NotPairedError, parsePairingQr } from './src/api/scannerApi';
import { OfflineStorage } from './src/storage/offlineStorage';
import { triggerScanFeedback } from './src/utils/feedback';

export default function App() {
  const [session, setSession] = useState(null);
  const [isSessionLoaded, setIsSessionLoaded] = useState(false);
  const [authNotice, setAuthNotice] = useState(null);
  const [isOnline, setIsOnline] = useState(false);
  const [scanMode, setScanMode] = useState('POS_REMOTE'); // 'POS_REMOTE' | 'PRICE_CHECK'
  // Quầy POS đã ghép cặp: { pairingId, terminalName } hoặc null
  const [pairing, setPairing] = useState(null);
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
  // Mã ghép cặp vừa xử lý: camera còn nhìn thấy QR thì không gửi lại (mã chỉ dùng một lần)
  const lastPairCode = useRef('');

  // Initial startup
  useEffect(() => {
    initializeApp();
    // Periodic heartbeat ping to detect online/offline status
    const interval = setInterval(checkConnectivity, 10000);
    return () => clearInterval(interval);
  }, []);

  const initializeApp = async () => {
    try {
      setSession(await AppConfig.getSession());
      setIsSessionLoaded(true);

      const settings = await AppConfig.getSettings();
      setScanMode(settings.scanMode || 'POS_REMOTE');

      // Pre-seed and load offline products cache
      await OfflineStorage.loadCachedProducts();

      // Refresh queues and history
      await refreshData();

      // Check server connectivity
      await checkConnectivity();

      // Máy POS có thể đã gỡ điện thoại này trong lúc app tắt
      if (await AppConfig.getSession()) {
        setPairing(await ScannerApi.checkPairing().catch(() => AppConfig.getPairing()));
      }
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

  // Token hết hạn / bị khóa: quay về màn hình đăng nhập
  const handleAuthError = useCallback(async (err) => {
    await ScannerApi.logout();
    setIsResultOpen(false);
    setIsQueueOpen(false);
    setIsSettingsOpen(false);
    setAuthNotice(err?.message || null);
    setPairing(null);
    setSession(null);
  }, []);

  const refreshData = async () => {
    const [q, h, p] = await Promise.all([
      OfflineStorage.getOfflineQueue(),
      OfflineStorage.getScanHistory(),
      AppConfig.getPairing()
    ]);
    setQueue(q);
    setHistory(h);
    setPairing(p);
  };

  const notifyNotPaired = useCallback((err) => {
    setPairing(null);
    setIsResultOpen(false);
    Alert.alert('Chưa ghép quầy POS', err.message);
  }, []);

  // Quét được QR "POS-PAIR:xxxxxx" trên máy POS, hoặc nhập tay mã 6 số trong Cài đặt
  const handlePairCode = useCallback(
    async (code) => {
      setIsProcessing(true);
      try {
        const p = await ScannerApi.pair(code);
        setPairing(p);
        await triggerScanFeedback(true);
        Alert.alert('Đã ghép cặp', `Mã quét từ điện thoại này sẽ vào giỏ hàng của ${p.terminalName}.`);
        return true;
      } catch (err) {
        if (err instanceof AuthError) {
          handleAuthError(err);
        } else {
          await triggerScanFeedback(false);
          Alert.alert('Ghép cặp thất bại', err.message);
        }
        return false;
      } finally {
        setIsProcessing(false);
      }
    },
    [handleAuthError]
  );

  const handleUnpair = useCallback(async () => {
    try {
      await ScannerApi.unpair();
    } catch (err) {
      if (err instanceof AuthError) handleAuthError(err);
    }
    setPairing(null);
  }, [handleAuthError]);

  const handleLogout = async () => {
    await ScannerApi.logout();
    setAuthNotice(null);
    setPairing(null);
    setSession(null);
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

      const pairCode = parsePairingQr(clean);
      if (pairCode) {
        if (pairCode !== lastPairCode.current) {
          lastPairCode.current = pairCode;
          await handlePairCode(pairCode);
        }
        return;
      }

      lastScannedBarcode.current = clean;
      lastScanTimestamp.current = now;

      setIsProcessing(true);

      try {
        // Chỉ chế độ POS_REMOTE mới bắn vào giỏ; tra giá không gửi gì tới POS và không vào hàng đợi
        const result =
          scanMode === 'POS_REMOTE'
            ? await ScannerApi.broadcastBarcode(clean)
            : await ScannerApi.lookupBarcode(clean);

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
        if (err instanceof AuthError) {
          handleAuthError(err);
        } else if (err instanceof NotPairedError) {
          notifyNotPaired(err);
        } else {
          Alert.alert('Lỗi quét mã', err.message);
        }
      } finally {
        setIsProcessing(false);
      }
    },
    [scanMode, handleAuthError, handlePairCode, notifyNotPaired]
  );

  // Resend to POS button in bottom sheet
  const handleResendToPos = async (barcode) => {
    setIsProcessing(true);
    try {
      const res = await ScannerApi.broadcastBarcode(barcode);
      await triggerScanFeedback(true);
      setScanResult((prev) => ({
        ...prev,
        ...res,
        message: res.online ? 'Đã bắn lại mã vào giỏ hàng POS!' : res.message
      }));
      await refreshData();
    } catch (e) {
      if (e instanceof AuthError) {
        handleAuthError(e);
      } else if (e instanceof NotPairedError) {
        notifyNotPaired(e);
      } else {
        Alert.alert('Lỗi', e.message);
      }
    } finally {
      setIsProcessing(false);
    }
  };

  if (!isSessionLoaded) {
    return <SafeAreaView style={styles.safeArea} />;
  }

  if (!session) {
    return (
      <SafeAreaView style={styles.safeArea}>
        <StatusBar barStyle="light-content" backgroundColor="#0f172a" />
        <LoginScreen
          notice={authNotice}
          onLoggedIn={(s) => {
            setAuthNotice(null);
            setSession(s);
            checkConnectivity();
          }}
        />
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.safeArea}>
      <StatusBar barStyle="light-content" backgroundColor="#0f172a" />
      <View style={styles.container}>
        {/* Top App Header with Status & Mode Switch */}
        <HeaderBar
          isOnline={isOnline}
          scanMode={scanMode}
          pairing={pairing}
          onOpenPairing={() => setIsSettingsOpen(true)}
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
          onAuthError={handleAuthError}
        />

        {/* Settings & Configuration Modal */}
        <SettingsModal
          visible={isSettingsOpen}
          onClose={() => setIsSettingsOpen(false)}
          session={session}
          pairing={pairing}
          onPairCode={handlePairCode}
          onUnpair={handleUnpair}
          onLogout={handleLogout}
          onAuthError={handleAuthError}
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
