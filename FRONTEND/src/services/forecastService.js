import api from './api';

export const forecastService = {
  async getDemandForecast(branchId = 1, days = 7, algorithm = 'EXP_SMOOTHING') {
    const res = await api.get('/forecast/demand', {
      params: { branchId, days, algorithm }
    });
    return Array.isArray(res) ? res : (res?.data || []);
  },
};
