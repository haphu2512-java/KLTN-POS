import React, { useEffect, useState } from 'react';
import { View, Text, TextInput, TouchableOpacity, ScrollView, Switch, Alert, StyleSheet } from 'react-native';
import { ScreenHeader, Card, SectionTitle, Button, Icon } from '../components/ui';
import { AppConfig, getAutoDetectedHost } from '../api/config';
import { ScannerApi } from '../api/scannerApi';
import { OfflineStorage } from '../storage/offlineStorage';
import { colors, fonts, radius, type } from '../theme';
import { formatDate } from '../utils/formatters';

const ROLE_LABELS = { ADMIN: 'Quản trị', MANAGER: 'Quản lý', CASHIER: 'Thu ngân', WAREHOUSE: 'Thủ kho' };

export default function SettingsScreen({
  isOnline,
  session,
  pairing,
  queueCount,
  catalogVersion,
  isSyncingCatalog,
  onSyncCatalog,
  onOpenQueue,
  onPairCode,
  onUnpair,
  onLogout,
  onServerSaved
}) {
  const [serverUrl, setServerUrl] = useState('');
  const [savedServerUrl, setSavedServerUrl] = useState('');
  const [hapticEnabled, setHapticEnabled] = useState(true);
  const [soundEnabled, setSoundEnabled] = useState(true);
  const [isTesting, setIsTesting] = useState(false);
  const [pingResult, setPingResult] = useState(null);
  const [cachedCount, setCachedCount] = useState(0);
  const [lastSyncTime, setLastSyncTime] = useState(null);
  const [pairCode, setPairCode] = useState('');
  const [isPairing, setIsPairing] = useState(false);

  useEffect(() => {
    (async () => {
      const s = await AppConfig.getSettings();
      setServerUrl(s.serverUrl);
      setSavedServerUrl(s.serverUrl);
      setHapticEnabled(s.hapticEnabled);
      setSoundEnabled(s.soundEnabled);
    })();
  }, []);

  useEffect(() => {
    (async () => {
      const prods = await OfflineStorage.loadCachedProducts();
      setCachedCount(prods.length);
      setLastSyncTime(await OfflineStorage.getLastSyncTime());
    })();
  }, [catalogVersion]);

  const handleTestPing = async () => {
    if (!serverUrl.trim()) return;
    setIsTesting(true);
    setPingResult(null);
    try {
      setPingResult(await ScannerApi.testConnection(serverUrl.trim()));
    } catch (e) {
      setPingResult({ online: false, message: e.message });
    } finally {
      setIsTesting(false);
    }
  };

  const handleAutoDetect = async () => {
    setIsTesting(true);
    setPingResult(null);
    try {
      const res = await AppConfig.autoDetectServer();
      if (res.success) {
        setServerUrl(res.url);
        setPingResult({ online: true, message: `Đã tự động tìm thấy máy chủ: ${res.url}` });
      } else {
        const detected = getAutoDetectedHost();
        if (detected) {
          setServerUrl(detected);
          setPingResult({ online: false, message: `Đã lấy IP máy chủ: ${detected}. Vui lòng kiểm tra Firewall nếu ping thất bại.` });
        } else {
          setPingResult({ online: false, message: 'Không thể tự động tìm máy chủ. Đảm bảo điện thoại và máy tính cùng chung Wi-Fi.' });
        }
      }
    } catch (e) {
      setPingResult({ online: false, message: e.message });
    } finally {
      setIsTesting(false);
    }
  };

  const handleSaveServer = async () => {
    if (!serverUrl.trim()) return;
    const clean = await AppConfig.setServerUrl(serverUrl);
    setServerUrl(clean);
    setSavedServerUrl(clean);
    Alert.alert('Đã lưu', 'Điện thoại sẽ dùng địa chỉ máy chủ mới.');
    onServerSaved();
  };

  const updateFeedback = async (next) => {
    const value = { hapticEnabled, soundEnabled, ...next };
    setHapticEnabled(value.hapticEnabled);
    setSoundEnabled(value.soundEnabled);
    await AppConfig.setFeedback(value);
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

  const confirmUnpair = () => {
    Alert.alert('Hủy ghép quầy', `Điện thoại sẽ ngừng gửi mã vào ${pairing?.terminalName}.`, [
      { text: 'Không', style: 'cancel' },
      { text: 'Hủy ghép', style: 'destructive', onPress: onUnpair }
    ]);
  };

  const confirmLogout = () => {
    Alert.alert('Đăng xuất', 'Bạn cần đăng nhập lại để tiếp tục quét.', [
      { text: 'Không', style: 'cancel' },
      { text: 'Đăng xuất', style: 'destructive', onPress: onLogout }
    ]);
  };

  const name = session?.fullName || session?.username || '';

  return (
    <View style={{ flex: 1 }}>
      <ScreenHeader title="Cài đặt" subtitle="Quầy đã ghép, tài khoản và máy chủ" isOnline={isOnline} />

      <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
        <SectionTitle>Quầy POS nhận mã</SectionTitle>
        <Card style={styles.section}>
          <View style={styles.rowCenter}>
            <View style={[styles.iconBox, { backgroundColor: pairing ? colors.primarySoft : colors.warningSoft }]}>
              <Icon name={pairing ? 'link' : 'link-2'} size={18} color={pairing ? colors.primary : colors.warningDark} />
            </View>
            <View style={{ flex: 1 }}>
              <Text style={type.heading}>{pairing ? pairing.terminalName : 'Chưa ghép quầy'}</Text>
              <Text style={type.small}>
                {pairing ? 'Mã quét ở chế độ "Gửi vào giỏ POS" sẽ vào giỏ quầy này' : 'Quét mã QR trên máy POS ở tab Quét'}
              </Text>
            </View>
          </View>

          <Text style={[type.small, { marginTop: 14, marginBottom: 6 }]}>
            Camera không đọc được QR? Nhập mã 6 số hiện dưới QR:
          </Text>
          <View style={styles.rowCenter}>
            <TextInput
              style={[styles.input, styles.codeInput]}
              value={pairCode}
              onChangeText={setPairCode}
              placeholder="000000"
              placeholderTextColor={colors.textLight}
              keyboardType="number-pad"
              maxLength={6}
            />
            <Button label="Ghép" icon="link" onPress={handleManualPair} loading={isPairing} compact style={{ minHeight: 46 }} />
          </View>

          {pairing && (
            <Button label="Hủy ghép quầy" icon="slash" variant="danger" onPress={confirmUnpair} style={{ marginTop: 12 }} />
          )}
        </Card>

        <SectionTitle>Tài khoản</SectionTitle>
        <Card style={styles.section}>
          <View style={styles.rowCenter}>
            <View style={styles.avatar}>
              <Text style={styles.avatarText}>{name.trim().charAt(0).toUpperCase() || '?'}</Text>
            </View>
            <View style={{ flex: 1 }}>
              <Text style={type.heading}>{name}</Text>
              <Text style={type.small}>
                {ROLE_LABELS[session?.role] || session?.role || ''}
                {session?.branchName
                  ? ` · ${session.branchName}`
                  : session?.branchId
                    ? ` · Chi nhánh #${session.branchId}`
                    : ''}
              </Text>
            </View>
          </View>
          <Button label="Đăng xuất" icon="log-out" variant="danger" onPress={confirmLogout} style={{ marginTop: 12 }} />
        </Card>

        <SectionTitle>Dữ liệu trên máy</SectionTitle>
        <Card style={styles.section}>
          <InfoRow icon="package" label="Danh mục sản phẩm" value={`${cachedCount} sản phẩm`} />
          <InfoRow icon="clock" label="Lần tải cuối" value={lastSyncTime ? formatDate(lastSyncTime) : 'Chưa tải'} />
          <TouchableOpacity onPress={onOpenQueue} activeOpacity={0.7}>
            <InfoRow
              icon="upload-cloud"
              label="Chờ đồng bộ"
              value={`${queueCount} mã`}
              valueColor={queueCount > 0 ? colors.warningDark : undefined}
              chevron
            />
          </TouchableOpacity>
          <Button
            label="Tải danh mục để dùng khi mất mạng"
            icon="download"
            variant="secondary"
            onPress={onSyncCatalog}
            loading={isSyncingCatalog}
            style={{ marginTop: 10 }}
          />
        </Card>

        <SectionTitle>Máy chủ POS</SectionTitle>
        <Card style={styles.section}>
          <TextInput
            style={styles.input}
            value={serverUrl}
            onChangeText={(v) => {
              setServerUrl(v);
              setPingResult(null);
            }}
            placeholder="http://192.168.1.15:8080"
            placeholderTextColor={colors.textLight}
            autoCapitalize="none"
            autoCorrect={false}
            keyboardType="url"
          />
          <View style={[styles.rowCenter, { marginTop: 8, flexWrap: 'wrap', gap: 6 }]}>
            <TouchableOpacity
              style={[styles.preset, { backgroundColor: '#f0f9ff', borderColor: '#bae6fd' }]}
              onPress={handleAutoDetect}
            >
              <Text style={[styles.presetText, { color: '#0284c7', fontFamily: fonts.medium }]}>
                ⚡ Tự động tìm IP máy chủ
              </Text>
            </TouchableOpacity>
            {[
              { label: 'Máy ảo Android', url: 'http://10.0.2.2:8080' },
              { label: 'Localhost', url: 'http://localhost:8080' }
            ].map((p) => (
              <TouchableOpacity key={p.url} style={styles.preset} onPress={() => setServerUrl(p.url)}>
                <Text style={styles.presetText}>{p.label}</Text>
              </TouchableOpacity>
            ))}
          </View>

          {pingResult && (
            <View
              style={[
                styles.ping,
                pingResult.online
                  ? { backgroundColor: colors.primarySoft, borderColor: colors.primaryBorder }
                  : { backgroundColor: colors.warningSoft, borderColor: colors.warningBorder }
              ]}
            >
              <Icon
                name={pingResult.online ? 'check-circle' : 'alert-triangle'}
                size={15}
                color={pingResult.online ? colors.primary : colors.warningDark}
              />
              <Text style={[type.small, { flex: 1, color: pingResult.online ? colors.primaryDark : colors.warningDark }]}>
                {pingResult.message}
              </Text>
            </View>
          )}

          <View style={[styles.rowCenter, { marginTop: 12 }]}>
            <Button
              label="Kiểm tra"
              icon="activity"
              variant="secondary"
              onPress={handleTestPing}
              loading={isTesting}
              style={{ flex: 1 }}
            />
            <Button
              label="Lưu"
              icon="check"
              onPress={handleSaveServer}
              disabled={!serverUrl.trim() || serverUrl.trim() === savedServerUrl}
              style={{ flex: 1 }}
            />
          </View>
        </Card>

        <SectionTitle>Phản hồi khi quét</SectionTitle>
        <Card style={[styles.section, { paddingVertical: 6 }]}>
          <SwitchRow
            icon="smartphone"
            label="Rung khi quét"
            value={hapticEnabled}
            onChange={(v) => updateFeedback({ hapticEnabled: v })}
          />
          <View style={styles.divider} />
          <SwitchRow
            icon="volume-2"
            label="Tiếng bíp"
            value={soundEnabled}
            onChange={(v) => updateFeedback({ soundEnabled: v })}
          />
        </Card>
      </ScrollView>
    </View>
  );
}

function InfoRow({ icon, label, value, valueColor, chevron }) {
  return (
    <View style={styles.infoRow}>
      <Icon name={icon} size={16} color={colors.textMuted} />
      <Text style={[type.body, { flex: 1 }]}>{label}</Text>
      <Text style={[type.bodyMedium, valueColor && { color: valueColor }]}>{value}</Text>
      {chevron ? <Icon name="chevron-right" size={16} color={colors.textLight} /> : null}
    </View>
  );
}

function SwitchRow({ icon, label, value, onChange }) {
  return (
    <View style={styles.infoRow}>
      <Icon name={icon} size={16} color={colors.textMuted} />
      <Text style={[type.body, { flex: 1 }]}>{label}</Text>
      <Switch
        value={value}
        onValueChange={onChange}
        trackColor={{ false: colors.borderStrong, true: colors.primary }}
        thumbColor={colors.white}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  content: {
    paddingHorizontal: 16,
    paddingBottom: 32
  },
  section: {
    marginBottom: 18
  },
  rowCenter: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10
  },
  iconBox: {
    width: 40,
    height: 40,
    borderRadius: radius.md,
    alignItems: 'center',
    justifyContent: 'center'
  },
  avatar: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: colors.primary,
    alignItems: 'center',
    justifyContent: 'center'
  },
  avatarText: {
    fontFamily: fonts.bold,
    fontSize: 16,
    color: colors.white
  },
  input: {
    minHeight: 46,
    backgroundColor: colors.white,
    borderWidth: 1,
    borderColor: colors.borderStrong,
    borderRadius: radius.md,
    paddingHorizontal: 12,
    fontFamily: fonts.regular,
    fontSize: 15,
    color: colors.text
  },
  codeInput: {
    flex: 1,
    fontFamily: fonts.mono,
    letterSpacing: 4
  },
  preset: {
    backgroundColor: colors.subtle,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 999,
    paddingHorizontal: 12,
    paddingVertical: 6
  },
  presetText: {
    fontFamily: fonts.medium,
    fontSize: 12,
    color: colors.textMuted
  },
  ping: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    borderWidth: 1,
    borderRadius: radius.md,
    padding: 10,
    marginTop: 10
  },
  infoRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    minHeight: 44
  },
  divider: {
    height: 1,
    backgroundColor: colors.border
  }
});
