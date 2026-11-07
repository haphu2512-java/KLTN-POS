/**
 * Kho thông báo dùng chung: gọi được cả ngoài component (vd. utils/exportCsv.js).
 * <ToastHost /> (gắn một lần ở App) lắng nghe và hiển thị.
 */
let listeners = new Set();
let nextId = 1;

function emit(event) {
  listeners.forEach((fn) => fn(event));
}

export function subscribeToasts(fn) {
  listeners.add(fn);
  return () => listeners.delete(fn);
}

function show(type, message, { duration } = {}) {
  const id = nextId++;
  // Lỗi/cảnh báo giữ lâu hơn để kịp đọc
  const ms = duration ?? (type === 'error' || type === 'warning' ? 6000 : 3500);
  emit({ kind: 'toast', toast: { id, type, message: String(message ?? ''), duration: ms } });
  return id;
}

export const toast = {
  success: (msg, opts) => show('success', msg, opts),
  error: (msg, opts) => show('error', msg, opts),
  warning: (msg, opts) => show('warning', msg, opts),
  info: (msg, opts) => show('info', msg, opts),
  dismiss: (id) => emit({ kind: 'dismiss', id }),
};

/**
 * Hộp xác nhận thay cho window.confirm. Trả về Promise<boolean>.
 * @param {string} message
 * @param {{ title?: string, confirmText?: string, cancelText?: string, danger?: boolean }} opts
 */
export function confirmDialog(message, opts = {}) {
  return new Promise((resolve) => {
    if (listeners.size === 0) {
      // Chưa gắn ToastHost (vd. trong test): rơi về hộp thoại của trình duyệt
      resolve(typeof window !== 'undefined' && window.confirm ? window.confirm(message) : false);
      return;
    }
    emit({ kind: 'confirm', confirm: { id: nextId++, message: String(message ?? ''), resolve, ...opts } });
  });
}
