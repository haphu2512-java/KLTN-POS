import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform
} from 'react-native';
import { AppConfig } from '../api/config';
import { ScannerApi } from '../api/scannerApi';

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
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <Text style={styles.title}>Đăng nhập máy quét</Text>
      <Text style={styles.subtitle}>
        Dùng cùng tài khoản đang đăng nhập trên máy POS. Mã quét sẽ chỉ được gửi tới quầy đó.
      </Text>

      <Text style={styles.label}>Địa chỉ máy chủ POS</Text>
      <TextInput
        style={styles.input}
        value={serverUrl}
        onChangeText={setServerUrl}
        placeholder="http://192.168.1.15:8080"
        placeholderTextColor="#64748b"
        autoCapitalize="none"
        autoCorrect={false}
        keyboardType="url"
      />

      {/* Quick IP Presets */}
      <View style={styles.presetsRow}>
        {process.env.EXPO_PUBLIC_SERVER_URL ? (
          <TouchableOpacity
            style={styles.presetChip}
            onPress={() => setServerUrl(process.env.EXPO_PUBLIC_SERVER_URL)}
          >
            <Text style={styles.presetText}>⚡ .env: {process.env.EXPO_PUBLIC_SERVER_URL}</Text>
          </TouchableOpacity>
        ) : null}
        <TouchableOpacity
          style={styles.presetChip}
          onPress={() => setServerUrl('http://10.0.2.2:8080')}
        >
          <Text style={styles.presetText}>Android Emu (10.0.2.2)</Text>
        </TouchableOpacity>
      </View>

      <Text style={styles.label}>Tên đăng nhập</Text>
      <TextInput
        style={styles.input}
        value={username}
        onChangeText={setUsername}
        placeholder="cashier"
        placeholderTextColor="#64748b"
        autoCapitalize="none"
        autoCorrect={false}
      />

      <Text style={styles.label}>Mật khẩu</Text>
      <TextInput
        style={styles.input}
        value={password}
        onChangeText={setPassword}
        placeholder="••••••"
        placeholderTextColor="#64748b"
        secureTextEntry
        onSubmitEditing={handleLogin}
      />

      {error ? <Text style={styles.error}>{error}</Text> : null}

      <TouchableOpacity
        style={styles.loginBtn}
        onPress={handleLogin}
        disabled={isLoading}
        activeOpacity={0.8}
      >
        {isLoading ? (
          <ActivityIndicator size="small" color="#fff" />
        ) : (
          <Text style={styles.loginBtnText}>Đăng nhập</Text>
        )}
      </TouchableOpacity>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#0f172a',
    paddingHorizontal: 24,
    justifyContent: 'center'
  },
  title: {
    color: '#f8fafc',
    fontSize: 24,
    fontWeight: '700',
    marginBottom: 8
  },
  subtitle: {
    color: '#94a3b8',
    fontSize: 14,
    lineHeight: 20,
    marginBottom: 24
  },
  label: {
    color: '#cbd5e1',
    fontSize: 13,
    fontWeight: '600',
    marginBottom: 6
  },
  input: {
    backgroundColor: '#1e293b',
    color: '#f8fafc',
    borderRadius: 10,
    paddingHorizontal: 14,
    paddingVertical: 12,
    fontSize: 15,
    marginBottom: 16,
    borderWidth: 1,
    borderColor: '#334155'
  },
  error: {
    color: '#fca5a5',
    fontSize: 13,
    marginBottom: 12
  },
  loginBtn: {
    backgroundColor: '#0284c7',
    borderRadius: 10,
    paddingVertical: 14,
    alignItems: 'center',
    minHeight: 48,
    justifyContent: 'center'
  },
  loginBtnText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '700'
  },
  presetsRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
    marginBottom: 16
  },
  presetChip: {
    backgroundColor: '#1e293b',
    borderWidth: 1,
    borderColor: '#0284c7',
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 8
  },
  presetText: {
    color: '#38bdf8',
    fontSize: 12,
    fontWeight: '600'
  }
});
