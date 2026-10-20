import React, { useEffect, useMemo, useState } from 'react';
import { View, Text, TextInput, TouchableOpacity, FlatList, StyleSheet } from 'react-native';
import { ScreenHeader, Card, EmptyState, Icon, Button, stockMeta } from '../components/ui';
import { OfflineStorage } from '../storage/offlineStorage';
import { colors, fonts, radius, type } from '../theme';
import { formatCurrency, formatDate } from '../utils/formatters';

// Bỏ dấu để gõ "banh gao" vẫn ra "Bánh gạo"
const normalize = (s) =>
  String(s || '')
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D')
    .toLowerCase();

export default function LookupScreen({
  isOnline,
  catalogVersion,
  onSelectProduct,
  onScanToLookup,
  onSyncCatalog,
  isSyncingCatalog
}) {
  const [products, setProducts] = useState([]);
  const [lastSync, setLastSync] = useState(null);
  const [query, setQuery] = useState('');

  useEffect(() => {
    let alive = true;
    (async () => {
      const [list, time] = await Promise.all([
        OfflineStorage.loadCachedProducts(),
        OfflineStorage.getLastSyncTime()
      ]);
      if (alive) {
        setProducts(list);
        setLastSync(time);
      }
    })();
    return () => {
      alive = false;
    };
  }, [catalogVersion]);

  const results = useMemo(() => {
    const q = normalize(query.trim());
    if (!q) return products;
    return products.filter(
      (p) => normalize(p.name).includes(q) || String(p.barcode || '').includes(q) || normalize(p.category).includes(q)
    );
  }, [products, query]);

  return (
    <View style={{ flex: 1 }}>
      <ScreenHeader title="Tra cứu" subtitle="Giá bán và tồn kho theo chi nhánh" isOnline={isOnline} />

      <View style={styles.searchWrap}>
        <View style={styles.search}>
          <Icon name="search" size={17} color={colors.textLight} />
          <TextInput
            style={styles.searchInput}
            value={query}
            onChangeText={setQuery}
            placeholder="Tên sản phẩm hoặc mã vạch"
            placeholderTextColor={colors.textLight}
            autoCorrect={false}
            returnKeyType="search"
          />
          {query ? (
            <TouchableOpacity onPress={() => setQuery('')} hitSlop={8}>
              <Icon name="x" size={17} color={colors.textLight} />
            </TouchableOpacity>
          ) : null}
        </View>
        <TouchableOpacity style={styles.scanBtn} onPress={onScanToLookup} activeOpacity={0.8}>
          <Icon name="maximize" size={18} color={colors.white} />
        </TouchableOpacity>
      </View>

      <View style={styles.catalogBar}>
        <Text style={[type.small, { flex: 1 }]} numberOfLines={1}>
          {products.length} sản phẩm trên máy{lastSync ? ` · cập nhật ${formatDate(lastSync)}` : ''}
        </Text>
        <Button
          label="Tải lại"
          icon="download"
          variant="ghost"
          compact
          onPress={onSyncCatalog}
          loading={isSyncingCatalog}
          style={{ paddingHorizontal: 4 }}
        />
      </View>

      <FlatList
        data={results}
        keyExtractor={(item) => String(item.id ?? item.barcode)}
        contentContainerStyle={styles.list}
        keyboardShouldPersistTaps="handled"
        ListEmptyComponent={
          <Card>
            <EmptyState
              icon="package"
              title={query ? 'Không tìm thấy sản phẩm' : 'Chưa có danh mục trên máy'}
              description={
                query
                  ? `Không có sản phẩm nào khớp "${query}". Thử tên ngắn hơn, hoặc quét mã vạch để tra trực tiếp.`
                  : 'Tải danh mục từ máy chủ để tra cứu cả khi mất mạng.'
              }
              action={
                query ? (
                  <Button label="Quét để tra giá" icon="maximize" variant="secondary" onPress={onScanToLookup} />
                ) : (
                  <Button label="Tải danh mục" icon="download" onPress={onSyncCatalog} loading={isSyncingCatalog} />
                )
              }
            />
          </Card>
        }
        renderItem={({ item }) => {
          const stock = stockMeta(item.stock);
          return (
            <TouchableOpacity style={styles.item} onPress={() => onSelectProduct(item)} activeOpacity={0.7}>
              <View style={{ flex: 1 }}>
                <Text style={type.bodyMedium} numberOfLines={2}>
                  {item.name}
                </Text>
                <Text style={styles.meta} numberOfLines={1}>
                  {item.barcode}
                  {item.category ? `  ·  ${item.category}` : ''}
                </Text>
              </View>
              <View style={{ alignItems: 'flex-end', gap: 4 }}>
                <Text style={styles.price}>{formatCurrency(item.salePrice ?? item.price ?? 0)}</Text>
                {stock ? (
                  <View style={[styles.stock, { backgroundColor: stock.bg }]}>
                    <Text style={[styles.stockText, { color: stock.fg }]}>{stock.label}</Text>
                  </View>
                ) : null}
              </View>
            </TouchableOpacity>
          );
        }}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  searchWrap: {
    flexDirection: 'row',
    gap: 8,
    paddingHorizontal: 16
  },
  search: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    minHeight: 46,
    backgroundColor: colors.white,
    borderWidth: 1,
    borderColor: colors.borderStrong,
    borderRadius: radius.md,
    paddingHorizontal: 12
  },
  searchInput: {
    flex: 1,
    fontFamily: fonts.regular,
    fontSize: 15,
    color: colors.text,
    paddingVertical: 8
  },
  scanBtn: {
    width: 46,
    height: 46,
    borderRadius: radius.md,
    backgroundColor: colors.primary,
    alignItems: 'center',
    justifyContent: 'center'
  },
  catalogBar: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingTop: 6,
    paddingBottom: 4
  },
  list: {
    paddingHorizontal: 16,
    paddingBottom: 24
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
  },
  stock: {
    paddingHorizontal: 7,
    paddingVertical: 2,
    borderRadius: radius.sm
  },
  stockText: {
    fontFamily: fonts.semibold,
    fontSize: 11
  }
});
