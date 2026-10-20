import React, { useState } from 'react';
import { View, Text, Modal, TouchableOpacity, FlatList, StyleSheet, Alert } from 'react-native';
import { ScannerApi, AuthError } from '../api/scannerApi';
import { OfflineStorage } from '../storage/offlineStorage';
import { formatDate } from '../utils/formatters';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Icon, Button, EmptyState } from './ui';
import { colors, fonts, radius, type } from '../theme';

export default function OfflineQueueModal({
  visible,
  queue,
  onClose,
  onQueueUpdated,
  onAuthError
}) {
  const [isSyncing, setIsSyncing] = useState(false);
  const insets = useSafeAreaInsets();

  const handleSyncAll = async () => {
    if (queue.length === 0) return;
    setIsSyncing(true);
    try {
      const res = await ScannerApi.syncOfflineQueue();
      onQueueUpdated();
      if (res.success) {
        Alert.alert('Thành công', res.message);
        onClose();
      } else {
        Alert.alert('Thất bại', res.message);
      }
    } catch (e) {
      onQueueUpdated();
      if (e instanceof AuthError) {
        onAuthError(e);
      } else {
        Alert.alert('Lỗi', `Không thể đồng bộ: ${e.message}`);
      }
    } finally {
      setIsSyncing(false);
    }
  };

  const handleClear = () => {
    Alert.alert(
      'Xác nhận xóa',
      'Bạn có chắc chắn muốn xóa toàn bộ hàng đợi quét Offline không?',
      [
        { text: 'Hủy', style: 'cancel' },
        {
          text: 'Xóa hết',
          style: 'destructive',
          onPress: async () => {
            await OfflineStorage.clearOfflineQueue();
            onQueueUpdated();
          }
        }
      ]
    );
  };

  const handleRemoveItem = async (clientScanId) => {
    await OfflineStorage.removeOfflineQueueItem(clientScanId);
    onQueueUpdated();
  };

  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
      <View style={styles.overlay}>
        <View style={[styles.container, { paddingBottom: 16 + insets.bottom }]}>
          <View style={styles.header}>
            <View style={{ flex: 1 }}>
              <Text style={type.title}>Chờ đồng bộ</Text>
              <Text style={[type.small, { marginTop: 4, lineHeight: 18 }]}>
                Mã quét lúc mất mạng. Sau khi đồng bộ, thu ngân xác nhận trên máy POS để thêm vào giỏ.
              </Text>
            </View>
            <TouchableOpacity onPress={onClose} style={styles.closeBtn} hitSlop={8}>
              <Icon name="x" size={18} color={colors.textMuted} />
            </TouchableOpacity>
          </View>

          {queue.length === 0 ? (
            <View style={{ flex: 1, justifyContent: 'center' }}>
              <EmptyState
                icon="check-circle"
                title="Không có mã nào chờ gửi"
                description="Khi mất mạng, mã bạn quét ở chế độ Gửi vào giỏ POS sẽ được giữ ở đây cho tới khi đồng bộ."
              />
            </View>
          ) : (
            <FlatList
              data={queue}
              keyExtractor={(item) => item.clientScanId}
              contentContainerStyle={{ paddingBottom: 12 }}
              renderItem={({ item }) => (
                <View style={styles.item}>
                  <View style={{ flex: 1 }}>
                    <Text style={type.bodyMedium} numberOfLines={1}>
                      {item.productName || 'Chưa rõ sản phẩm'}
                    </Text>
                    <Text style={styles.meta}>
                      {item.barcode}  ·  {formatDate(item.timestamp)}
                    </Text>
                  </View>
                  <TouchableOpacity
                    style={styles.deleteBtn}
                    onPress={() => handleRemoveItem(item.clientScanId)}
                    hitSlop={6}
                  >
                    <Icon name="trash-2" size={16} color={colors.danger} />
                  </TouchableOpacity>
                </View>
              )}
            />
          )}

          <View style={styles.footer}>
            {queue.length > 0 && (
              <Button
                label={`Đồng bộ ${queue.length} mã`}
                icon="upload-cloud"
                onPress={handleSyncAll}
                loading={isSyncing}
              />
            )}
            <View style={{ flexDirection: 'row', gap: 10 }}>
              {queue.length > 0 && (
                <Button
                  label="Xóa hết"
                  variant="danger"
                  onPress={handleClear}
                  disabled={isSyncing}
                  style={{ flex: 1 }}
                />
              )}
              <Button label="Đóng" variant="secondary" onPress={onClose} style={{ flex: 1 }} />
            </View>
          </View>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(35, 33, 28, 0.45)',
    justifyContent: 'flex-end'
  },
  container: {
    backgroundColor: colors.canvas,
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
    height: '75%',
    paddingHorizontal: 16,
    paddingTop: 18
  },
  header: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 12,
    marginBottom: 14
  },
  closeBtn: {
    width: 34,
    height: 34,
    borderRadius: 17,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    alignItems: 'center',
    justifyContent: 'center'
  },
  item: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.lg,
    padding: 12,
    marginBottom: 8
  },
  meta: {
    fontFamily: fonts.regular,
    fontVariant: ['tabular-nums'],
    fontSize: 12,
    color: colors.textMuted,
    marginTop: 3
  },
  deleteBtn: {
    width: 34,
    height: 34,
    borderRadius: radius.md,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: colors.dangerSoft
  },
  footer: {
    gap: 10,
    paddingTop: 12,
    borderTopWidth: 1,
    borderTopColor: colors.border
  }
});
