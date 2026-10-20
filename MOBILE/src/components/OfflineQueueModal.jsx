import React, { useState } from 'react';
import {
  View,
  Text,
  Modal,
  TouchableOpacity,
  FlatList,
  StyleSheet,
  ActivityIndicator,
  Alert
} from 'react-native';
import { ScannerApi, AuthError } from '../api/scannerApi';
import { OfflineStorage } from '../storage/offlineStorage';
import { formatDate } from '../utils/formatters';

export default function OfflineQueueModal({
  visible,
  queue,
  onClose,
  onQueueUpdated,
  onAuthError
}) {
  const [isSyncing, setIsSyncing] = useState(false);

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
        <View style={styles.container}>
          {/* Header */}
          <View style={styles.header}>
            <View>
              <Text style={styles.title}>Hàng đợi quét Offline</Text>
              <Text style={styles.subtitle}>
                {queue.length} mã chờ gửi. Sau khi đồng bộ, xác nhận trên màn hình POS để thêm vào giỏ.
              </Text>
            </View>
            <TouchableOpacity onPress={onClose} style={styles.closeBtn}>
              <Text style={styles.closeText}>✕</Text>
            </TouchableOpacity>
          </View>

          {/* List */}
          {queue.length === 0 ? (
            <View style={styles.emptyContainer}>
              <Text style={styles.emptyIcon}>📦</Text>
              <Text style={styles.emptyText}>Hàng đợi Offline đang trống</Text>
              <Text style={styles.emptySubtext}>
                Khi mất kết nối Wi-Fi, mọi mã vạch quét được sẽ tự động lưu vào đây.
              </Text>
            </View>
          ) : (
            <FlatList
              data={queue}
              keyExtractor={(item) => item.clientScanId}
              contentContainerStyle={styles.listContent}
              renderItem={({ item, index }) => (
                <View style={styles.queueItem}>
                  <View style={styles.itemIndex}>
                    <Text style={styles.itemIndexText}>#{index + 1}</Text>
                  </View>
                  <View style={styles.itemDetails}>
                    <Text style={styles.itemBarcode}>{item.barcode}</Text>
                    <Text style={styles.itemName} numberOfLines={1}>
                      {item.productName || 'Chưa định danh'}
                    </Text>
                    <Text style={styles.itemTime}>{formatDate(item.timestamp)}</Text>
                  </View>
                  <TouchableOpacity
                    style={styles.deleteBtn}
                    onPress={() => handleRemoveItem(item.clientScanId)}
                  >
                    <Text style={styles.deleteBtnText}>✕</Text>
                  </TouchableOpacity>
                </View>
              )}
            />
          )}

          {/* Footer Actions */}
          <View style={styles.footer}>
            {queue.length > 0 && (
              <TouchableOpacity
                style={styles.syncBtn}
                onPress={handleSyncAll}
                disabled={isSyncing}
                activeOpacity={0.8}
              >
                {isSyncing ? (
                  <ActivityIndicator size="small" color="#fff" />
                ) : (
                  <Text style={styles.syncBtnText}>⚡ Đồng bộ toàn bộ ({queue.length})</Text>
                )}
              </TouchableOpacity>
            )}

            {queue.length > 0 && (
              <TouchableOpacity
                style={styles.clearBtn}
                onPress={handleClear}
                disabled={isSyncing}
                activeOpacity={0.8}
              >
                <Text style={styles.clearBtnText}>Xóa hàng đợi</Text>
              </TouchableOpacity>
            )}

            <TouchableOpacity style={styles.backBtn} onPress={onClose} activeOpacity={0.8}>
              <Text style={styles.backBtnText}>Đóng</Text>
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
    height: '75%',
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
  subtitle: {
    color: '#94a3b8',
    fontSize: 12,
    marginTop: 2
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
  emptyContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 30
  },
  emptyIcon: {
    fontSize: 48,
    marginBottom: 12
  },
  emptyText: {
    color: '#cbd5e1',
    fontSize: 16,
    fontWeight: '700',
    marginBottom: 6
  },
  emptySubtext: {
    color: '#64748b',
    fontSize: 12,
    textAlign: 'center',
    lineHeight: 18
  },
  listContent: {
    paddingBottom: 20
  },
  queueItem: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#1e293b',
    borderRadius: 12,
    padding: 12,
    marginBottom: 10,
    borderWidth: 1,
    borderColor: '#334155'
  },
  itemIndex: {
    width: 32,
    height: 32,
    borderRadius: 16,
    backgroundColor: '#334155',
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 12
  },
  itemIndexText: {
    color: '#94a3b8',
    fontSize: 11,
    fontWeight: '700'
  },
  itemDetails: {
    flex: 1
  },
  itemBarcode: {
    color: '#38bdf8',
    fontFamily: 'monospace',
    fontSize: 13,
    fontWeight: '700'
  },
  itemName: {
    color: '#f8fafc',
    fontSize: 12,
    fontWeight: '600',
    marginTop: 2
  },
  itemTime: {
    color: '#64748b',
    fontSize: 10,
    marginTop: 2
  },
  deleteBtn: {
    width: 28,
    height: 28,
    borderRadius: 14,
    backgroundColor: '#334155',
    alignItems: 'center',
    justifyContent: 'center',
    marginLeft: 8
  },
  deleteBtnText: {
    color: '#ef4444',
    fontSize: 12,
    fontWeight: '700'
  },
  footer: {
    gap: 8,
    borderTopWidth: 1,
    borderTopColor: '#1e293b',
    paddingTop: 12
  },
  syncBtn: {
    backgroundColor: '#059669',
    paddingVertical: 14,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center'
  },
  syncBtnText: {
    color: '#fff',
    fontSize: 14,
    fontWeight: '700'
  },
  clearBtn: {
    backgroundColor: '#334155',
    paddingVertical: 10,
    borderRadius: 10,
    alignItems: 'center'
  },
  clearBtnText: {
    color: '#ef4444',
    fontSize: 12,
    fontWeight: '600'
  },
  backBtn: {
    paddingVertical: 8,
    alignItems: 'center'
  },
  backBtnText: {
    color: '#94a3b8',
    fontSize: 13
  }
});
