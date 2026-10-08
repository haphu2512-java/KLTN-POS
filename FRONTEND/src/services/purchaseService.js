import api from './api';

export const purchaseService = {
  async getStockIns(branchId = 1) {
    const res = await api.get('/stock-in', { params: { branchId } });
    return res.data || [];
  },

  async getStockInById(id) {
    const res = await api.get(`/stock-in/${id}`);
    return res.data;
  },

  async createStockIn(stockInData) {
    const res = await api.post('/stock-in', stockInData);
    return res.data;
  },

  async getSuppliers() {
    const res = await api.get('/suppliers');
    return res.data || [];
  },

  async createSupplier(supplierData) {
    const res = await api.post('/suppliers', supplierData);
    return res.data;
  },
};

