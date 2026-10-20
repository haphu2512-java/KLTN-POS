import React from 'react';
import {
  View,
  Text,
  Modal,
  TouchableOpacity,
  FlatList,
  StyleSheet
} from 'react-native';
import { OfflineStorage } from '../storage/offlineStorage';
import { formatCurrency } from '../utils/formatters';

export default function HistoryModal({
  visible,
  history,
  onClose,
  onHistoryUpdated,
  onSelectBarcode
}) {
  const handleClearHistory = async () => {
    await OfflineStorage.clearScanHistory();
    onHistoryUpdated();
  };

  const getStatusBadge = (status) => {
    switch (status) {
      case 'SENT':
        return { text: '✓ Đã gửi POS', bg: '#064e3b', color: '#34d399' };
      case 'OFFLINE_QUEUED':
        return { text: '💾 Lưu Offline', bg: '#451a03', color: '#fbbf24' };
      case 'LOOKUP':
        return { text: 'Tra giá', bg: '#1e3a8a', color: '#93c5fd' };
      case 'NOT_FOUND':
        return { text: '⚠️ Chưa có mã', bg: '#334155', color: '#94a3b8' };
      default:
        return { text: status, bg: '#1e293b', color: '#cbd5e1' };
    }
  };

  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
      <View style={styles.overlay}>
        <View style={styles.container}>
          {/* Header */}
          <View style={styles.header}>
            <View>
              <Text style={styles.title}>Lịch sử quét mã</Text>
              <Text style={styles.subtitle}>{history.length} lần quét gần nhất</Text>
            </View>
            <TouchableOpacity onPress={onClose} style={styles.closeBtn}>
              <Text style={styles.closeText}>✕</Text>
            </TouchableOpacity>
          </View>

          {/* List */}
          {history.length === 0 ? (
            <View style={styles.emptyContainer}>
              <Text style={styles.emptyIcon}>🕒</Text>
              <Text style={styles.emptyText}>Chưa có lịch sử quét</Text>
              <Text style={styles.emptySubtext}>
                Các mã vạch bạn quét bằng Camera hoặc nhập tay sẽ hiển thị tại đây.
              </Text>
            </View>
          ) : (
            <FlatList
              data={history}
              keyExtractor={(item) => item.id}
              contentContainerStyle={styles.listContent}
              renderItem={({ item }) => {
                const badge = getStatusBadge(item.status);
                return (
                  <TouchableOpacity
                    style={styles.historyItem}
                    onPress={() => {
                      onSelectBarcode(item.barcode);
                      onClose();
                    }}
                    activeOpacity={0.7}
                  >
                    <View style={styles.itemMain}>
                      <Text style={styles.itemBarcode}>{item.barcode}</Text>
                      <Text style={styles.itemName} numberOfLines={1}>
                        {item.product?.name || 'Sản phẩm chưa đăng ký'}
                      </Text>
                      {item.product && (
                        <Text style={styles.itemPrice}>
                          {formatCurrency(item.product.salePrice ?? item.product.price ?? 0)}
                        </Text>
                      )}
                    </View>

                    <View style={styles.itemRight}>
                      <View style={[styles.badge, { backgroundColor: badge.bg }]}>
                        <Text style={[styles.badgeText, { color: badge.color }]}>
                          {badge.text}
                        </Text>
                      </View>
                      <Text style={styles.itemTime}>{item.timestamp}</Text>
                    </View>
                  </TouchableOpacity>
                );
              }}
            />
          )}

          {/* Footer Actions */}
          <View style={styles.footer}>
            {history.length > 0 && (
              <TouchableOpacity
                style={styles.clearBtn}
                onPress={handleClearHistory}
                activeOpacity={0.8}
              >
                <Text style={styles.clearBtnText}>Xóa lịch sử</Text>
              </TouchableOpacity>
            )}

            <TouchableOpacity style={styles.closeActionBtn} onPress={onClose} activeOpacity={0.8}>
              <Text style={styles.closeActionText}>Đóng</Text>
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
  historyItem: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#1e293b',
    borderRadius: 12,
    padding: 12,
    marginBottom: 10,
    borderWidth: 1,
    borderColor: '#334155'
  },
  itemMain: {
    flex: 1,
    marginRight: 10
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
  itemPrice: {
    color: '#10b981',
    fontSize: 12,
    fontWeight: '700',
    marginTop: 2
  },
  itemRight: {
    alignItems: 'flex-end'
  },
  badge: {
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 6,
    marginBottom: 4
  },
  badgeText: {
    fontSize: 10,
    fontWeight: '700'
  },
  itemTime: {
    color: '#64748b',
    fontSize: 10
  },
  footer: {
    gap: 8,
    borderTopWidth: 1,
    borderTopColor: '#1e293b',
    paddingTop: 12
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
  closeActionBtn: {
    paddingVertical: 8,
    alignItems: 'center'
  },
  closeActionText: {
    color: '#94a3b8',
    fontSize: 13
  }
});
