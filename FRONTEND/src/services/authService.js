import api from './api';

const lastBranchKey = (username) => `pos_last_branch_${String(username || '').trim().toLowerCase()}`;

export const authService = {
  /**
   * Đăng nhập vào một chi nhánh. Tài khoản có nhiều chi nhánh mà chưa chọn thì máy chủ trả
   * 409 BRANCH_SELECTION_REQUIRED kèm danh sách chi nhánh (lỗi được ném ra để màn hình hỏi chọn).
   */
  async login(username, password, branchId) {
    const response = await api.post('/auth/login', { username, password, branchId: branchId || undefined });
    if (response && response.success && response.data) {
      localStorage.setItem('pos_token', response.data.token);
      localStorage.setItem('pos_user', JSON.stringify(response.data));
      try {
        localStorage.setItem(lastBranchKey(username), String(response.data.branchId));
      } catch {
        // Bộ nhớ trình duyệt bị chặn: chỉ mất gợi ý chọn sẵn chi nhánh lần sau
      }
      return response.data;
    }
    throw new Error(response?.message || 'Đăng nhập không thành công');
  },

  /** Chi nhánh lần trước tài khoản này đăng nhập trên máy, để chọn sẵn */
  getLastBranchId(username) {
    try {
      const v = localStorage.getItem(lastBranchKey(username));
      return v ? Number(v) : null;
    } catch {
      return null;
    }
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
