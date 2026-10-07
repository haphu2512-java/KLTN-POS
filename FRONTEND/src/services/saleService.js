import api from './api';

export const saleService = {
  async getInvoices(branchId = 1) {
    const res = await api.get('/invoices', { params: { branchId } });
    return res.data || [];
  },

  async getInvoiceById(id) {
    const res = await api.get(`/invoices/${id}`);
    return res.data;
  },

  async createInvoice(invoiceData) {
    const res = await api.post('/invoices', invoiceData);
    return res.data;
  },

  async refundInvoice(id, reason = '') {
    const res = await api.post(`/invoices/${id}/refund`, { reason });
    return res.data;
  },
};

