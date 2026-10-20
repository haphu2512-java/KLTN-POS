import AsyncStorage from '@react-native-async-storage/async-storage';

const STORAGE_KEYS = {
  CACHED_PRODUCTS: '@pos_offline_products_cache',
  OFFLINE_QUEUE: '@pos_offline_scan_queue',
  SCAN_HISTORY: '@pos_scan_history',
  LAST_SYNC_TIME: '@pos_last_sync_time'
};

// Fallback seed products so offline lookup always works even on first cold start without network
const SEED_PRODUCTS = [
  {
    id: 1,
    barcode: "1053044001004",
    name: "Snack mực vị tương ớt Sriracha Talaethong gói 100g",
    category: "Bánh snack",
    unit: "Gói",
    salePrice: 33000,
    costPrice: 26000,
    stock: 45
  },
  {
    id: 2,
    barcode: "1053820000425",
    name: "Ô mai hỗn hợp cầu vồng Rainbow Plum gói 408g",
    category: "Trái cây sấy",
    unit: "Gói",
    salePrice: 60000,
    costPrice: 47000,
    stock: 28
  },
  {
    id: 3,
    barcode: "1053086000170",
    name: "Bánh gạo vị phô mai Poca Làng gạo gói 108g",
    category: "Bánh gạo",
    unit: "Gói",
    salePrice: 26000,
    costPrice: 20000,
    stock: 60
  },
  {
    id: 4,
    barcode: "1052850001654",
    name: "Kẹo Golia Frutta C nhân hương chanh gừng hộp 105.6g",
    category: "Kẹo cứng",
    unit: "Hộp",
    salePrice: 34000,
    costPrice: 27000,
    stock: 15
  },
  {
    id: 5,
    barcode: "1052850000849",
    name: "Kẹo socola M&M's Mini ống 35g",
    category: "Kẹo socola",
    unit: "Ống",
    salePrice: 24000,
    costPrice: 19000,
    stock: 50
  },
  {
    id: 6,
    barcode: "8934563148268",
    name: "Mì tôm Hảo Hảo chua cay Handy gói 75g",
    category: "Thực phẩm ăn liền",
    unit: "Gói",
    salePrice: 8000,
    costPrice: 6200,
    stock: 120
  },
  {
    id: 7,
    barcode: "8934588012117",
    name: "Nước ngọt Coca-Cola lon 320ml",
    category: "Đồ uống",
    unit: "Lon",
    salePrice: 10000,
    costPrice: 8500,
    stock: 200
  }
];

export const OfflineStorage = {
  // Save products catalog locally
  async saveCachedProducts(products) {
    if (!Array.isArray(products)) return;
    try {
      const sanitized = products.map(p => ({
        id: p.id,
        barcode: String(p.barcode || '').trim(),
        name: p.name || 'Sản phẩm không tên',
        category: p.category || 'Chung',
        unit: p.unit || 'Cái',
        salePrice: Number(p.salePrice || p.price || 0),
        costPrice: Number(p.costPrice || 0),
        stock: p.stock == null ? null : Number(p.stock),
        imageUrl: p.imageUrl || null
      }));
      await AsyncStorage.setItem(STORAGE_KEYS.CACHED_PRODUCTS, JSON.stringify(sanitized));
      await AsyncStorage.setItem(STORAGE_KEYS.LAST_SYNC_TIME, new Date().toISOString());
      return sanitized;
    } catch (e) {
      console.warn('Lỗi lưu cache sản phẩm:', e);
      return [];
    }
  },

  // Load products from local cache
  async loadCachedProducts() {
    try {
      const raw = await AsyncStorage.getItem(STORAGE_KEYS.CACHED_PRODUCTS);
      if (raw) {
        const parsed = JSON.parse(raw);
        if (Array.isArray(parsed) && parsed.length > 0) {
          return parsed;
        }
      }
    } catch {}
    // Seed initial cache on first use
    await this.saveCachedProducts(SEED_PRODUCTS);
    return SEED_PRODUCTS;
  },

  // Cập nhật một sản phẩm trong danh mục trên máy (sau khi gán mã vạch mới)
  async upsertCachedProduct(product) {
    if (!product?.id) return;
    const list = await this.loadCachedProducts();
    const rest = list.filter(p => p.id !== product.id);
    // Giữ nguyên thời điểm tải danh mục: chỉ một sản phẩm thay đổi
    const lastSync = await this.getLastSyncTime();
    await this.saveCachedProducts([product, ...rest]);
    if (lastSync) {
      await AsyncStorage.setItem(STORAGE_KEYS.LAST_SYNC_TIME, lastSync).catch(() => {});
    }
  },

  // Offline product lookup by barcode
  async lookupProductOffline(barcode) {
    if (!barcode) return null;
    const clean = String(barcode).trim().toLowerCase();
    const list = await this.loadCachedProducts();
    return list.find(p => p.barcode && p.barcode.toLowerCase() === clean) || null;
  },

  // Last sync timestamp
  async getLastSyncTime() {
    try {
      return await AsyncStorage.getItem(STORAGE_KEYS.LAST_SYNC_TIME);
    } catch {
      return null;
    }
  },

  // OFFLINE SCAN QUEUE
  async getOfflineQueue() {
    try {
      const raw = await AsyncStorage.getItem(STORAGE_KEYS.OFFLINE_QUEUE);
      return raw ? JSON.parse(raw) : [];
    } catch {
      return [];
    }
  },

  async queueOfflineScan(scanRecord) {
    try {
      const queue = await this.getOfflineQueue();
      const newEntry = {
        clientScanId: scanRecord.clientScanId || `scan-${Date.now()}-${Math.random().toString(36).substring(2, 7)}`,
        barcode: String(scanRecord.barcode || '').trim(),
        deviceName: scanRecord.deviceName || 'Expo Mobile Scanner',
        timestamp: scanRecord.timestamp || new Date().toISOString(),
        action: scanRecord.action || 'ADD_TO_CART',
        productName: scanRecord.productName || null,
        productPrice: scanRecord.productPrice || 0
      };
      queue.unshift(newEntry);
      await AsyncStorage.setItem(STORAGE_KEYS.OFFLINE_QUEUE, JSON.stringify(queue));
      return newEntry;
    } catch (e) {
      console.warn('Lỗi thêm vào hàng đợi offline:', e);
      return null;
    }
  },

  async clearOfflineQueue() {
    await AsyncStorage.removeItem(STORAGE_KEYS.OFFLINE_QUEUE);
  },

  // Đọc lại hàng đợi ngay lúc xóa để không làm mất mã vừa quét trong khi đang đồng bộ
  async removeOfflineQueueItems(clientScanIds) {
    const ids = new Set(clientScanIds);
    const queue = await this.getOfflineQueue();
    const remaining = queue.filter((item) => !ids.has(item.clientScanId));
    await AsyncStorage.setItem(STORAGE_KEYS.OFFLINE_QUEUE, JSON.stringify(remaining));
  },

  async removeOfflineQueueItem(clientScanId) {
    const queue = await this.getOfflineQueue();
    const filtered = queue.filter(item => item.clientScanId !== clientScanId);
    await AsyncStorage.setItem(STORAGE_KEYS.OFFLINE_QUEUE, JSON.stringify(filtered));
  },

  // SCAN HISTORY (Recent 50 scans)
  async getScanHistory() {
    try {
      const raw = await AsyncStorage.getItem(STORAGE_KEYS.SCAN_HISTORY);
      return raw ? JSON.parse(raw) : [];
    } catch {
      return [];
    }
  },

  async addScanHistory(record) {
    try {
      const history = await this.getScanHistory();
      const updated = [
        {
          id: `hist-${Date.now()}-${Math.random().toString(36).substring(2, 6)}`,
          barcode: record.barcode,
          product: record.product || null,
          status: record.status || 'SENT', // 'SENT' | 'OFFLINE_QUEUED' | 'NOT_FOUND' | 'LOOKUP'
          timestamp: new Date().toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit', second: '2-digit' }),
          date: new Date().toLocaleDateString('vi-VN')
        },
        ...history
      ].slice(0, 50); // Keep last 50
      await AsyncStorage.setItem(STORAGE_KEYS.SCAN_HISTORY, JSON.stringify(updated));
      return updated;
    } catch (e) {
      console.warn('Lỗi lưu lịch sử quét:', e);
    }
  },

  async clearScanHistory() {
    await AsyncStorage.removeItem(STORAGE_KEYS.SCAN_HISTORY);
  }
};
