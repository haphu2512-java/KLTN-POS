import api from './api';

export const reportService = {
  async getRevenueReport(branchId, days = 7) {
    const res = await api.get('/reports/revenue', {
      params: { branchId: branchId || undefined, days }
    });
    return res?.data !== undefined ? res.data : res;
  },

  async getTopProducts(branchId, days = 30, limit = 10) {
    const res = await api.get('/reports/top-products', {
      params: { branchId: branchId || undefined, days, limit }
    });
    const items = res?.data !== undefined ? res.data : res;
    return Array.isArray(items) ? items : [];
  },
};
