import api from './api';

export const customerService = {
  async getCustomers(search = '') {
    const res = await api.get('/customers', { params: search ? { search } : {} });
    return res.data || [];
  },

  async getCustomerById(id) {
    const res = await api.get(`/customers/${id}`);
    return res.data;
  },

  async getCustomerByPhone(phone) {
    const res = await api.get(`/customers/phone/${phone}`);
    return res.data;
  },

  async createCustomer(data) {
    const res = await api.post('/customers', data);
    return res.data;
  },
};
