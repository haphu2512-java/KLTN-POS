import api from './api';

const unwrap = (res) => (res?.data !== undefined ? res.data : res);

export const loyaltyService = {
  /** { earnPerAmount, pointValue, redeemMaxPercent, minRedeemPoints } */
  async getPolicy() {
    return unwrap(await api.get('/loyalty/policy'));
  },

  /** Sổ điểm của khách, mới nhất trước */
  async getTransactions(customerId) {
    const res = unwrap(await api.get(`/loyalty/customers/${customerId}/transactions`));
    return Array.isArray(res) ? res : [];
  },

  /** Quản lý cộng (số dương) hoặc trừ (số âm) điểm tay, bắt buộc lý do */
  async adjust(customerId, points, reason) {
    return unwrap(await api.post(`/loyalty/customers/${customerId}/adjust`, { points, reason }));
  }
};

/**
 * Số điểm tối đa dùng được cho đơn có số tiền (sau giảm giá) là amount.
 * Cùng công thức với LoyaltyPolicy.maxRedeemable ở backend.
 */
export function maxRedeemablePoints(policy, amount, balance) {
  if (!policy || amount <= 0 || !balance || balance <= 0) return 0;
  const cap = Math.floor((amount * Number(policy.redeemMaxPercent)) / 100);
  return Math.min(Math.floor(cap / Number(policy.pointValue)), balance);
}
