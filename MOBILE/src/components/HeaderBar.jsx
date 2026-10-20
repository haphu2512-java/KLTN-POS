import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';

export default function HeaderBar({
  isOnline,
  scanMode,
  pairing,
  onOpenPairing,
  onToggleMode,
  queueCount,
  onOpenQueue,
  onOpenSettings,
  onOpenHistory
}) {
  return (
    <View style={styles.container}>
      <View style={styles.topRow}>
        <View style={styles.brandContainer}>
          <Text style={styles.brandTitle}>POS SCANNER</Text>
          <View style={styles.statusRow}>
            <View style={[styles.statusDot, { backgroundColor: isOnline ? '#10b981' : '#ef4444' }]} />
            <Text style={styles.statusText}>
              {isOnline ? 'Online (LAN)' : 'Offline (Local)'}
            </Text>
          </View>
        </View>

        <View style={styles.actionsRow}>
          {/* Offline Queue Badge Button */}
          {queueCount > 0 && (
            <TouchableOpacity style={styles.queueBtn} onPress={onOpenQueue} activeOpacity={0.7}>
              <Text style={styles.queueIcon}>📥</Text>
              <View style={styles.queueBadge}>
                <Text style={styles.queueBadgeText}>{queueCount}</Text>
              </View>
            </TouchableOpacity>
          )}

          {/* History Button */}
          <TouchableOpacity style={styles.iconBtn} onPress={onOpenHistory} activeOpacity={0.7}>
            <Text style={styles.iconBtnText}>🕒</Text>
          </TouchableOpacity>

          {/* Settings Button */}
          <TouchableOpacity style={styles.iconBtn} onPress={onOpenSettings} activeOpacity={0.7}>
            <Text style={styles.iconBtnText}>⚙️</Text>
          </TouchableOpacity>
        </View>
      </View>

      {/* Quầy POS đang nhận mã quét */}
      <TouchableOpacity
        style={[styles.pairingChip, !pairing && styles.pairingChipMissing]}
        onPress={onOpenPairing}
        activeOpacity={0.8}
      >
        <Text style={styles.pairingText} numberOfLines={1}>
          {pairing
            ? `🔗 Đang ghép: ${pairing.terminalName}`
            : '⚠️ Chưa ghép quầy POS · quét QR trên máy POS'}
        </Text>
      </TouchableOpacity>

      {/* Mode Switcher Tabs */}
      <View style={styles.modeTabs}>
        <TouchableOpacity
          style={[styles.modeTab, scanMode === 'POS_REMOTE' && styles.modeTabActive]}
          onPress={() => onToggleMode('POS_REMOTE')}
          activeOpacity={0.8}
        >
          <Text style={[styles.modeTabText, scanMode === 'POS_REMOTE' && styles.modeTabTextActive]}>
            🛒 Bắn vào POS
          </Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={[styles.modeTab, scanMode === 'PRICE_CHECK' && styles.modeTabActiveBlue]}
          onPress={() => onToggleMode('PRICE_CHECK')}
          activeOpacity={0.8}
        >
          <Text style={[styles.modeTabText, scanMode === 'PRICE_CHECK' && styles.modeTabTextActive]}>
            🔍 Tra cứu giá & Tồn kho
          </Text>
        </TouchableOpacity>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    backgroundColor: '#0f172a',
    paddingTop: 12,
    paddingBottom: 10,
    paddingHorizontal: 16,
    borderBottomWidth: 1,
    borderBottomColor: '#1e293b'
  },
  pairingChip: {
    backgroundColor: '#064e3b',
    borderRadius: 8,
    paddingHorizontal: 10,
    paddingVertical: 6,
    marginBottom: 10
  },
  pairingChipMissing: {
    backgroundColor: '#78350f'
  },
  pairingText: {
    color: '#f8fafc',
    fontSize: 12,
    fontWeight: '600'
  },
  topRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 10
  },
  brandContainer: {
    flexDirection: 'column'
  },
  brandTitle: {
    color: '#f8fafc',
    fontSize: 18,
    fontWeight: '800',
    letterSpacing: 0.5
  },
  statusRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: 2
  },
  statusDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    marginRight: 6
  },
  statusText: {
    color: '#94a3b8',
    fontSize: 11,
    fontWeight: '500'
  },
  actionsRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8
  },
  queueBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#334155',
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 20,
    position: 'relative'
  },
  queueIcon: {
    fontSize: 14,
    marginRight: 4
  },
  queueBadge: {
    backgroundColor: '#f59e0b',
    borderRadius: 10,
    paddingHorizontal: 6,
    paddingVertical: 1
  },
  queueBadgeText: {
    color: '#0f172a',
    fontSize: 11,
    fontWeight: '700'
  },
  iconBtn: {
    backgroundColor: '#1e293b',
    width: 36,
    height: 36,
    borderRadius: 18,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    borderColor: '#334155'
  },
  iconBtnText: {
    fontSize: 16
  },
  modeTabs: {
    flexDirection: 'row',
    backgroundColor: '#1e293b',
    borderRadius: 8,
    padding: 3,
    gap: 4
  },
  modeTab: {
    flex: 1,
    paddingVertical: 7,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 6
  },
  modeTabActive: {
    backgroundColor: '#059669'
  },
  modeTabActiveBlue: {
    backgroundColor: '#2563eb'
  },
  modeTabText: {
    color: '#94a3b8',
    fontSize: 12,
    fontWeight: '600'
  },
  modeTabTextActive: {
    color: '#ffffff',
    fontWeight: '700'
  }
});
