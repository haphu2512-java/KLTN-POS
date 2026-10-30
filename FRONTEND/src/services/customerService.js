import api from './api';

export const customerService = {
  async getCustomers(search = '') {
    const res = await api.get('/customers', { params: search ? { search } : {} });
    return Array.isArray(res) ? res : (res?.data || []);
  },

  async getCustomerById(id) {
    const res = await api.get(`/customers/${id}`);
    return res?.data !== undefined ? res.data : res;
  },

  async getCustomerByPhone(phone) {
    const res = await api.get(`/customers/phone/${phone}`);
    return res?.data !== undefined ? res.data : res;
  },

  async createCustomer(data) {
    const res = await api.post('/customers', data);
    return res?.data !== undefined ? res.data : res;
  },

  async updateCustomer(id, data) {
    const res = await api.put(`/customers/${id}`, data);
    return res?.data !== undefined ? res.data : res;
  },
};
