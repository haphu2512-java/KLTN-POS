import api from './api';

const unwrap = (res) => (res?.data !== undefined ? res.data : res);

/** Hạn sử dụng theo lô nhập */
export const expiryService = {
  /** { warnDays, lots: [{ productName, lotNumber, expiryDate, remainingQty, daysLeft, status, value }], expiredLots, nearLots, expiredValue, nearValue } */
  async report(days = 30, branchId) {
    return unwrap(await api.get('/expiry', { params: { days, branchId: branchId || undefined } }));
  },
  /** Lập phiếu kiểm kê nháp hủy hàng hết hạn; trả về phiếu kiểm kê */
  async disposalCount() {
    return unwrap(await api.post('/expiry/disposal-count'));
  },
};
