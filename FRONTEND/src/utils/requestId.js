// Mã chống trùng cho mỗi thao tác ghi (hóa đơn, phiếu nhập, điều chuyển):
// gửi lại cùng mã sau khi mất phản hồi thì máy chủ không ghi lần thứ hai
export function newRequestId() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return `${Date.now()}-${Math.random().toString(36).slice(2, 12)}`;
}
