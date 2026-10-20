import React from 'react';
import { View, Text, StyleSheet, Modal, TouchableOpacity } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Icon, Button, stockMeta } from './ui';
import { colors, fonts, radius, type } from '../theme';
import { formatCurrency } from '../utils/formatters';

// result.mode: 'POS_REMOTE' (vừa gửi vào giỏ) | 'PRICE_CHECK' (chỉ tra giá)
export default function ProductResultSheet({
  visible,
  result,
  canSendToPos,
  canAssignBarcode,
  closeLabel,
  onClose,
  onSendToPos,
  onAssignBarcode
}) {
  const insets = useSafeAreaInsets();
  if (!result) return null;

  const { product, barcode, online, message, found, mode } = result;
  const stock = stockMeta(product?.stock);
  const isPosMode = mode === 'POS_REMOTE';
  // Mã vừa vào hàng đợi offline thì không cho gửi lại, tránh thêm trùng khi đồng bộ
  const showSend = found && (isPosMode ? online : canSendToPos);
  // Gán mã cần máy chủ, nên chỉ hiện khi đang trực tuyến
  const showAssign = !found && online && canAssignBarcode;

  const tone = !online
    ? { icon: 'cloud-off', fg: colors.warningDark, bg: colors.warningSoft, border: colors.warningBorder }
    : found
      ? { icon: isPosMode ? 'check-circle' : 'info', fg: colors.primaryDark, bg: colors.primarySoft, border: colors.primaryBorder }
      : { icon: 'alert-circle', fg: colors.danger, bg: colors.dangerSoft, border: colors.dangerBorder };

  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
      <View style={styles.overlay}>
        <TouchableOpacity style={{ flex: 1 }} activeOpacity={1} onPress={onClose} />
        <View style={[styles.sheet, { paddingBottom: 16 + insets.bottom }]}>
          <View style={styles.handle} />

          {message ? (
            <View style={[styles.status, { backgroundColor: tone.bg, borderColor: tone.border }]}>
              <Icon name={tone.icon} size={16} color={tone.fg} />
              <Text style={[styles.statusText, { color: tone.fg }]} numberOfLines={3}>
                {message}
              </Text>
            </View>
          ) : null}

          {found && product ? (
            <>
              <Text style={styles.name}>{product.name}</Text>
              <Text style={styles.barcode}>{barcode}</Text>

              <View style={styles.priceRow}>
                <View>
                  <Text style={type.label}>GIÁ BÁN</Text>
                  <Text style={styles.price}>{formatCurrency(product.salePrice ?? product.price ?? 0)}</Text>
                </View>
                {stock ? (
                  <View style={[styles.stock, { backgroundColor: stock.bg }]}>
                    <Text style={[styles.stockText, { color: stock.fg }]}>{stock.label}</Text>
                  </View>
                ) : null}
              </View>

              <View style={styles.metaRow}>
                {product.category ? <Meta icon="grid" text={product.category} /> : null}
                {product.unit ? <Meta icon="box" text={`Đơn vị: ${product.unit}`} /> : null}
              </View>
            </>
          ) : (
            <View style={{ marginBottom: 6 }}>
              <Text style={styles.name}>Không tìm thấy sản phẩm</Text>
              <Text style={styles.barcode}>{barcode}</Text>
              <Text style={[type.small, { marginTop: 8, lineHeight: 18 }]}>
                {canAssignBarcode
                  ? 'Mã vạch này chưa gắn với sản phẩm nào. Nếu sản phẩm đã có trong danh mục, hãy gán mã này cho nó.'
                  : 'Mã vạch này chưa có trong danh mục của cửa hàng. Báo quản lý để gán mã hoặc thêm sản phẩm.'}
              </Text>
            </View>
          )}

          <View style={styles.actions}>
            {showSend && (
              <Button
                label={isPosMode ? 'Gửi lại' : 'Thêm vào giỏ POS'}
                icon="shopping-cart"
                variant="secondary"
                onPress={() => onSendToPos(barcode)}
                style={{ flex: 1 }}
              />
            )}
            {showAssign && (
              <Button
                label="Gán mã cho sản phẩm"
                icon="link"
                variant="secondary"
                onPress={() => onAssignBarcode(barcode)}
                style={{ flex: 1 }}
              />
            )}
            <Button label={closeLabel || 'Tiếp tục quét'} onPress={onClose} style={{ flex: 1 }} />
          </View>
        </View>
      </View>
    </Modal>
  );
}

function Meta({ icon, text }) {
  return (
    <View style={styles.meta}>
      <Icon name={icon} size={12} color={colors.textMuted} />
      <Text style={styles.metaText}>{text}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(35, 33, 28, 0.45)'
  },
  sheet: {
    backgroundColor: colors.surface,
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
    paddingHorizontal: 20,
    paddingTop: 10
  },
  handle: {
    width: 40,
    height: 4,
    borderRadius: 2,
    backgroundColor: colors.borderStrong,
    alignSelf: 'center',
    marginBottom: 14
  },
  status: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    borderWidth: 1,
    borderRadius: radius.md,
    padding: 10,
    marginBottom: 14
  },
  statusText: {
    flex: 1,
    fontFamily: fonts.medium,
    fontSize: 13
  },
  name: {
    fontFamily: fonts.bold,
    fontSize: 18,
    color: colors.text,
    lineHeight: 24
  },
  barcode: {
    fontFamily: fonts.mono,
    fontSize: 13,
    color: colors.textMuted,
    marginTop: 4
  },
  priceRow: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    justifyContent: 'space-between',
    marginTop: 14,
    paddingTop: 14,
    borderTopWidth: 1,
    borderTopColor: colors.border
  },
  price: {
    fontFamily: fonts.bold,
    fontSize: 26,
    color: colors.primary,
    marginTop: 2
  },
  stock: {
    paddingHorizontal: 10,
    paddingVertical: 5,
    borderRadius: radius.sm
  },
  stockText: {
    fontFamily: fonts.semibold,
    fontSize: 13
  },
  metaRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
    marginTop: 12
  },
  meta: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
    backgroundColor: colors.subtle,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.sm,
    paddingHorizontal: 8,
    paddingVertical: 4
  },
  metaText: {
    fontFamily: fonts.medium,
    fontSize: 12,
    color: colors.textMuted
  },
  actions: {
    flexDirection: 'row',
    gap: 10,
    marginTop: 18
  }
});
