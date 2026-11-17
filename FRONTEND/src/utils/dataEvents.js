/**
 * Báo thay đổi dữ liệu giữa các màn hình trong cùng ứng dụng (web và Electron đều chạy chung một cửa sổ).
 * Các màn hình được giữ sống khi chuyển trang (KeepAlivePage) nên không tự tải lại khi mở lại;
 * màn hình nào vừa ghi dữ liệu thì phát sự kiện ở đây để màn hình khác tải lại phần liên quan.
 */
export const DATA_EVENTS = {
  /** Có phiếu nhập kho mới (nhận hàng từ đơn đặt hàng, nhập kho tay...). detail: { branchId, stockInId } */
  STOCK_IN_CHANGED: 'stock-in-changed',
};

const listeners = new Map();

export function onDataChange(type, handler) {
  if (!listeners.has(type)) listeners.set(type, new Set());
  listeners.get(type).add(handler);
  return () => listeners.get(type)?.delete(handler);
}

export function emitDataChange(type, detail = {}) {
  (listeners.get(type) || []).forEach((handler) => {
    try {
      handler(detail);
    } catch (err) {
      console.error(`Lỗi khi xử lý sự kiện ${type}:`, err);
    }
  });
}
