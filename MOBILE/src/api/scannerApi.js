import { AppConfig } from './config';
import { OfflineStorage } from '../storage/offlineStorage';

const SYNC_CHUNK_SIZE = 200; // Khớp giới hạn @Size của /api/scanner/sync-batch

// Token hết hạn, sai hoặc tài khoản bị khóa: app phải đăng nhập lại, không xếp hàng đợi
export class AuthError extends Error {
  constructor(message = 'Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại.') {
    super(message);
    this.name = 'AuthError';
  }
}

// Điện thoại chưa ghép cặp với quầy POS nào (hoặc quầy đã gỡ điện thoại): phải quét QR trên máy POS
export class NotPairedError extends Error {
  constructor(message = 'Điện thoại chưa ghép với quầy POS. Hãy quét mã QR ở nút "Ghép điện thoại" trên máy POS.') {
    super(message);
    this.name = 'NotPairedError';
  }
}

// Nội dung QR trên máy POS: "POS-PAIR:123456"
export const PAIRING_QR_PREFIX = 'POS-PAIR:';
export const parsePairingQr = (data) => {
  const text = String(data || '').trim();
  return text.startsWith(PAIRING_QR_PREFIX) ? text.substring(PAIRING_QR_PREFIX.length).trim() : null;
};

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

// Gọi API có kèm JWT; 401/403 ném AuthError
const authFetch = async (path, options = {}, timeoutMs = 3000) => {
  const [serverUrl, session] = await Promise.all([AppConfig.getServerUrl(), AppConfig.getSession()]);
  if (!session?.token) {
    throw new AuthError('Chưa đăng nhập.');
  }
  const response = await fetchWithTimeout(
    `${serverUrl}${path}`,
    {
      ...options,
      headers: {
        Accept: 'application/json',
        ...(options.headers || {}),
        Authorization: `Bearer ${session.token}`
      }
    },
    timeoutMs
  );
  if (response.status === 401) {
    throw new AuthError();
  }
  if (response.status === 403) {
    throw new AuthError('Tài khoản này không có quyền dùng chức năng quét mã.');
  }
  if (response.status === 409) {
    const json = await response.clone().json().catch(() => null);
    if (json?.code === 'NOT_PAIRED') {
      await AppConfig.clearPairing();
      throw new NotPairedError(json.message || undefined);
    }
  }
  return response;
};

const newScanId = () => `scan-${Date.now()}-${Math.random().toString(36).substring(2, 8)}`;

export const ScannerApi = {
  // Đăng nhập bằng tài khoản POS; mã quét sẽ được gửi tới máy POS đang đăng nhập cùng tài khoản
  async login(serverUrl, username, password) {
    const cleanUrl = await AppConfig.setServerUrl(serverUrl);
    const response = await fetchWithTimeout(
      `${cleanUrl}/api/auth/login`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
        body: JSON.stringify({ username: username.trim(), password })
      },
      8000
    );
    const json = await response.json().catch(() => null);
    if (!response.ok || !json?.data?.token) {
      throw new Error(json?.message || `Đăng nhập thất bại (HTTP ${response.status})`);
    }
    const { token, username: user, fullName, role, branchId } = json.data;
    const session = { token, username: user, fullName, role, branchId };
    await AppConfig.setSession(session);
    return session;
  },

  async logout() {
    // Ghép cặp gắn với tài khoản: đăng nhập tài khoản khác phải ghép lại
    await AppConfig.clearPairing();
    await AppConfig.clearSession();
  },

  // Gửi mã 6 số đọc từ QR trên máy POS để ghép điện thoại với quầy đó
  async pair(code) {
    const response = await authFetch(
      '/api/scanner/pair',
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ code: String(code || '').trim(), deviceName: 'Expo Mobile App' })
      },
      8000
    );
    const json = await response.json().catch(() => null);
    if (!response.ok || !json?.data?.pairingId) {
      throw new Error(json?.message || `Ghép cặp thất bại (HTTP ${response.status})`);
    }
    const pairing = { pairingId: json.data.pairingId, terminalName: json.data.terminalName };
    await AppConfig.setPairing(pairing);
    return pairing;
  },

  // Hủy ghép cặp (báo server nếu được, luôn xóa trên máy)
  async unpair() {
    const pairing = await AppConfig.getPairing();
    await AppConfig.clearPairing();
    if (!pairing) return;
    try {
      await authFetch(`/api/scanner/pairings/${pairing.pairingId}`, { method: 'DELETE' }, 5000);
    } catch (err) {
      if (err instanceof AuthError) throw err;
    }
  },

  // Kiểm tra máy POS có gỡ điện thoại này chưa; mất mạng thì giữ nguyên
  async checkPairing() {
    const pairing = await AppConfig.getPairing();
    if (!pairing) return null;
    try {
      const response = await authFetch(`/api/scanner/pairings/${pairing.pairingId}`, {}, 5000);
      if (response.status === 404) {
        await AppConfig.clearPairing();
        return null;
      }
      if (response.ok) {
        const json = await response.json().catch(() => null);
        const fresh = { ...pairing, terminalName: json?.data?.terminalName || pairing.terminalName };
        await AppConfig.setPairing(fresh);
        return fresh;
      }
    } catch (err) {
      if (err instanceof AuthError) throw err;
    }
    return pairing;
  },

  // Bắn mã vào giỏ hàng của quầy POS đã ghép cặp. Chỉ dùng cho chế độ POS_REMOTE.
  async broadcastBarcode(barcode) {
    const cleanBarcode = String(barcode || '').trim();
    const clientScanId = newScanId();
    const pairing = await AppConfig.getPairing();
    if (!pairing) {
      throw new NotPairedError();
    }

    try {
      const response = await authFetch(
        '/api/scanner/broadcast',
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            pairingId: pairing.pairingId,
            barcode: cleanBarcode,
            deviceName: 'Expo Mobile App',
            clientScanId,
            timestamp: new Date().toISOString(),
            action: 'ADD_TO_CART'
          })
        },
        8000
      );

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }

      const resData = await response.json();
      const product = resData?.data?.product || null;

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
          ? `Đã gửi "${product.name}" tới ${pairing.terminalName || 'quầy POS'}!`
          : `Đã gửi mã ${cleanBarcode} tới ${pairing.terminalName || 'quầy POS'} (chưa có trong kho)`
      };
    } catch (err) {
      if (err instanceof AuthError || err instanceof NotPairedError) throw err;
      console.log('Không thể kết nối máy chủ, kích hoạt Offline-First:', err.message);

      const localProduct = await OfflineStorage.lookupProductOffline(cleanBarcode);

      // Giữ nguyên clientScanId: nếu request vừa rồi thực ra đã tới server, server sẽ bỏ qua bản trùng khi đồng bộ
      await OfflineStorage.queueOfflineScan({
        clientScanId,
        barcode: cleanBarcode,
        deviceName: 'Expo Mobile (Offline)',
        timestamp: new Date().toISOString(),
        action: 'ADD_TO_CART',
        productName: localProduct?.name || 'Sản phẩm Offline',
        productPrice: localProduct?.salePrice || 0
      });

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

  // Tra giá / tồn kho. Không gửi gì tới POS và không xếp hàng đợi.
  async lookupBarcode(barcode) {
    const cleanBarcode = String(barcode || '').trim();
    try {
      const response = await authFetch(
        `/api/scanner/lookup?barcode=${encodeURIComponent(cleanBarcode)}`,
        {},
        5000
      );
      if (response.status === 404) {
        await OfflineStorage.addScanHistory({ barcode: cleanBarcode, product: null, status: 'NOT_FOUND' });
        return { success: true, online: true, product: null, found: false, message: `Không tìm thấy mã ${cleanBarcode}` };
      }
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      const json = await response.json();
      const product = json?.data || null;
      await OfflineStorage.addScanHistory({ barcode: cleanBarcode, product, status: 'LOOKUP' });
      return { success: true, online: true, product, found: !!product, message: 'Tra cứu trực tuyến' };
    } catch (err) {
      if (err instanceof AuthError) throw err;
      const localProduct = await OfflineStorage.lookupProductOffline(cleanBarcode);
      await OfflineStorage.addScanHistory({ barcode: cleanBarcode, product: localProduct, status: 'LOOKUP' });
      return {
        success: true,
        online: false,
        product: localProduct,
        found: !!localProduct,
        message: localProduct
          ? '[Offline] Giá theo danh mục đã tải về máy'
          : `[Offline] Không có mã ${cleanBarcode} trong danh mục đã tải`
      };
    }
  },

  // Sync entire product catalog to phone for offline lookups
  async fetchProductsForCache() {
    try {
      const response = await authFetch('/api/scanner/products/cache', {}, 10000);

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
      if (err instanceof AuthError) throw err;
      return {
        success: false,
        message: `Lỗi kết nối máy chủ: ${err.message}`
      };
    }
  },

  // Đẩy hàng đợi offline lên server; POS sẽ hỏi thu ngân trước khi thêm vào giỏ.
  // Chỉ xóa đúng các mục đã gửi thành công, mã quét mới trong lúc đồng bộ vẫn được giữ lại.
  async syncOfflineQueue() {
    const queue = await OfflineStorage.getOfflineQueue();
    if (queue.length === 0) {
      return { success: true, count: 0, message: 'Hàng đợi Offline đang trống.' };
    }

    const pairing = await AppConfig.getPairing();
    if (!pairing) {
      return { success: false, count: 0, message: new NotPairedError().message };
    }

    // Hàng đợi lưu mới nhất ở đầu; gửi theo thứ tự quét
    const ordered = [...queue].reverse();
    let sent = 0;
    try {
      for (let i = 0; i < ordered.length; i += SYNC_CHUNK_SIZE) {
        const chunk = ordered.slice(i, i + SYNC_CHUNK_SIZE);
        const response = await authFetch(
          '/api/scanner/sync-batch',
          {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
              pairingId: pairing.pairingId,
              scans: chunk.map(({ barcode, clientScanId, deviceName, timestamp }) => ({
                barcode,
                clientScanId,
                deviceName,
                timestamp,
                action: 'ADD_TO_CART'
              }))
            })
          },
          10000
        );

        if (!response.ok) {
          throw new Error(`HTTP ${response.status}`);
        }

        await OfflineStorage.removeOfflineQueueItems(chunk.map((item) => item.clientScanId));
        sent += chunk.length;
      }

      return {
        success: true,
        count: sent,
        message: `Đã gửi ${sent} mã quét tới ${pairing.terminalName || 'quầy POS'}. Hãy xác nhận trên màn hình POS để thêm vào giỏ.`
      };
    } catch (err) {
      if (err instanceof AuthError) throw err;
      return {
        success: false,
        count: sent,
        message: `Lỗi đồng bộ: ${err.message}. Các mã chưa gửi vẫn an toàn trong hàng đợi.`
      };
    }
  },

  // Kiểm tra kết nối bằng endpoint nhẹ, không tải danh mục
  async testConnection(customUrl) {
    const url = (customUrl || (await AppConfig.getServerUrl())).trim().replace(/\/+$/, '');
    try {
      const start = Date.now();
      const res = await fetchWithTimeout(`${url}/api/scanner/ping`, {}, 2500);
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
