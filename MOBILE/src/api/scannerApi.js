import { AppConfig } from './config';
import { OfflineStorage } from '../storage/offlineStorage';

// Fetch helper with timeout
const fetchWithTimeout = async (url, options = {}, timeoutMs = 3000) => {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), timeoutMs);

  try {
    const response = await fetch(url, {
      ...options,
      signal: controller.signal
    });
    clearTimeout(timeoutId);
    return response;
  } catch (error) {
    clearTimeout(timeoutId);
    throw error;
  }
};

export const ScannerApi = {
  // Broadcast barcode directly to POS through Spring Boot WebSocket
  async broadcastBarcode(barcode, action = 'ADD_TO_CART') {
    const serverUrl = await AppConfig.getServerUrl();
    const branchId = await AppConfig.getBranchId();
    const cleanBarcode = String(barcode || '').trim();
    const clientScanId = `scan-${Date.now()}-${Math.random().toString(36).substring(2, 6)}`;

    try {
      const response = await fetchWithTimeout(
        `${serverUrl}/api/scanner/broadcast`,
        {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Accept: 'application/json'
          },
          body: JSON.stringify({
            barcode: cleanBarcode,
            branchId,
            deviceName: 'Expo Mobile App',
            clientScanId,
            action
          })
        },
        3000 // 3s timeout
      );

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }

      const resData = await response.json();
      const product = resData?.data?.product || null;

      // Add to history
      await OfflineStorage.addScanHistory({
        barcode: cleanBarcode,
        product,
        status: product ? 'SENT' : 'NOT_FOUND'
      });

      return {
        success: true,
        online: true,
        broadcasted: true,
        product,
        found: !!product,
        message: product 
          ? `Đã gửi "${product.name}" tới quầy POS!` 
          : `Đã gửi mã ${cleanBarcode} (chưa có trong kho)`
      };
    } catch (err) {
      console.log('Không thể kết nối máy chủ, kích hoạt Offline-First:', err.message);

      // OFFLINE FALLBACK: Look up in local offline storage cache
      const localProduct = await OfflineStorage.lookupProductOffline(cleanBarcode);

      // Enqueue to offline scan queue for later synchronization
      await OfflineStorage.queueOfflineScan({
        clientScanId,
        barcode: cleanBarcode,
        branchId,
        deviceName: 'Expo Mobile (Offline)',
        timestamp: new Date().toISOString(),
        action,
        productName: localProduct?.name || 'Sản phẩm Offline',
        productPrice: localProduct?.salePrice || 0
      });

      // Add to history as offline queued
      await OfflineStorage.addScanHistory({
        barcode: cleanBarcode,
        product: localProduct,
        status: 'OFFLINE_QUEUED'
      });

      return {
        success: true,
        online: false,
        broadcasted: false,
        queued: true,
        product: localProduct,
        found: !!localProduct,
        message: localProduct
          ? `[Offline] Tra cứu được "${localProduct.name}". Đã xếp hàng chờ gửi!`
          : `[Offline] Mã ${cleanBarcode} đã lưu vào hàng đợi đồng bộ.`
      };
    }
  },

  // Sync entire product catalog to phone for offline lookups
  async fetchProductsForCache() {
    const serverUrl = await AppConfig.getServerUrl();
    try {
      const response = await fetchWithTimeout(
        `${serverUrl}/api/scanner/products/cache`,
        {
          headers: { Accept: 'application/json' }
        },
        7000
      );

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }

      const json = await response.json();
      const products = json?.data || [];
      if (Array.isArray(products) && products.length > 0) {
        await OfflineStorage.saveCachedProducts(products);
        return { success: true, count: products.length };
      }
      return { success: false, message: 'Danh sách sản phẩm trống' };
    } catch (err) {
      return {
        success: false,
        message: `Lỗi kết nối máy chủ: ${err.message}`
      };
    }
  },

  // Push all queued offline scans to Spring Boot backend
  async syncOfflineQueue() {
    const queue = await OfflineStorage.getOfflineQueue();
    if (queue.length === 0) {
      return { success: true, count: 0, message: 'Hàng đợi Offline đang trống.' };
    }

    const serverUrl = await AppConfig.getServerUrl();
    try {
      const response = await fetchWithTimeout(
        `${serverUrl}/api/scanner/sync-batch`,
        {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Accept: 'application/json'
          },
          body: JSON.stringify(queue)
        },
        8000
      );

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }

      await OfflineStorage.clearOfflineQueue();
      return {
        success: true,
        count: queue.length,
        message: `Đã đồng bộ thành công ${queue.length} mã quét lên hệ thống POS!`
      };
    } catch (err) {
      return {
        success: false,
        message: `Lỗi đồng bộ: ${err.message}. Các mã quét vẫn an toàn trong hàng đợi.`
      };
    }
  },

  // Test server connectivity
  async testConnection(customUrl) {
    const url = customUrl || (await AppConfig.getServerUrl());
    try {
      const start = Date.now();
      const res = await fetchWithTimeout(`${url}/api/scanner/products/cache`, {}, 2500);
      const pingMs = Date.now() - start;
      return {
        online: res.ok,
        status: res.status,
        pingMs,
        message: res.ok ? `Kết nối thành công (${pingMs}ms)` : `Máy chủ phản hồi mã ${res.status}`
      };
    } catch (e) {
      return {
        online: false,
        pingMs: 0,
        message: `Không thể kết nối đến ${url} (${e.name === 'AbortError' ? 'Hết thời gian chờ' : e.message})`
      };
    }
  }
};
