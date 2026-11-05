import api from './api';
import { newRequestId } from '../utils/requestId';

const unwrap = (res) => (res?.data !== undefined ? res.data : res);

/** Đơn đặt hàng nhà cung cấp (lập từ đề xuất AI hoặc tạo tay) */
export const purchaseOrderService = {
  async list(branchId) {
    const res = unwrap(await api.get('/purchase-orders', { params: { branchId: branchId || undefined } }));
    return Array.isArray(res) ? res : [];
  },
  async get(id) {
    return unwrap(await api.get(`/purchase-orders/${id}`));
  },
  /** Mỗi nhà cung cấp một đơn nháp; trả về danh sách đơn vừa lập */
  async fromForecast(days = 7) {
    const res = unwrap(await api.post('/purchase-orders/from-forecast', null, { params: { days } }));
    return Array.isArray(res) ? res : [];
  },
  /** req: { supplierId, note, expectedDate, lines: [{ productId, orderedQty, unitCost }] } */
  async create(req) {
    return unwrap(await api.post('/purchase-orders', req));
  },
  async update(id, req) {
    return unwrap(await api.put(`/purchase-orders/${id}`, req));
  },
  async send(id) {
    return unwrap(await api.post(`/purchase-orders/${id}/send`));
  },
  /** lines: [{ productId, quantity, unitCost }]; bỏ trống = nhận đủ phần còn lại. Trả { order, stockIn } */
  async receive(id, lines, note) {
    return unwrap(await api.post(`/purchase-orders/${id}/receive`, { lines, note, clientRequestId: newRequestId() }));
  },
  async cancel(id) {
    return unwrap(await api.post(`/purchase-orders/${id}/cancel`));
  },
};
