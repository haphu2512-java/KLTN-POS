import React from 'react';
import { View, Text, TouchableOpacity, FlatList, Alert, StyleSheet } from 'react-native';
import { ScreenHeader, Card, EmptyState, Icon, StatusTag, Button } from '../components/ui';
import { colors, fonts, radius, type } from '../theme';
import { formatCurrency } from '../utils/formatters';

export default function HistoryScreen({ isOnline, history, queueCount, onOpenQueue, onSelectHistory, onClearHistory, onGoScan }) {
  const confirmClear = () => {
    Alert.alert('Xóa lịch sử quét', 'Chỉ xóa danh sách trên điện thoại này. Hàng đợi chờ đồng bộ được giữ nguyên.', [
      { text: 'Không', style: 'cancel' },
      { text: 'Xóa', style: 'destructive', onPress: onClearHistory }
    ]);
  };

  return (
    <View style={{ flex: 1 }}>
      <ScreenHeader
        title="Lịch sử quét"
        subtitle={history.length ? `${history.length} lần quét gần nhất` : 'Lưu 50 lần quét gần nhất'}
        isOnline={isOnline}
      />

      <FlatList
        data={history}
        keyExtractor={(item) => item.id}
        contentContainerStyle={styles.list}
        ListHeaderComponent={
          <>
            {queueCount > 0 && (
              <TouchableOpacity style={styles.queue} onPress={onOpenQueue} activeOpacity={0.8}>
                <Icon name="upload-cloud" size={18} color={colors.warningDark} />
                <View style={{ flex: 1 }}>
                  <Text style={[styles.queueTitle]}>{queueCount} mã chờ đồng bộ</Text>
                  <Text style={type.small}>Mở hàng đợi để gửi lên máy chủ</Text>
                </View>
                <Icon name="chevron-right" size={18} color={colors.textLight} />
              </TouchableOpacity>
            )}
            {history.length > 0 && (
              <View style={styles.toolbar}>
                <Text style={type.small}>Chạm một dòng để xem lại giá và tồn kho</Text>
                <TouchableOpacity onPress={confirmClear} hitSlop={8} style={styles.clear}>
                  <Icon name="trash-2" size={14} color={colors.danger} />
                  <Text style={styles.clearText}>Xóa</Text>
                </TouchableOpacity>
              </View>
            )}
          </>
        }
        ListEmptyComponent={
          <Card>
            <EmptyState
              icon="clock"
              title="Chưa có lần quét nào"
              description="Mỗi mã bạn quét hoặc nhập tay sẽ được ghi lại ở đây, kể cả khi đang mất mạng."
              action={<Button label="Bắt đầu quét" icon="maximize" onPress={onGoScan} />}
            />
          </Card>
        }
        renderItem={({ item }) => (
          <TouchableOpacity style={styles.item} onPress={() => onSelectHistory(item)} activeOpacity={0.7}>
            <View style={{ flex: 1 }}>
              <Text style={type.bodyMedium} numberOfLines={1}>
                {item.product?.name || 'Sản phẩm chưa có trong danh mục'}
              </Text>
              <Text style={styles.meta} numberOfLines={1}>
                {item.barcode}
                {item.product ? `  ·  ${formatCurrency(item.product.salePrice ?? item.product.price ?? 0)}` : ''}
              </Text>
            </View>
            <View style={{ alignItems: 'flex-end', gap: 4 }}>
              <StatusTag status={item.status} />
              <Text style={type.small}>
                {item.timestamp}
                {item.date ? ` · ${item.date}` : ''}
              </Text>
            </View>
          </TouchableOpacity>
        )}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  list: {
    paddingHorizontal: 16,
    paddingBottom: 24
  },
  queue: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    backgroundColor: colors.warningSoft,
    borderWidth: 1,
    borderColor: colors.warningBorder,
    borderRadius: radius.lg,
    padding: 12,
    marginBottom: 12
  },
  queueTitle: {
    fontFamily: fonts.semibold,
    fontSize: 14,
    color: colors.warningDark
  },
  toolbar: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 8
  },
  clear: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4
  },
  clearText: {
    fontFamily: fonts.semibold,
    fontSize: 13,
    color: colors.danger
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
  }
});
