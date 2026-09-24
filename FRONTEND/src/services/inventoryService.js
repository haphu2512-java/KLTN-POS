import api from './api';

export const inventoryService = {
  async getBranchInventory(branchId = 1) {
    const res = await api.get(`/inventory/branch/${branchId}`);
    return res.data || [];
  },

  async getLowStock(branchId = 1) {
    const res = await api.get(`/inventory/branch/${branchId}/low-stock`);
    return res.data || [];
  },

  async getProductInventory(branchId = 1, productId) {
    const res = await api.get(`/inventory/branch/${branchId}/product/${productId}`);
    return res.data;
  },
};
