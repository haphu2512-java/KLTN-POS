import api from './api';

export const userService = {
  async getUsers() {
    const res = await api.get('/users');
    return res.data || [];
  },

  async getUserById(id) {
    const res = await api.get(`/users/${id}`);
    return res.data;
  },

  async createUser(userData) {
    const res = await api.post('/users', userData);
    return res.data;
  },

  async updateUser(id, userData) {
    const res = await api.put(`/users/${id}`, userData);
    return res.data;
  },

  async toggleUserStatus(id) {
    const res = await api.patch(`/users/${id}/toggle-status`);
    return res.data;
  },

  async changePassword(id, newPassword) {
    const res = await api.post(`/users/${id}/change-password`, { newPassword });
    return res.data;
  },
};
