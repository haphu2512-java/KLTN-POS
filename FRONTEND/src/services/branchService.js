import api from './api';

export const branchService = {
  async getActiveBranches() {
    const res = await api.get('/branches/active');
    return Array.isArray(res) ? res : (res?.data || []);
  },
};
