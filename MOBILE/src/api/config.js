import AsyncStorage from '@react-native-async-storage/async-storage';
import { Platform } from 'react-native';
import Constants from 'expo-constants';

const STORAGE_KEYS = {
  SERVER_URL: '@pos_scanner_server_url',
  BRANCH_ID: '@pos_scanner_branch_id',
  SCAN_MODE: '@pos_scanner_mode', // 'POS_REMOTE' | 'PRICE_CHECK'
  HAPTIC_ENABLED: '@pos_scanner_haptic',
  SOUND_ENABLED: '@pos_scanner_sound',
  AUTH_SESSION: '@pos_scanner_session', // { token, username, fullName, role, branchId }
  PAIRING: '@pos_scanner_pairing', // { pairingId, terminalName }
  DEVICE_ID: '@pos_scanner_device_id' // mã cố định của điện thoại, sinh một lần
};

// Tự động phát hiện IP của máy tính host thông qua Expo Metro bundler
export const getAutoDetectedHost = () => {
  try {
    const hostUri =
      Constants.expoConfig?.hostUri ||
      Constants.manifest2?.extra?.expoGo?.debuggerHost ||
      Constants.manifest?.debuggerHost;
    if (hostUri) {
      const ip = hostUri.split(':')[0];
      if (ip && ip !== 'localhost' && ip !== '127.0.0.1') {
        return `http://${ip}:8080`;
      }
    }
  } catch {}
  return null;
};

// Default server URL based on Auto-detected Expo host, .env and platform
const getDefaultServerUrl = () => {
  // 1. Tự động lấy IP của máy tính đang chạy Expo (tự cập nhật khi đổi Wi-Fi)
  const autoHost = getAutoDetectedHost();
  if (autoHost) {
    return autoHost;
  }
  // 2. Đọc từ biến EXPO_PUBLIC_SERVER_URL trong .env nếu có
  const envUrl = process.env.EXPO_PUBLIC_SERVER_URL;
  if (envUrl && typeof envUrl === 'string' && envUrl.trim().length > 0) {
    return envUrl.trim().replace(/\/+$/, '');
  }
  if (Platform.OS === 'android') {
    return 'http://10.0.2.2:8080';
  }
  return 'http://localhost:8080';
};

const newDeviceId = () =>
  `${Date.now().toString(36)}-${Array.from({ length: 4 }, () => Math.random().toString(36).substring(2, 10)).join('')}`;

export const AppConfig = {
  // Mã cố định của điện thoại: server dùng để ghép lại thì thay ghép cặp cũ, không tạo thêm dòng mới
  async getDeviceId() {
    try {
      const saved = await AsyncStorage.getItem(STORAGE_KEYS.DEVICE_ID);
      if (saved) return saved;
    } catch {}
    const id = newDeviceId();
    await AsyncStorage.setItem(STORAGE_KEYS.DEVICE_ID, id).catch(() => {});
    return id;
  },

  // Tên hiện trên máy POS để thu ngân phân biệt các điện thoại
  getDeviceName() {
    const name = String(Constants.deviceName || '').trim();
    if (name) return name.substring(0, 100);
    return Platform.OS === 'ios' ? 'iPhone' : 'Điện thoại Android';
  },

  // Quầy POS đã ghép cặp (quét QR trên máy POS); mã quét được bắn vào giỏ của quầy này
  async getPairing() {
    try {
      const raw = await AsyncStorage.getItem(STORAGE_KEYS.PAIRING);
      return raw ? JSON.parse(raw) : null;
    } catch {
      return null;
    }
  },

  async setPairing(pairing) {
    await AsyncStorage.setItem(STORAGE_KEYS.PAIRING, JSON.stringify(pairing));
  },

  async clearPairing() {
    await AsyncStorage.removeItem(STORAGE_KEYS.PAIRING);
  },

  // Phiên đăng nhập của nhân viên đang cầm điện thoại
  async getSession() {
    try {
      const raw = await AsyncStorage.getItem(STORAGE_KEYS.AUTH_SESSION);
      return raw ? JSON.parse(raw) : null;
    } catch {
      return null;
    }
  },

  async setSession(session) {
    await AsyncStorage.setItem(STORAGE_KEYS.AUTH_SESSION, JSON.stringify(session));
  },

  async clearSession() {
    await AsyncStorage.removeItem(STORAGE_KEYS.AUTH_SESSION);
  },

  async getServerUrl() {
    const autoHost = getAutoDetectedHost();
    const envUrl = process.env.EXPO_PUBLIC_SERVER_URL;
    try {
      const saved = await AsyncStorage.getItem(STORAGE_KEYS.SERVER_URL);
      if (saved) {
        // Nếu có IP từ Expo Metro và IP đã lưu là IP cũ/ảo (10.0.2.2 hoặc IP dải mạng cũ)
        if (autoHost && saved !== autoHost) {
          if (
            saved.includes('10.0.2.2') ||
            saved.includes('192.168.100.53') ||
            !saved.startsWith('http')
          ) {
            await AsyncStorage.setItem(STORAGE_KEYS.SERVER_URL, autoHost);
            return autoHost;
          }
        }
        return saved;
      }
    } catch {}
    return getDefaultServerUrl();
  },

  // Tự động dò tìm và kiểm tra kết nối với các ứng viên server URL
  async autoDetectServer() {
    const candidateUrls = [];
    const autoHost = getAutoDetectedHost();
    if (autoHost) candidateUrls.push(autoHost);
    const envUrl = process.env.EXPO_PUBLIC_SERVER_URL;
    if (envUrl && !candidateUrls.includes(envUrl.trim())) {
      candidateUrls.push(envUrl.trim().replace(/\/+$/, ''));
    }
    if (Platform.OS === 'android') candidateUrls.push('http://10.0.2.2:8080');
    candidateUrls.push('http://localhost:8080');

    for (const url of candidateUrls) {
      try {
        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), 2000);
        const res = await fetch(`${url}/api/scanner/ping`, {
          signal: controller.signal
        });
        clearTimeout(timeoutId);
        if (res.ok) {
          await this.setServerUrl(url);
          return { success: true, url };
        }
      } catch {}
    }
    return { success: false, url: candidateUrls[0] || null };
  },

  async setServerUrl(url) {
    const cleanUrl = url.trim().replace(/\/+$/, '');
    await AsyncStorage.setItem(STORAGE_KEYS.SERVER_URL, cleanUrl);
    return cleanUrl;
  },

  async getBranchId() {
    try {
      const saved = await AsyncStorage.getItem(STORAGE_KEYS.BRANCH_ID);
      if (saved) return parseInt(saved, 10);
    } catch {}
    return 1;
  },

  async setBranchId(id) {
    await AsyncStorage.setItem(STORAGE_KEYS.BRANCH_ID, String(id));
  },

  async getScanMode() {
    try {
      const saved = await AsyncStorage.getItem(STORAGE_KEYS.SCAN_MODE);
      if (saved) return saved;
    } catch {}
    return 'POS_REMOTE'; // Default: Remote Scanner to POS active cart
  },

  async setScanMode(mode) {
    await AsyncStorage.setItem(STORAGE_KEYS.SCAN_MODE, mode);
  },

  async getSettings() {
    try {
      const [serverUrl, branchId, scanMode, haptic, sound] = await Promise.all([
        this.getServerUrl(),
        this.getBranchId(),
        this.getScanMode(),
        AsyncStorage.getItem(STORAGE_KEYS.HAPTIC_ENABLED),
        AsyncStorage.getItem(STORAGE_KEYS.SOUND_ENABLED)
      ]);
      return {
        serverUrl,
        branchId,
        scanMode,
        hapticEnabled: haptic !== 'false',
        soundEnabled: sound !== 'false'
      };
    } catch {
      return {
        serverUrl: getDefaultServerUrl(),
        branchId: 1,
        scanMode: 'POS_REMOTE',
        hapticEnabled: true,
        soundEnabled: true
      };
    }
  },

  async setFeedback({ hapticEnabled, soundEnabled }) {
    await Promise.all([
      AsyncStorage.setItem(STORAGE_KEYS.HAPTIC_ENABLED, String(hapticEnabled ?? true)),
      AsyncStorage.setItem(STORAGE_KEYS.SOUND_ENABLED, String(soundEnabled ?? true))
    ]);
  },

  async saveSettings(settings) {
    await Promise.all([
      settings.serverUrl ? this.setServerUrl(settings.serverUrl) : Promise.resolve(),
      settings.branchId ? this.setBranchId(settings.branchId) : Promise.resolve(),
      settings.scanMode ? this.setScanMode(settings.scanMode) : Promise.resolve(),
      AsyncStorage.setItem(STORAGE_KEYS.HAPTIC_ENABLED, String(settings.hapticEnabled ?? true)),
      AsyncStorage.setItem(STORAGE_KEYS.SOUND_ENABLED, String(settings.soundEnabled ?? true))
    ]);
  }
};
