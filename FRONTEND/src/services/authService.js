import api from './api';

export const authService = {
  async login(username, password) {
    const response = await api.post('/auth/login', { username, password });
    if (response && response.success && response.data) {
      localStorage.setItem('pos_token', response.data.token);
      localStorage.setItem('pos_user', JSON.stringify(response.data));
      return response.data;
    }
    throw new Error(response?.message || 'Đăng nhập không thành công');
  },

  getCurrentUser() {
    try {
      const userStr = localStorage.getItem('pos_user');
      return userStr ? JSON.parse(userStr) : null;
    } catch {
      return null;
    }
  },

  getToken() {
    return localStorage.getItem('pos_token');
  },

  logout() {
    localStorage.removeItem('pos_token');
    localStorage.removeItem('pos_user');
  },

  async fetchMe() {
    const response = await api.get('/auth/me');
    return response.data;
  }
};
