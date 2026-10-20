import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet, Modal } from 'react-native';
import { formatCurrency } from '../utils/formatters';

export default function ProductResultSheet({
  visible,
  result,
  scanMode,
  onClose,
  onResendToPos
}) {
  if (!result) return null;

  const { product, barcode, online, queued, message, found } = result;

  const getStockBadge = (stock) => {
    if (stock == null) return null;
    if (stock <= 0) {
      return { text: 'Hết hàng (0)', bg: '#ef4444', color: '#fff' };
    }
    if (stock <= 5) {
      return { text: `Sắp hết (${stock})`, bg: '#f59e0b', color: '#000' };
    }
    return { text: `Còn hàng (${stock})`, bg: '#10b981', color: '#fff' };
  };

  const stockBadge = getStockBadge(product?.stock);

  return (
    <Modal
      visible={visible}
      transparent
      animationType="slide"
      onRequestClose={onClose}
    >
      <View style={styles.overlay}>
        <View style={styles.sheetContainer}>
          {/* Header indicator */}
          <View style={styles.dragIndicator} />

          {/* Status banner */}
          <View
            style={[
              styles.statusBanner,
              { backgroundColor: online ? '#064e3b' : '#451a03' }
            ]}
          >
            <Text style={styles.statusBannerIcon}>{online ? '⚡' : '💾'}</Text>
            <Text
              style={[
                styles.statusBannerText,
                { color: online ? '#34d399' : '#fbbf24' }
              ]}
              numberOfLines={2}
            >
              {message}
            </Text>
          </View>

          {/* Barcode badge */}
          <View style={styles.barcodeRow}>
            <Text style={styles.barcodeLabel}>MÃ VẠCH (BARCODE):</Text>
            <View style={styles.barcodeTag}>
              <Text style={styles.barcodeText}>{barcode}</Text>
            </View>
          </View>

          {/* Product Info */}
          {found && product ? (
            <View style={styles.productBox}>
              <Text style={styles.productName}>{product.name}</Text>
              
              <View style={styles.metaRow}>
                {product.category ? (
                  <View style={styles.metaTag}>
                    <Text style={styles.metaText}>{product.category}</Text>
                  </View>
                ) : null}
                {product.unit ? (
                  <View style={styles.metaTag}>
                    <Text style={styles.metaText}>ĐVT: {product.unit}</Text>
                  </View>
                ) : null}
                {stockBadge ? (
                  <View style={[styles.metaTag, { backgroundColor: stockBadge.bg }]}>
                    <Text style={[styles.metaText, { color: stockBadge.color, fontWeight: '700' }]}>
                      {stockBadge.text}
                    </Text>
                  </View>
                ) : null}
              </View>

              {/* Price Row */}
              <View style={styles.priceRow}>
                <Text style={styles.priceLabel}>Giá bán niêm yết:</Text>
                <Text style={styles.priceValue}>
                  {formatCurrency(product.salePrice ?? product.price ?? 0)}
                </Text>
              </View>

              {/* Action indicator for POS remote */}
              {scanMode === 'POS_REMOTE' && (
                <View style={styles.posAddedBox}>
                  <Text style={styles.posAddedText}>
                    {online
                      ? '✓ Đã bắn trực tiếp vào giỏ hàng thu ngân POS'
                      : '✓ Đã lưu hàng đợi - sẽ tự bắn khi có mạng'}
                  </Text>
                </View>
              )}
            </View>
          ) : (
            <View style={styles.notFoundBox}>
              <Text style={styles.notFoundTitle}>⚠️ Không tìm thấy sản phẩm</Text>
              <Text style={styles.notFoundDesc}>
                Mã vạch này chưa được đăng ký trong danh mục sản phẩm của cửa hàng.
              </Text>
            </View>
          )}

          {/* Action Buttons */}
          <View style={styles.btnRow}>
            {found && (
              <TouchableOpacity
                style={styles.resendBtn}
                onPress={() => onResendToPos(barcode)}
                activeOpacity={0.8}
              >
                <Text style={styles.resendBtnText}>🛒 Bắn lại vào POS</Text>
              </TouchableOpacity>
            )}

            <TouchableOpacity
              style={[styles.closeBtn, !found && { flex: 1 }]}
              onPress={onClose}
              activeOpacity={0.8}
            >
              <Text style={styles.closeBtnText}>Tiếp tục quét</Text>
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
    backgroundColor: 'rgba(0, 0, 0, 0.65)',
    justifyContent: 'flex-end'
  },
  sheetContainer: {
    backgroundColor: '#1e293b',
    borderTopLeftRadius: 24,
    borderTopRightRadius: 24,
    padding: 20,
    borderWidth: 1,
    borderColor: '#334155'
  },
  dragIndicator: {
    width: 40,
    height: 4,
    borderRadius: 2,
    backgroundColor: '#64748b',
    alignSelf: 'center',
    marginBottom: 14
  },
  statusBanner: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 10,
    borderRadius: 10,
    marginBottom: 14
  },
  statusBannerIcon: {
    fontSize: 16,
    marginRight: 8
  },
  statusBannerText: {
    fontSize: 13,
    fontWeight: '600',
    flex: 1
  },
  barcodeRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 12
  },
  barcodeLabel: {
    color: '#94a3b8',
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 0.5
  },
  barcodeTag: {
    backgroundColor: '#0f172a',
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 6,
    borderWidth: 1,
    borderColor: '#475569'
  },
  barcodeText: {
    color: '#38bdf8',
    fontFamily: 'monospace',
    fontSize: 13,
    fontWeight: '700'
  },
  productBox: {
    backgroundColor: '#0f172a',
    borderRadius: 14,
    padding: 16,
    borderWidth: 1,
    borderColor: '#334155',
    marginBottom: 16
  },
  productName: {
    color: '#f8fafc',
    fontSize: 16,
    fontWeight: '700',
    lineHeight: 22,
    marginBottom: 8
  },
  metaRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 6,
    marginBottom: 12
  },
  metaTag: {
    backgroundColor: '#1e293b',
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 6
  },
  metaText: {
    color: '#cbd5e1',
    fontSize: 11,
    fontWeight: '500'
  },
  priceRow: {
    flexDirection: 'row',
    alignItems: 'baseline',
    justifyContent: 'space-between',
    borderTopWidth: 1,
    borderTopColor: '#1e293b',
    paddingTop: 10
  },
  priceLabel: {
    color: '#94a3b8',
    fontSize: 13
  },
  priceValue: {
    color: '#10b981',
    fontSize: 22,
    fontWeight: '800'
  },
  posAddedBox: {
    marginTop: 10,
    backgroundColor: '#064e3b',
    padding: 8,
    borderRadius: 8
  },
  posAddedText: {
    color: '#6ee7b7',
    fontSize: 12,
    textAlign: 'center',
    fontWeight: '600'
  },
  notFoundBox: {
    backgroundColor: '#451a03',
    padding: 16,
    borderRadius: 12,
    marginBottom: 16,
    borderWidth: 1,
    borderColor: '#b45309'
  },
  notFoundTitle: {
    color: '#fbbf24',
    fontSize: 15,
    fontWeight: '700',
    marginBottom: 4
  },
  notFoundDesc: {
    color: '#fef3c7',
    fontSize: 12,
    lineHeight: 18
  },
  btnRow: {
    flexDirection: 'row',
    gap: 10
  },
  resendBtn: {
    flex: 1,
    backgroundColor: '#059669',
    paddingVertical: 14,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center'
  },
  resendBtnText: {
    color: '#ffffff',
    fontSize: 14,
    fontWeight: '700'
  },
  closeBtn: {
    flex: 1,
    backgroundColor: '#334155',
    paddingVertical: 14,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center'
  },
  closeBtnText: {
    color: '#f8fafc',
    fontSize: 14,
    fontWeight: '600'
  }
});
