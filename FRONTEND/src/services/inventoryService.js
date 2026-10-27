import api from './api';

export const inventoryService = {
  async getBranchInventory(branchId) {
    const res = await api.get('/inventory', { params: { branchId } });
    return Array.isArray(res) ? res : (res?.data || []);
  },

  async getLowStock(branchId) {
    const res = await api.get('/inventory/low-stock', { params: { branchId } });
    return Array.isArray(res) ? res : (res?.data || []);
  },

  async getProductInventory(branchId, productId) {
    const res = await api.get('/inventory/product', { params: { productId, branchId } });
    return res?.data !== undefined ? res.data : res;
  },

  // Chi nhánh xuất là chi nhánh của phiên đăng nhập (máy chủ tự lấy)
  // clientRequestId: gửi lại cùng mã (sau timeout) thì máy chủ không điều chuyển lần hai
  async transfer({ productId, quantity, toBranchId, note, clientRequestId }) {
    const res = await api.post('/inventory/transfer', { productId, quantity, toBranchId, note, clientRequestId });
    return res?.data !== undefined ? res.data : res;
  },
};
