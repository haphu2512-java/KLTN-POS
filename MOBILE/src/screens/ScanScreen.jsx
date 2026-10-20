import React from 'react';
import { View, Text, TouchableOpacity, ScrollView, StyleSheet, useWindowDimensions } from 'react-native';
import ScannerView from '../components/ScannerView';
import { ScreenHeader, Card, SectionTitle, EmptyState, Icon, StatusTag } from '../components/ui';
import { colors, fonts, radius, type } from '../theme';
import { formatCurrency } from '../utils/formatters';

const MODES = [
  { key: 'POS_REMOTE', label: 'Gửi vào giỏ POS', icon: 'shopping-cart' },
  { key: 'PRICE_CHECK', label: 'Kiểm giá', icon: 'tag' }
];

export default function ScanScreen({
  isOnline,
  scanMode,
  onToggleMode,
  pairing,
  queueCount,
  onOpenQueue,
  onOpenPairing,
  history,
  onSeeAllHistory,
  onSelectHistory,
  onBarcodeDetected,
  isScanningActive,
  isProcessing
}) {
  const { width, height } = useWindowDimensions();
  // Camera chiếm khoảng 1/3 chiều cao màn, phần dưới dành cho trạng thái và mã vừa quét
  const cameraHeight = Math.round(Math.min(width - 32, Math.max(220, height * 0.34)));
  const recent = history.slice(0, 5);

  return (
    <View style={{ flex: 1 }}>
      <ScreenHeader
        title="Quét mã"
        subtitle={scanMode === 'POS_REMOTE' ? 'Thêm vào giỏ của quầy đã ghép' : 'Chỉ xem giá, không gửi tới POS'}
        isOnline={isOnline}
      />

      <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
        <View style={styles.segment}>
          {MODES.map((m) => {
            const active = scanMode === m.key;
            return (
              <TouchableOpacity
                key={m.key}
                style={[styles.segmentItem, active && styles.segmentItemActive]}
                onPress={() => onToggleMode(m.key)}
                activeOpacity={0.8}
              >
                <Icon name={m.icon} size={15} color={active ? colors.white : colors.textMuted} />
                <Text style={[styles.segmentText, active && { color: colors.white }]}>{m.label}</Text>
              </TouchableOpacity>
            );
          })}
        </View>

        {scanMode === 'POS_REMOTE' && (
          <TouchableOpacity
            style={[styles.banner, pairing ? styles.bannerOk : styles.bannerWarn]}
            onPress={onOpenPairing}
            activeOpacity={0.8}
          >
            <Icon
              name={pairing ? 'link' : 'alert-triangle'}
              size={16}
              color={pairing ? colors.primary : colors.warningDark}
            />
            <View style={{ flex: 1 }}>
              <Text style={[styles.bannerTitle, { color: pairing ? colors.primaryDark : colors.warningDark }]}>
                {pairing ? `Đang ghép ${pairing.terminalName}` : 'Chưa ghép quầy POS'}
              </Text>
              <Text style={styles.bannerDesc}>
                {pairing
                  ? 'Đổi quầy: quét mã QR trên máy POS khác'
                  : 'Trên máy POS bấm "Ghép điện thoại" rồi quét mã QR hiện ra'}
              </Text>
            </View>
            <Icon name="chevron-right" size={18} color={colors.textLight} />
          </TouchableOpacity>
        )}

        {queueCount > 0 && (
          <TouchableOpacity style={[styles.banner, styles.bannerWarn]} onPress={onOpenQueue} activeOpacity={0.8}>
            <Icon name="upload-cloud" size={16} color={colors.warningDark} />
            <View style={{ flex: 1 }}>
              <Text style={[styles.bannerTitle, { color: colors.warningDark }]}>
                {queueCount} mã đang chờ đồng bộ
              </Text>
              <Text style={styles.bannerDesc}>Quét lúc mất mạng. Bấm để gửi lên máy chủ khi có mạng.</Text>
            </View>
            <Icon name="chevron-right" size={18} color={colors.textLight} />
          </TouchableOpacity>
        )}

        <ScannerView
          height={cameraHeight}
          onBarcodeDetected={onBarcodeDetected}
          isScanningActive={isScanningActive}
          isProcessing={isProcessing}
        />

        <View style={{ marginTop: 18 }}>
          <SectionTitle
            right={
              history.length > 0 ? (
                <TouchableOpacity onPress={onSeeAllHistory} hitSlop={8}>
                  <Text style={styles.link}>Xem tất cả</Text>
                </TouchableOpacity>
              ) : null
            }
          >
            Vừa quét
          </SectionTitle>

          {recent.length === 0 ? (
            <Card>
              <EmptyState
                icon="maximize"
                title="Chưa quét mã nào"
                description="Đưa mã vạch sản phẩm vào khung camera. Mã vừa quét sẽ hiện ở đây."
              />
            </Card>
          ) : (
            <Card style={{ paddingVertical: 4 }}>
              {recent.map((item, idx) => (
                <TouchableOpacity
                  key={item.id}
                  style={[styles.row, idx > 0 && styles.rowDivider]}
                  onPress={() => onSelectHistory(item)}
                  activeOpacity={0.7}
                >
                  <View style={{ flex: 1 }}>
                    <Text style={type.bodyMedium} numberOfLines={1}>
                      {item.product?.name || 'Sản phẩm chưa có trong danh mục'}
                    </Text>
                    <Text style={styles.rowMeta} numberOfLines={1}>
                      {item.barcode}
                      {item.product ? `  ·  ${formatCurrency(item.product.salePrice ?? item.product.price ?? 0)}` : ''}
                    </Text>
                  </View>
                  <View style={{ alignItems: 'flex-end', gap: 4 }}>
                    <StatusTag status={item.status} />
                    <Text style={type.small}>{item.timestamp}</Text>
                  </View>
                </TouchableOpacity>
              ))}
            </Card>
          )}
        </View>
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  content: {
    paddingHorizontal: 16,
    paddingBottom: 24
  },
  segment: {
    flexDirection: 'row',
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.md + 2,
    padding: 3,
    marginBottom: 10
  },
  segmentItem: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
    minHeight: 38,
    borderRadius: radius.md
  },
  segmentItemActive: {
    backgroundColor: colors.primary
  },
  segmentText: {
    fontFamily: fonts.semibold,
    fontSize: 13,
    color: colors.textMuted
  },
  banner: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    borderWidth: 1,
    borderRadius: radius.lg,
    paddingHorizontal: 12,
    paddingVertical: 10,
    marginBottom: 10
  },
  bannerOk: {
    backgroundColor: colors.primarySoft,
    borderColor: colors.primaryBorder
  },
  bannerWarn: {
    backgroundColor: colors.warningSoft,
    borderColor: colors.warningBorder
  },
  bannerTitle: {
    fontFamily: fonts.semibold,
    fontSize: 14
  },
  bannerDesc: {
    fontFamily: fonts.regular,
    fontSize: 12,
    color: colors.textMuted,
    marginTop: 1
  },
  link: {
    fontFamily: fonts.semibold,
    fontSize: 13,
    color: colors.primary
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    paddingVertical: 10
  },
  rowDivider: {
    borderTopWidth: 1,
    borderTopColor: colors.border
  },
  rowMeta: {
    fontFamily: fonts.regular,
    fontVariant: ['tabular-nums'],
    fontSize: 12,
    color: colors.textMuted,
    marginTop: 2
  }
});
