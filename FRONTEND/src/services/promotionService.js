import api from './api';

const unwrap = (res) => (res?.data !== undefined ? res.data : res);

export const promotionService = {
  /** ADMIN/MANAGER: mọi chương trình khuyến mãi, mới tạo trước */
  async getAll() {
    const res = unwrap(await api.get('/promotions'));
    return Array.isArray(res) ? res : [];
  },

  /** ADMIN: tạo chương trình */
  async create(payload) {
    return unwrap(await api.post('/promotions', payload));
  },

  /** ADMIN: sửa chương trình */
  async update(id, payload) {
    return unwrap(await api.put(`/promotions/${id}`, payload));
  },

  /** ADMIN: bật/tắt nhanh */
  async setActive(id, active) {
    return unwrap(await api.patch(`/promotions/${id}/active`, { active }));
  },

  /** ADMIN: xóa hẳn (hóa đơn cũ vẫn giữ tên và tiền giảm) */
  async remove(id) {
    return unwrap(await api.delete(`/promotions/${id}`));
  },

  /**
   * Khuyến mãi áp được cho giỏ hàng tại chi nhánh đang đăng nhập, giảm nhiều nhất trước.
   * items: [{ productId, quantity, unitPrice }]
   * Trả [{ promotionId, name, description, type, value, category, memberOnly, discountAmount }]
   */
  async getApplicable(customerId, items) {
    const res = unwrap(await api.post('/promotions/applicable', { customerId: customerId || null, items }));
    return Array.isArray(res) ? res : [];
  }
};
