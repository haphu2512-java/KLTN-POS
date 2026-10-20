import React, { useEffect, useState } from 'react';
import {
  View,
  Text,
  Modal,
  TextInput,
  TouchableOpacity,
  FlatList,
  ActivityIndicator,
  StyleSheet,
  Alert
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { ScannerApi, AuthError } from '../api/scannerApi';
import { Icon, EmptyState } from './ui';
import { colors, fonts, radius, type } from '../theme';
import { formatCurrency } from '../utils/formatters';

// Mã quét ra chưa có trong hệ thống: tìm đúng sản phẩm rồi lưu mã vạch thật của bao bì cho nó
export default function AssignBarcodeModal({ visible, barcode, onClose, onAssigned, onAuthError }) {
  const insets = useSafeAreaInsets();
  const [query, setQuery] = useState('');
  const [results, setResults] = useState([]);
  const [isSearching, setIsSearching] = useState(false);
  const [error, setError] = useState(null);
  const [savingId, setSavingId] = useState(null);

  useEffect(() => {
    if (visible) {
      setQuery('');
      setResults([]);
      setError(null);
      setSavingId(null);
    }
  }, [visible, barcode]);

  // Chờ người dùng gõ xong mới gọi máy chủ
  useEffect(() => {
    const q = query.trim();
    if (q.length < 2) {
      setResults([]);
      setIsSearching(false);
      return undefined;
    }
    let alive = true;
    setIsSearching(true);
    const timer = setTimeout(async () => {
      try {
        const list = await ScannerApi.searchProducts(q);
        if (alive) {
          setResults(list);
          setError(null);
        }
      } catch (e) {
        if (!alive) return;
        if (e instanceof AuthError) {
          onAuthError(e);
        } else {
          setError(e.name === 'AbortError' ? 'Không kết nối được máy chủ.' : e.message);
        }
      } finally {
        if (alive) setIsSearching(false);
      }
    }, 350);
    return () => {
      alive = false;
      clearTimeout(timer);
    };
  }, [query, onAuthError]);

  const assign = async (product) => {
    setSavingId(product.id);
    try {
      const updated = await ScannerApi.assignBarcode(product.id, barcode);
      onAssigned(updated);
    } catch (e) {
      if (e instanceof AuthError) {
        onAuthError(e);
      } else {
        Alert.alert('Không gán được mã', e.name === 'AbortError' ? 'Không kết nối được máy chủ.' : e.message);
      }
    } finally {
      setSavingId(null);
    }
  };

  const confirmAssign = (product) => {
    Alert.alert(
      'Gán mã vạch',
      `Lưu mã ${barcode} cho "${product.name}"?\n\nMã cũ ${product.barcode} sẽ được thay bằng mã này.`,
      [
        { text: 'Hủy', style: 'cancel' },
        { text: 'Gán mã', onPress: () => assign(product) }
      ]
    );
  };

  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
      <View style={styles.overlay}>
        <View style={[styles.container, { paddingBottom: 16 + insets.bottom }]}>
          <View style={styles.header}>
            <View style={{ flex: 1 }}>
              <Text style={type.title}>Gán mã vạch</Text>
              <Text style={[type.small, { marginTop: 4, lineHeight: 18 }]}>
                Chọn sản phẩm đúng với mã vừa quét. Lần sau quét mã này sẽ ra sản phẩm đó.
              </Text>
            </View>
            <TouchableOpacity onPress={onClose} style={styles.closeBtn} hitSlop={8}>
              <Icon name="x" size={18} color={colors.textMuted} />
            </TouchableOpacity>
          </View>

          <View style={styles.barcodeBox}>
            <Icon name="maximize" size={15} color={colors.primaryDark} />
            <Text style={styles.barcodeText}>{barcode}</Text>
          </View>

          <View style={styles.search}>
            <Icon name="search" size={17} color={colors.textLight} />
            <TextInput
              style={styles.searchInput}
              value={query}
              onChangeText={setQuery}
              placeholder="Gõ tên sản phẩm, vd: pepsi lon"
              placeholderTextColor={colors.textLight}
              autoCorrect={false}
              autoFocus
              returnKeyType="search"
            />
            {isSearching ? <ActivityIndicator size="small" color={colors.textLight} /> : null}
          </View>

          {error ? <Text style={styles.error}>{error}</Text> : null}

          <FlatList
            data={results}
            keyExtractor={(item) => String(item.id)}
            keyboardShouldPersistTaps="handled"
            contentContainerStyle={{ paddingBottom: 12, flexGrow: 1 }}
            ListEmptyComponent={
              isSearching ? null : (
                <View style={{ flex: 1, justifyContent: 'center' }}>
                  <EmptyState
                    icon="search"
                    title={query.trim().length < 2 ? 'Tìm sản phẩm cần gán mã' : 'Không có sản phẩm khớp'}
                    description={
                      query.trim().length < 2
                        ? 'Gõ ít nhất 2 ký tự trong tên sản phẩm.'
                        : 'Thử gõ ngắn hơn hoặc có dấu, ví dụ "sữa tươi".'
                    }
                  />
                </View>
              )
            }
            renderItem={({ item }) => (
              <TouchableOpacity
                style={styles.item}
                onPress={() => confirmAssign(item)}
                disabled={savingId != null}
                activeOpacity={0.7}
              >
                <View style={{ flex: 1 }}>
                  <Text style={type.bodyMedium} numberOfLines={2}>
                    {item.name}
                  </Text>
                  <Text style={styles.meta} numberOfLines={1}>
                    Mã hiện tại: {item.barcode}
                  </Text>
                </View>
                {savingId === item.id ? (
                  <ActivityIndicator size="small" color={colors.primary} />
                ) : (
                  <Text style={styles.price}>{formatCurrency(item.salePrice ?? 0)}</Text>
                )}
              </TouchableOpacity>
            )}
          />
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
    height: '85%',
    paddingHorizontal: 16,
    paddingTop: 18
  },
  header: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 12,
    marginBottom: 12
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
  barcodeBox: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    backgroundColor: colors.primarySoft,
    borderWidth: 1,
    borderColor: colors.primaryBorder,
    borderRadius: radius.md,
    paddingHorizontal: 12,
    paddingVertical: 8,
    marginBottom: 10
  },
  barcodeText: {
    fontFamily: fonts.mono,
    fontSize: 15,
    color: colors.primaryDark
  },
  search: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    minHeight: 46,
    backgroundColor: colors.white,
    borderWidth: 1,
    borderColor: colors.borderStrong,
    borderRadius: radius.md,
    paddingHorizontal: 12,
    marginBottom: 10
  },
  searchInput: {
    flex: 1,
    fontFamily: fonts.regular,
    fontSize: 15,
    color: colors.text,
    paddingVertical: 8
  },
  error: {
    fontFamily: fonts.medium,
    fontSize: 13,
    color: colors.danger,
    marginBottom: 8
  },
  item: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
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
  price: {
    fontFamily: fonts.bold,
    fontSize: 15,
    color: colors.text
  }
});
