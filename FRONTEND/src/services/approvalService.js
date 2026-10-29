import api from './api';

const unwrap = (res) => (res?.data !== undefined ? res.data : res);

export const approvalService = {
  /** { cashierMaxDiscountPercent, hasPin } */
  async getPolicy() {
    return unwrap(await api.get('/approvals/policy'));
  },

  /** Quản lý đặt/đổi PIN duyệt (4-6 chữ số) */
  async setPin(currentPassword, pin) {
    return unwrap(await api.put('/approvals/pin', { currentPassword, pin }));
  },

  /** Quản lý nhập tài khoản + PIN trên máy POS; trả { token, approverName, maxDiscount, expiresAt } */
  async approveDiscount(approverUsername, pin, amount) {
    return unwrap(await api.post('/approvals/discount', { approverUsername, pin, amount }));
  }
};
