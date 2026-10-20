import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  TextInput,
  StyleSheet,
  ScrollView,
  KeyboardAvoidingView,
  Platform
} from 'react-native';
import { AppConfig } from '../api/config';
import { ScannerApi } from '../api/scannerApi';
import { Icon, Button } from './ui';
import { colors, fonts, radius, type } from '../theme';

// Điện thoại đăng nhập cùng tài khoản với máy POS; mã quét chỉ đi tới POS của tài khoản đó
export default function LoginScreen({ onLoggedIn, notice }) {
  const [serverUrl, setServerUrl] = useState('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(notice || null);

  useEffect(() => {
    AppConfig.getServerUrl().then(setServerUrl);
  }, []);

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
      setError(e.name === 'AbortError' ? `Không kết nối được tới ${serverUrl}` : e.message);
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
          Đăng nhập bằng tài khoản nhân viên của cửa hàng. Mã quét chỉ được gửi tới quầy POS bạn đã ghép.
        </Text>

        <View style={styles.card}>
          <Field label="Địa chỉ máy chủ POS" icon="server">
            <TextInput
              style={styles.input}
              value={serverUrl}
              onChangeText={setServerUrl}
              placeholder="http://192.168.1.15:8080"
              placeholderTextColor={colors.textLight}
              autoCapitalize="none"
              autoCorrect={false}
              keyboardType="url"
            />
          </Field>

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
  errorText: {
    flex: 1,
    fontFamily: fonts.medium,
    fontSize: 13,
    color: colors.danger
  }
});
