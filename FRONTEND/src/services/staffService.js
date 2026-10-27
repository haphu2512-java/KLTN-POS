import api from './api';

const unwrap = (res) => (res?.data !== undefined ? res.data : res);

/** Nhân viên trực ca (không phải tài khoản đăng nhập); quản lý thêm/sửa ở chi nhánh mình */
export const staffService = {
  async list(branchId) {
    return unwrap(await api.get('/staff', { params: { branchId: branchId || undefined } })) || [];
  },

  async create({ branchId, fullName, phone, position }) {
    return unwrap(await api.post('/staff', { branchId, fullName, phone, position }));
  },

  async update(id, { branchId, fullName, phone, position, active }) {
    return unwrap(await api.put(`/staff/${id}`, { branchId, fullName, phone, position, active }));
  },
};
