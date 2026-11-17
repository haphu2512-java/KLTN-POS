import api from './api';
import { DATA_EVENTS, emitDataChange } from '../utils/dataEvents';

const unwrap = (res) => (res?.data !== undefined ? res.data : res);

/** Báo màn hình Bán hàng trong cùng cửa sổ tải lại khuyến mãi (máy khác nhận qua WebSocket) */
const changed = (promotionId, change, result) => {
  emitDataChange(DATA_EVENTS.PROMOTION_CHANGED, { promotionId, change });
  return result;
};

export const promotionService = {
  /** ADMIN/MANAGER: mọi chương trình khuyến mãi, mới tạo trước */
  async getAll() {
    const res = unwrap(await api.get('/promotions'));
    return Array.isArray(res) ? res : [];
  },

  /** ADMIN: tạo chương trình */
  async create(payload) {
    const created = unwrap(await api.post('/promotions', payload));
    return changed(created?.id, 'created', created);
  },

  /** ADMIN: sửa chương trình */
  async update(id, payload) {
    return changed(id, 'updated', unwrap(await api.put(`/promotions/${id}`, payload)));
  },

  /** ADMIN: bật/tắt nhanh */
  async setActive(id, active) {
    return changed(id, active ? 'activated' : 'deactivated', unwrap(await api.patch(`/promotions/${id}/active`, { active })));
  },

  /** ADMIN: xóa hẳn (hóa đơn cũ vẫn giữ tên và tiền giảm) */
  async remove(id) {
    return changed(id, 'deleted', unwrap(await api.delete(`/promotions/${id}`)));
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
