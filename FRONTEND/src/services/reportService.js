import api from './api';

export const reportService = {
  async getRevenueReport(branchId = 1, days = 7) {
    const res = await api.get('/reports/revenue', {
      params: { branchId, days }
    });
    return res?.data !== undefined ? res.data : res;
  },

  async getTopProducts(branchId = 1, days = 30, limit = 10) {
    const res = await api.get('/reports/top-products', {
      params: { branchId, days, limit }
    });
    const items = res?.data !== undefined ? res.data : res;
    return Array.isArray(items) ? items : [];
  },
};
