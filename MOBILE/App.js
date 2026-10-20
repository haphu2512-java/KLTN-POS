import React, { useState, useEffect, useCallback, useRef } from 'react';
import { StyleSheet, View, Alert } from 'react-native';
import { StatusBar } from 'expo-status-bar';
import { SafeAreaProvider, useSafeAreaInsets } from 'react-native-safe-area-context';
import {
  useFonts,
  BeVietnamPro_400Regular,
  BeVietnamPro_500Medium,
  BeVietnamPro_600SemiBold,
  BeVietnamPro_700Bold
} from '@expo-google-fonts/be-vietnam-pro';
import ProductResultSheet from './src/components/ProductResultSheet';
import OfflineQueueModal from './src/components/OfflineQueueModal';
import LoginScreen from './src/components/LoginScreen';
import TabBar from './src/components/TabBar';
import ScanScreen from './src/screens/ScanScreen';
import LookupScreen from './src/screens/LookupScreen';
import HistoryScreen from './src/screens/HistoryScreen';
import SettingsScreen from './src/screens/SettingsScreen';
import { colors } from './src/theme';
import { AppConfig } from './src/api/config';
import { ScannerApi, AuthError, NotPairedError, parsePairingQr } from './src/api/scannerApi';
import { OfflineStorage } from './src/storage/offlineStorage';
import { triggerScanFeedback } from './src/utils/feedback';

export default function App() {
  return (
    <SafeAreaProvider>
      <AppContent />
    </SafeAreaProvider>
  );
}

function AppContent() {
  const insets = useSafeAreaInsets();
  // Nạp lỗi (ví dụ thiếu mạng lúc tải bundle) thì vẫn chạy bằng font hệ thống
  const [fontsLoaded, fontError] = useFonts({
    BeVietnamPro_400Regular,
    BeVietnamPro_500Medium,
    BeVietnamPro_600SemiBold,
    BeVietnamPro_700Bold
  });
  const [activeTab, setActiveTab] = useState('scan'); // 'scan' | 'lookup' | 'history' | 'settings'
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

  const [isQueueOpen, setIsQueueOpen] = useState(false);
  const [isSyncingCatalog, setIsSyncingCatalog] = useState(false);
  // Tăng mỗi lần tải lại danh mục để màn Tra cứu / Cài đặt đọc lại bộ nhớ máy
  const [catalogVersion, setCatalogVersion] = useState(0);

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
    setActiveTab('scan');
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
          mode: scanMode,
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

  // Tra giá một mã (từ Lịch sử, Tra cứu): không gửi gì tới POS
  const handleLookup = async (barcode) => {
    setIsProcessing(true);
    try {
      const result = await ScannerApi.lookupBarcode(barcode);
      setScanResult({ barcode, mode: 'PRICE_CHECK', ...result });
      setIsResultOpen(true);
      await refreshData();
    } catch (e) {
      if (e instanceof AuthError) {
        handleAuthError(e);
      } else {
        Alert.alert('Lỗi tra cứu', e.message);
      }
    } finally {
      setIsProcessing(false);
    }
  };

  // Nút "Gửi lại" / "Thêm vào giỏ POS" trong thẻ kết quả
  const handleSendToPos = async (barcode) => {
    setIsProcessing(true);
    try {
      const res = await ScannerApi.broadcastBarcode(barcode);
      await triggerScanFeedback(true);
      setScanResult((prev) => ({
        ...prev,
        ...res,
        mode: 'POS_REMOTE'
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

  const handleSyncCatalog = async () => {
    setIsSyncingCatalog(true);
    try {
      const res = await ScannerApi.fetchProductsForCache();
      if (res.success) {
        Alert.alert('Đã tải danh mục', `${res.count} sản phẩm đã lưu trên máy để tra cứu khi mất mạng.`);
      } else {
        Alert.alert('Chưa tải được danh mục', res.message);
      }
    } catch (e) {
      if (e instanceof AuthError) {
        handleAuthError(e);
      } else {
        Alert.alert('Lỗi', e.message);
      }
    } finally {
      setCatalogVersion((v) => v + 1);
      setIsSyncingCatalog(false);
    }
  };

  const handleClearHistory = async () => {
    await OfflineStorage.clearScanHistory();
    refreshData();
  };

  const fontsReady = fontsLoaded || !!fontError;

  if (!isSessionLoaded || !fontsReady) {
    return <View style={[styles.root, { paddingTop: insets.top }]} />;
  }

  if (!session) {
    return (
      <View style={[styles.root, { paddingTop: insets.top, paddingBottom: insets.bottom }]}>
        <StatusBar style="dark" />
        <LoginScreen
          notice={authNotice}
          onLoggedIn={(s) => {
            setAuthNotice(null);
            setSession(s);
            setActiveTab('scan');
            checkConnectivity();
          }}
        />
      </View>
    );
  }

  const renderScreen = () => {
    switch (activeTab) {
      case 'lookup':
        return (
          <LookupScreen
            isOnline={isOnline}
            catalogVersion={catalogVersion}
            onSelectProduct={(p) => handleLookup(p.barcode)}
            onScanToLookup={() => {
              handleToggleMode('PRICE_CHECK');
              setActiveTab('scan');
            }}
            onSyncCatalog={handleSyncCatalog}
            isSyncingCatalog={isSyncingCatalog}
          />
        );
      case 'history':
        return (
          <HistoryScreen
            isOnline={isOnline}
            history={history}
            queueCount={queue.length}
            onOpenQueue={() => setIsQueueOpen(true)}
            onSelectHistory={(item) => handleLookup(item.barcode)}
            onClearHistory={handleClearHistory}
            onGoScan={() => setActiveTab('scan')}
          />
        );
      case 'settings':
        return (
          <SettingsScreen
            isOnline={isOnline}
            session={session}
            pairing={pairing}
            queueCount={queue.length}
            catalogVersion={catalogVersion}
            isSyncingCatalog={isSyncingCatalog}
            onSyncCatalog={handleSyncCatalog}
            onOpenQueue={() => setIsQueueOpen(true)}
            onPairCode={handlePairCode}
            onUnpair={handleUnpair}
            onLogout={handleLogout}
            onServerSaved={() => {
              checkConnectivity();
              refreshData();
            }}
          />
        );
      default:
        // Chỉ gắn camera khi đang ở tab Quét, rời tab là camera tắt
        return (
          <ScanScreen
            isOnline={isOnline}
            scanMode={scanMode}
            onToggleMode={handleToggleMode}
            pairing={pairing}
            queueCount={queue.length}
            onOpenQueue={() => setIsQueueOpen(true)}
            onOpenPairing={() => setActiveTab('settings')}
            history={history}
            onSeeAllHistory={() => setActiveTab('history')}
            onSelectHistory={(item) => handleLookup(item.barcode)}
            onBarcodeDetected={handleBarcodeDetected}
            isScanningActive={!isResultOpen && !isQueueOpen}
            isProcessing={isProcessing}
          />
        );
    }
  };

  return (
    <View style={[styles.root, { paddingTop: insets.top }]}>
      <StatusBar style="dark" />
      <View style={{ flex: 1 }}>{renderScreen()}</View>
      <TabBar
        active={activeTab}
        onChange={setActiveTab}
        badges={{ history: queue.length }}
      />

      <ProductResultSheet
        visible={isResultOpen}
        result={scanResult}
        canSendToPos={!!pairing}
        closeLabel={activeTab === 'scan' ? 'Tiếp tục quét' : 'Đóng'}
        onClose={() => setIsResultOpen(false)}
        onSendToPos={handleSendToPos}
      />

      <OfflineQueueModal
        visible={isQueueOpen}
        queue={queue}
        onClose={() => setIsQueueOpen(false)}
        onQueueUpdated={refreshData}
        onAuthError={handleAuthError}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: colors.canvas
  }
});
