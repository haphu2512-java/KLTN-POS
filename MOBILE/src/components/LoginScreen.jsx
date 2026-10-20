import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  TextInput,
  StyleSheet,
  ScrollView,
  KeyboardAvoidingView,
  Platform,
  TouchableOpacity,
  ActivityIndicator
} from 'react-native';
import { AppConfig, getAutoDetectedHost } from '../api/config';
import { ScannerApi } from '../api/scannerApi';
import { Icon, Button } from './ui';
import { colors, fonts, radius, type } from '../theme';

// Điện thoại đăng nhập cùng tài khoản với máy POS; mã quét chỉ đi tới POS của tài khoản đó
export default function LoginScreen({ onLoggedIn, notice }) {
  const [serverUrl, setServerUrl] = useState('');
  const [username, setUsername] = useState('cashier');
  const [password, setPassword] = useState('123456');
  const [isLoading, setIsLoading] = useState(false);
  const [isScanningServer, setIsScanningServer] = useState(false);
  const [scanMessage, setScanMessage] = useState(null);
  const [error, setError] = useState(notice || null);

  useEffect(() => {
    AppConfig.getServerUrl().then(setServerUrl);
  }, []);

  const handleAutoDetect = async () => {
    setIsScanningServer(true);
    setScanMessage(null);
    setError(null);
    try {
      const res = await AppConfig.autoDetectServer();
      if (res.success) {
        setServerUrl(res.url);
        setScanMessage(`✓ Đã kết nối máy chủ: ${res.url}`);
      } else {
        const detected = getAutoDetectedHost();
        if (detected) {
          setServerUrl(detected);
          setScanMessage(`Đã lấy IP máy chủ: ${detected} (Vui lòng kiểm tra Firewall nếu chưa ping được)`);
        } else {
          setError('Không tìm thấy máy chủ POS tự động. Hãy đảm bảo điện thoại và máy tính cùng chung Wi-Fi.');
        }
      }
    } catch (e) {
      setError(e.message);
    } finally {
      setIsScanningServer(false);
    }
  };

  const handleLogin = async () => {
    if (!serverUrl.trim() || !username.trim() || !password) {
      setError('Vui lòng nhập địa chỉ máy chủ, tên đăng nhập và mật khẩu.');
      return;
    }
    setIsLoading(true);
    setError(null);
    try {
      const session = await ScannerApi.login(serverUrl, username, password);
      setPassword('');
      onLoggedIn(session);
    } catch (e) {
      setError(e.name === 'AbortError' ? `Không kết nối được tới ${serverUrl}. Hãy kiểm tra xem điện thoại và máy tính cùng chung Wi-Fi.` : e.message);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      <ScrollView contentContainerStyle={styles.container} keyboardShouldPersistTaps="handled">
        <View style={styles.logo}>
          <Icon name="maximize" size={26} color={colors.white} />
        </View>
        <Text style={styles.title}>Máy quét POS</Text>
        <Text style={styles.subtitle}>
          Đăng nhập bằng tài khoản thu ngân của cửa hàng để đồng bộ quét mã vạch trực tiếp lên quầy bán hàng.
        </Text>

        <View style={styles.card}>
          <Field label="Địa chỉ máy chủ POS" icon="server">
            <TextInput
              style={styles.input}
              value={serverUrl}
              onChangeText={setServerUrl}
              placeholder="http://192.168.0.106:8080"
              placeholderTextColor={colors.textLight}
              autoCapitalize="none"
              autoCorrect={false}
              keyboardType="url"
            />
          </Field>

          {/* Nút tự động dò IP khi đổi Wi-Fi */}
          <View style={styles.detectRow}>
            <TouchableOpacity
              style={styles.detectBtn}
              onPress={handleAutoDetect}
              disabled={isScanningServer}
            >
              {isScanningServer ? (
                <ActivityIndicator size="small" color="#0284c7" />
              ) : (
                <Icon name="refresh-cw" size={13} color="#0284c7" />
              )}
              <Text style={styles.detectBtnText}>
                {isScanningServer ? 'Đang dò máy chủ...' : '⚡ Tự động tìm IP máy chủ POS'}
              </Text>
            </TouchableOpacity>
          </View>

          {scanMessage ? (
            <View style={styles.successBox}>
              <Text style={styles.successText}>{scanMessage}</Text>
            </View>
          ) : null}

          <Field label="Tên đăng nhập" icon="user">
            <TextInput
              style={styles.input}
              value={username}
              onChangeText={setUsername}
              placeholder="cashier"
              placeholderTextColor={colors.textLight}
              autoCapitalize="none"
              autoCorrect={false}
            />
          </Field>

          <Field label="Mật khẩu" icon="lock">
            <TextInput
              style={styles.input}
              value={password}
              onChangeText={setPassword}
              placeholder="Mật khẩu"
              placeholderTextColor={colors.textLight}
              secureTextEntry
              onSubmitEditing={handleLogin}
            />
          </Field>

          {error ? (
            <View style={styles.error}>
              <Icon name="alert-circle" size={15} color={colors.danger} />
              <Text style={styles.errorText}>{error}</Text>
            </View>
          ) : null}

          <Button label="Đăng nhập" onPress={handleLogin} loading={isLoading} style={{ marginTop: 4 }} />
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

function Field({ label, icon, children }) {
  return (
    <View style={{ marginBottom: 14 }}>
      <Text style={[type.label, { marginBottom: 6 }]}>{label}</Text>
      <View style={styles.inputWrap}>
        <Icon name={icon} size={16} color={colors.textLight} />
        {children}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flexGrow: 1,
    justifyContent: 'center',
    paddingHorizontal: 20,
    paddingVertical: 32
  },
  logo: {
    width: 52,
    height: 52,
    borderRadius: radius.lg,
    backgroundColor: colors.primary,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 16
  },
  title: {
    fontFamily: fonts.bold,
    fontSize: 24,
    color: colors.text
  },
  subtitle: {
    fontFamily: fonts.regular,
    fontSize: 14,
    lineHeight: 20,
    color: colors.textMuted,
    marginTop: 6,
    marginBottom: 20
  },
  card: {
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.xl,
    padding: 16
  },
  inputWrap: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    minHeight: 46,
    backgroundColor: colors.white,
    borderWidth: 1,
    borderColor: colors.borderStrong,
    borderRadius: radius.md,
    paddingHorizontal: 12
  },
  input: {
    flex: 1,
    fontFamily: fonts.regular,
    fontSize: 15,
    color: colors.text,
    paddingVertical: 10
  },
  error: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    backgroundColor: colors.dangerSoft,
    borderWidth: 1,
    borderColor: colors.dangerBorder,
    borderRadius: radius.md,
    padding: 10,
    marginBottom: 12
  },
  loginBtnText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '700'
  },
  detectRow: {
    flexDirection: 'row',
    marginBottom: 14,
    marginTop: -4
  },
  detectBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    backgroundColor: '#f0f9ff',
    borderWidth: 1,
    borderColor: '#bae6fd',
    paddingHorizontal: 12,
    paddingVertical: 7,
    borderRadius: radius.md
  },
  detectBtnText: {
    fontFamily: fonts.medium,
    color: '#0284c7',
    fontSize: 12
  },
  successBox: {
    backgroundColor: '#ecfdf5',
    borderWidth: 1,
    borderColor: '#a7f3d0',
    borderRadius: radius.md,
    padding: 10,
    marginBottom: 14
  },
  successText: {
    fontFamily: fonts.regular,
    color: '#059669',
    fontSize: 13
  }
});
