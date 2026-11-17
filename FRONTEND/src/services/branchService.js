import api from './api';

const unwrap = (res) => (res?.data !== undefined ? res.data : res);

export const branchService = {
  async getActiveBranches() {
    const res = await api.get('/branches/active');
    return Array.isArray(res) ? res : (res?.data || []);
  },

  /** ADMIN: mọi chi nhánh, kể cả đã ngừng hoạt động */
  async getAllBranches() {
    const res = await api.get('/branches');
    return Array.isArray(res) ? res : (res?.data || []);
  },

  async createBranch({ name, address, phone }) {
    return unwrap(await api.post('/branches', { name, address, phone }));
  },

  async updateBranch(id, { name, address, phone, active }) {
    return unwrap(await api.put(`/branches/${id}`, { name, address, phone, active }));
  },
};
