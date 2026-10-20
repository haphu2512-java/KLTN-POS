import AsyncStorage from '@react-native-async-storage/async-storage';
import { Platform } from 'react-native';

const STORAGE_KEYS = {
  SERVER_URL: '@pos_scanner_server_url',
  BRANCH_ID: '@pos_scanner_branch_id',
  SCAN_MODE: '@pos_scanner_mode', // 'POS_REMOTE' | 'PRICE_CHECK'
  HAPTIC_ENABLED: '@pos_scanner_haptic',
  SOUND_ENABLED: '@pos_scanner_sound'
};

// Default server URL based on platform
const getDefaultServerUrl = () => {
  if (Platform.OS === 'android') {
    // 10.0.2.2 is the Android emulator's alias to host loopback interface
    return 'http://10.0.2.2:8080';
  }
  return 'http://localhost:8080';
};

export const AppConfig = {
  async getServerUrl() {
    try {
      const saved = await AsyncStorage.getItem(STORAGE_KEYS.SERVER_URL);
      if (saved) return saved;
    } catch {}
    return getDefaultServerUrl();
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
