import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  Modal,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  Switch,
  ActivityIndicator,
  Alert,
  ScrollView
} from 'react-native';
import { AppConfig } from '../api/config';
import { ScannerApi, AuthError } from '../api/scannerApi';
import { OfflineStorage } from '../storage/offlineStorage';
import { formatDate } from '../utils/formatters';

export default function SettingsModal({
  visible,
  onClose,
  onSettingsSaved,
  session,
  pairing,
  onPairCode,
  onUnpair,
  onLogout,
  onAuthError
}) {
  const [serverUrl, setServerUrl] = useState('');
  const [hapticEnabled, setHapticEnabled] = useState(true);
  const [soundEnabled, setSoundEnabled] = useState(true);
  const [isTesting, setIsTesting] = useState(false);
  const [pingResult, setPingResult] = useState(null);
  const [isSyncingCatalog, setIsSyncingCatalog] = useState(false);
  const [cachedCount, setCachedCount] = useState(0);
  const [lastSyncTime, setLastSyncTime] = useState(null);
  const [pairCode, setPairCode] = useState('');
  const [isPairing, setIsPairing] = useState(false);

  useEffect(() => {
    if (visible) {
      loadCurrentSettings();
    }
  }, [visible]);

  const loadCurrentSettings = async () => {
    const s = await AppConfig.getSettings();
    setServerUrl(s.serverUrl);
    setHapticEnabled(s.hapticEnabled);
    setSoundEnabled(s.soundEnabled);

    const prods = await OfflineStorage.loadCachedProducts();
    setCachedCount(prods.length);
    const syncTime = await OfflineStorage.getLastSyncTime();
    setLastSyncTime(syncTime);
    setPingResult(null);
  };

  const handleTestPing = async () => {
    if (!serverUrl.trim()) return;
    setIsTesting(true);
    setPingResult(null);
    try {
      const res = await ScannerApi.testConnection(serverUrl.trim());
      setPingResult(res);
    } catch (e) {
      setPingResult({ online: false, message: e.message });
    } finally {
      setIsTesting(false);
    }
  };

  const handleSyncCatalog = async () => {
    setIsSyncingCatalog(true);
    try {
      const res = await ScannerApi.fetchProductsForCache();
      if (res.success) {
        setCachedCount(res.count);
        const syncTime = await OfflineStorage.getLastSyncTime();
        setLastSyncTime(syncTime);
        Alert.alert('Thành công', `Đã tải ${res.count} sản phẩm vào bộ nhớ máy để tra cứu Offline.`);
      } else {
        Alert.alert('Lỗi tải danh mục', res.message);
      }
    } catch (e) {
      if (e instanceof AuthError) {
        onAuthError(e);
      } else {
        Alert.alert('Lỗi', e.message);
      }
    } finally {
      setIsSyncingCatalog(false);
    }
  };

  // Nhập tay mã 6 số hiện dưới QR trên máy POS (khi camera không đọc được QR)
  const handleManualPair = async () => {
    if (!/^\d{6}$/.test(pairCode.trim())) {
      Alert.alert('Mã không hợp lệ', 'Nhập đúng 6 chữ số hiện trên máy POS.');
      return;
    }
    setIsPairing(true);
    const ok = await onPairCode(pairCode.trim());
    setIsPairing(false);
    if (ok) setPairCode('');
  };

  const handleUnpair = () => {
    Alert.alert('Hủy ghép cặp', `Điện thoại sẽ ngừng bắn mã vào ${pairing?.terminalName}.`, [
      { text: 'Không', style: 'cancel' },
      { text: 'Hủy ghép', style: 'destructive', onPress: onUnpair }
    ]);
  };

  const handleSave = async () => {
    await AppConfig.saveSettings({
      serverUrl: serverUrl.trim(),
      hapticEnabled,
      soundEnabled
    });
    Alert.alert('Thành công', 'Đã lưu cấu hình kết nối!');
    onSettingsSaved();
    onClose();
  };

  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
      <View style={styles.overlay}>
        <View style={styles.container}>
          {/* Header */}
          <View style={styles.header}>
            <Text style={styles.title}>Cài đặt kết nối & Offline</Text>
            <TouchableOpacity onPress={onClose} style={styles.closeBtn}>
              <Text style={styles.closeText}>✕</Text>
            </TouchableOpacity>
          </View>

          <ScrollView style={styles.body} showsVerticalScrollIndicator={false}>
            {/* SERVER CONFIG SECTION */}
            <View style={styles.section}>
              <Text style={styles.sectionTitle}>Địa chỉ máy chủ POS (Backend API)</Text>
              <TextInput
                style={styles.input}
                value={serverUrl}
                onChangeText={setServerUrl}
                placeholder="http://192.168.1.15:8080"
                placeholderTextColor="#64748b"
                autoCapitalize="none"
                autoCorrect={false}
              />

              {/* Quick Preset Buttons */}
              <View style={styles.presetsRow}>
                <TouchableOpacity
                  style={styles.presetChip}
                  onPress={() => setServerUrl('http://10.0.2.2:8080')}
                >
                  <Text style={styles.presetText}>Android Emu (10.0.2.2)</Text>
                </TouchableOpacity>

                <TouchableOpacity
                  style={styles.presetChip}
                  onPress={() => setServerUrl('http://localhost:8080')}
                >
                  <Text style={styles.presetText}>Localhost</Text>
                </TouchableOpacity>
              </View>

              {/* Test Ping Button */}
              <TouchableOpacity
                style={styles.testBtn}
                onPress={handleTestPing}
                disabled={isTesting}
                activeOpacity={0.8}
              >
                {isTesting ? (
                  <ActivityIndicator size="small" color="#fff" />
                ) : (
                  <Text style={styles.testBtnText}>⚡ Kiểm tra kết nối máy chủ</Text>
                )}
              </TouchableOpacity>

              {/* Ping Result Feedback */}
              {pingResult && (
                <View
                  style={[
                    styles.pingResultBox,
                    { backgroundColor: pingResult.online ? '#064e3b' : '#451a03' }
                  ]}
                >
                  <Text
                    style={[
                      styles.pingResultText,
                      { color: pingResult.online ? '#34d399' : '#fbbf24' }
                    ]}
                  >
                    {pingResult.online ? '✓ ' : '✕ '} {pingResult.message}
                  </Text>
                </View>
              )}
            </View>

            {/* PAIRING: điện thoại bắn mã vào quầy POS đã ghép */}
            <View style={styles.section}>
              <Text style={styles.sectionTitle}>Quầy POS nhận mã quét</Text>
              {pairing ? (
                <>
                  <View style={styles.cacheInfoBox}>
                    <Text style={styles.cacheCount}>
                      Đang ghép: <Text style={styles.highlightText}>{pairing.terminalName}</Text>
                    </Text>
                    <Text style={styles.cacheTime}>Muốn đổi quầy: quét QR trên máy POS khác.</Text>
                  </View>
                  <TouchableOpacity
                    style={[styles.syncCatalogBtn, { backgroundColor: '#7f1d1d' }]}
                    onPress={handleUnpair}
                    activeOpacity={0.8}
                  >
                    <Text style={styles.syncCatalogBtnText}>Hủy ghép cặp</Text>
                  </TouchableOpacity>
                </>
              ) : (
                <Text style={styles.cacheTime}>
                  Trên máy POS bấm "Ghép điện thoại", rồi đưa camera vào mã QR. Hoặc nhập mã 6 số:
                </Text>
              )}
              <View style={[styles.presetsRow, { alignItems: 'center' }]}>
                <TextInput
                  style={[styles.input, { flex: 1, marginBottom: 0 }]}
                  value={pairCode}
                  onChangeText={setPairCode}
                  placeholder="Mã 6 số trên máy POS"
                  placeholderTextColor="#64748b"
                  keyboardType="number-pad"
                  maxLength={6}
                />
                <TouchableOpacity
                  style={[styles.presetChip, { paddingVertical: 12 }]}
                  onPress={handleManualPair}
                  disabled={isPairing}
                >
                  {isPairing ? (
                    <ActivityIndicator size="small" color="#fff" />
                  ) : (
                    <Text style={styles.presetText}>Ghép</Text>
                  )}
                </TouchableOpacity>
              </View>
            </View>

            {/* ACCOUNT: chi nhánh lấy theo tài khoản đăng nhập, không chọn tay */}
            <View style={styles.section}>
              <Text style={styles.sectionTitle}>Tài khoản</Text>
              <View style={styles.cacheInfoBox}>
                <Text style={styles.cacheCount}>
                  Đăng nhập: <Text style={styles.highlightText}>{session?.fullName || session?.username}</Text>
                </Text>
                <Text style={styles.cacheTime}>
                  {session?.role} · {session?.branchId ? `Chi nhánh #${session.branchId}` : 'Không gắn chi nhánh'}
                </Text>
              </View>
              <TouchableOpacity
                style={[styles.syncCatalogBtn, { backgroundColor: '#7f1d1d' }]}
                onPress={onLogout}
                activeOpacity={0.8}
              >
                <Text style={styles.syncCatalogBtnText}>Đăng xuất</Text>
              </TouchableOpacity>
            </View>

            {/* OFFLINE CATALOG SECTION */}
            <View style={styles.section}>
              <Text style={styles.sectionTitle}>Dữ liệu Offline (Offline Cache)</Text>
              <View style={styles.cacheInfoBox}>
                <Text style={styles.cacheCount}>
                  Đã lưu trữ: <Text style={styles.highlightText}>{cachedCount} sản phẩm</Text>
                </Text>
                {lastSyncTime && (
                  <Text style={styles.cacheTime}>
                    Lần tải cuối: {formatDate(lastSyncTime)}
                  </Text>
                )}
              </View>

              <TouchableOpacity
                style={styles.syncCatalogBtn}
                onPress={handleSyncCatalog}
                disabled={isSyncingCatalog}
                activeOpacity={0.8}
              >
                {isSyncingCatalog ? (
                  <ActivityIndicator size="small" color="#fff" />
                ) : (
                  <Text style={styles.syncCatalogBtnText}>
                    📥 Tải danh mục sản phẩm về máy (Offline)
                  </Text>
                )}
              </TouchableOpacity>
            </View>

            {/* FEEDBACK SWITCHES */}
            <View style={styles.section}>
              <Text style={styles.sectionTitle}>Hiệu ứng quét mã</Text>

              <View style={styles.switchRow}>
                <Text style={styles.switchLabel}>Rung phản hồi (Haptics)</Text>
                <Switch
                  value={hapticEnabled}
                  onValueChange={setHapticEnabled}
                  trackColor={{ false: '#334155', true: '#059669' }}
                  thumbColor="#fff"
                />
              </View>

              <View style={styles.switchRow}>
                <Text style={styles.switchLabel}>Âm thanh Beep (Sound)</Text>
                <Switch
                  value={soundEnabled}
                  onValueChange={setSoundEnabled}
                  trackColor={{ false: '#334155', true: '#059669' }}
                  thumbColor="#fff"
                />
              </View>
            </View>
          </ScrollView>

          {/* Footer Save */}
          <View style={styles.footer}>
            <TouchableOpacity style={styles.saveBtn} onPress={handleSave} activeOpacity={0.8}>
              <Text style={styles.saveBtnText}>Lưu cấu hình</Text>
            </TouchableOpacity>
          </View>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.7)',
    justifyContent: 'flex-end'
  },
  container: {
    backgroundColor: '#0f172a',
    borderTopLeftRadius: 24,
    borderTopRightRadius: 24,
    maxHeight: '85%',
    padding: 20,
    borderWidth: 1,
    borderColor: '#334155'
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 16
  },
  title: {
    color: '#f8fafc',
    fontSize: 18,
    fontWeight: '800'
  },
  closeBtn: {
    width: 32,
    height: 32,
    borderRadius: 16,
    backgroundColor: '#1e293b',
    alignItems: 'center',
    justifyContent: 'center'
  },
  closeText: {
    color: '#94a3b8',
    fontSize: 16,
    fontWeight: '700'
  },
  body: {
    marginBottom: 16
  },
  section: {
    backgroundColor: '#1e293b',
    borderRadius: 14,
    padding: 14,
    marginBottom: 14,
    borderWidth: 1,
    borderColor: '#334155'
  },
  sectionTitle: {
    color: '#f8fafc',
    fontSize: 13,
    fontWeight: '700',
    marginBottom: 10
  },
  input: {
    backgroundColor: '#0f172a',
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    color: '#fff',
    fontSize: 13,
    borderWidth: 1,
    borderColor: '#334155',
    marginBottom: 10
  },
  presetsRow: {
    flexDirection: 'row',
    gap: 8,
    marginBottom: 10
  },
  presetChip: {
    backgroundColor: '#334155',
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 6
  },
  presetText: {
    color: '#cbd5e1',
    fontSize: 11,
    fontWeight: '600'
  },
  testBtn: {
    backgroundColor: '#2563eb',
    paddingVertical: 10,
    borderRadius: 8,
    alignItems: 'center'
  },
  testBtnText: {
    color: '#fff',
    fontSize: 12,
    fontWeight: '700'
  },
  pingResultBox: {
    marginTop: 8,
    padding: 8,
    borderRadius: 8
  },
  pingResultText: {
    fontSize: 11,
    fontWeight: '600'
  },
  cacheInfoBox: {
    backgroundColor: '#0f172a',
    padding: 10,
    borderRadius: 8,
    marginBottom: 10
  },
  cacheCount: {
    color: '#94a3b8',
    fontSize: 12
  },
  highlightText: {
    color: '#38bdf8',
    fontWeight: '700'
  },
  cacheTime: {
    color: '#64748b',
    fontSize: 11,
    marginTop: 4
  },
  syncCatalogBtn: {
    backgroundColor: '#059669',
    paddingVertical: 10,
    borderRadius: 8,
    alignItems: 'center'
  },
  syncCatalogBtnText: {
    color: '#fff',
    fontSize: 12,
    fontWeight: '700'
  },
  switchRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 6
  },
  switchLabel: {
    color: '#cbd5e1',
    fontSize: 13
  },
  footer: {
    borderTopWidth: 1,
    borderTopColor: '#1e293b',
    paddingTop: 12
  },
  saveBtn: {
    backgroundColor: '#10b981',
    paddingVertical: 14,
    borderRadius: 12,
    alignItems: 'center'
  },
  saveBtnText: {
    color: '#0f172a',
    fontSize: 15,
    fontWeight: '800'
  }
});
