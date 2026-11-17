import api from './api';

const unwrap = (res) => (res?.data !== undefined ? res.data : res);

export const stockCountService = {
  async list(branchId) {
    const res = unwrap(await api.get('/stock-counts', { params: { branchId: branchId || undefined } }));
    return Array.isArray(res) ? res : [];
  },
  async get(id) {
    return unwrap(await api.get(`/stock-counts/${id}`));
  },
  async create(note) {
    return unwrap(await api.post('/stock-counts', { note }));
  },
  /** line: { productId | barcode, countedQty, reason, add } */
  async upsertLine(id, line) {
    return unwrap(await api.put(`/stock-counts/${id}/lines`, line));
  },
  async removeLine(id, productId) {
    return unwrap(await api.delete(`/stock-counts/${id}/lines/${productId}`));
  },
  async submit(id) {
    return unwrap(await api.post(`/stock-counts/${id}/submit`));
  },
  async approve(id) {
    return unwrap(await api.post(`/stock-counts/${id}/approve`));
  },
  async cancel(id) {
    return unwrap(await api.post(`/stock-counts/${id}/cancel`));
  },
  async reasons() {
    const res = unwrap(await api.get('/stock-counts/reasons'));
    return Array.isArray(res) ? res : [];
  },
  /** { from, to, byReason: [{ reason, label, quantity, value }], totalLoss, totalGain } */
  async shrinkage(params = {}) {
    return unwrap(await api.get('/stock-counts/shrinkage', { params }));
  },
};
