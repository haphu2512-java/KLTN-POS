import api from './api';

const unwrap = (res) => (res?.data !== undefined ? res.data : res);

export const auditService = {
  /** ADMIN/MANAGER: tra cứu nhật ký thao tác. params: from, to (yyyy-MM-dd), action, username, branchId, page, size */
  async search(params = {}) {
    const clean = Object.fromEntries(Object.entries(params).filter(([, v]) => v !== '' && v != null));
    const res = unwrap(await api.get('/audit-logs', { params: clean }));
    return res || { items: [], total: 0, page: 0, size: 50 };
  },

  /** Danh sách loại thao tác [{ value, label }] để lọc */
  async actions() {
    const res = unwrap(await api.get('/audit-logs/actions'));
    return Array.isArray(res) ? res : [];
  },
};
