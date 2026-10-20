import api from './api';

export const userService = {
  async getUsers() {
    const res = await api.get('/users');
    return Array.isArray(res) ? res : (res?.data || []);
  },

  async getUserById(id) {
    const res = await api.get(`/users/${id}`);
    return res?.data !== undefined ? res.data : res;
  },

  async createUser(userData) {
    const res = await api.post('/users', userData);
    return res?.data !== undefined ? res.data : res;
  },

  async updateUser(id, userData) {
    const res = await api.put(`/users/${id}`, userData);
    return res?.data !== undefined ? res.data : res;
  },

  async toggleUserStatus(id) {
    const res = await api.patch(`/users/${id}/toggle-status`);
    return res?.data !== undefined ? res.data : res;
  },

  async changePassword(id, newPassword) {
    const res = await api.post(`/users/${id}/change-password`, { newPassword });
    return res?.data !== undefined ? res.data : res;
  },
};
