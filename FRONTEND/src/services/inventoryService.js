import api from './api';

export const inventoryService = {
  async getBranchInventory(branchId = 1) {
    const res = await api.get('/inventory', { params: { branchId } });
    return Array.isArray(res) ? res : (res?.data || []);
  },

  async getLowStock(branchId = 1) {
    const res = await api.get('/inventory/low-stock', { params: { branchId } });
    return Array.isArray(res) ? res : (res?.data || []);
  },

  async getProductInventory(branchId = 1, productId) {
    const res = await api.get('/inventory/product', { params: { productId, branchId } });
    return res?.data !== undefined ? res.data : res;
  },
};
