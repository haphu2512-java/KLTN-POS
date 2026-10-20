import api from './api';

export const purchaseService = {
  async getStockIns(branchId = 1) {
    const res = await api.get('/stock-in', { params: { branchId } });
    return Array.isArray(res) ? res : (res?.data || []);
  },

  async getStockInById(id) {
    const res = await api.get(`/stock-in/${id}`);
    return res?.data !== undefined ? res.data : res;
  },

  async createStockIn(stockInData) {
    const res = await api.post('/stock-in', stockInData);
    return res?.data !== undefined ? res.data : res;
  },

  async getSuppliers() {
    const res = await api.get('/suppliers');
    return Array.isArray(res) ? res : (res?.data || []);
  },

  async createSupplier(supplierData) {
    const res = await api.post('/suppliers', supplierData);
    return res?.data !== undefined ? res.data : res;
  },
};
