import api from './api';

export const forecastService = {
  async getDemandForecast(branchId = 1, days = 7, algorithm = 'EXP_SMOOTHING') {
    const res = await api.get('/forecast/demand', {
      params: { branchId, days, algorithm }
    });
    return Array.isArray(res) ? res : (res?.data || []);
  },

  // Dự báo 1 sản phẩm, có kèm chuỗi bán hàng lịch sử để vẽ biểu đồ thực tế và dự báo
  async getProductForecast(productId, branchId = 1, days = 7, algorithm = 'EXP_SMOOTHING') {
    const res = await api.get(`/forecast/demand/${productId}`, {
      params: { branchId, days, algorithm }
    });
    return res?.data ?? res ?? null;
  },
};
