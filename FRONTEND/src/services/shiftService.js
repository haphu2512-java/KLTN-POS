import api from './api';

const unwrap = (res) => (res?.data !== undefined ? res.data : res);

export const shiftService = {
  async getWorkShifts() {
    return unwrap(await api.get('/shifts/work-shifts')) || [];
  },

  async createWorkShift({ name, startTime, endTime }) {
    return unwrap(await api.post('/shifts/work-shifts', { name, startTime, endTime }));
  },

  async updateWorkShift(id, { name, startTime, endTime, active }) {
    return unwrap(await api.put(`/shifts/work-shifts/${id}`, { name, startTime, endTime, active }));
  },

  // { session, suggestedWorkShiftId, suggestedOpeningCash, previousShift, shiftRequired }
  async getCurrent() {
    return unwrap(await api.get('/shifts/current'));
  },

  // Ca luôn mở tại chi nhánh của phiên đăng nhập
  async openShift({ workShiftId, openingCash, note }) {
    return unwrap(await api.post('/shifts/open', { workShiftId, openingCash, note }));
  },

  async closeCurrent({ closingCash, handoverNote }) {
    return unwrap(await api.post('/shifts/current/close', { closingCash, handoverNote }));
  },

  /**
   * Lịch sử ca theo bộ lọc. Máy chủ tự giới hạn: thu ngân chỉ nhận ca của mình, quản lý chỉ chi nhánh đang đăng nhập.
   * Trả về { shifts, totalShifts, openShifts, mismatchedShifts, totalShortage, totalOverage }.
   */
  async listShifts({ branchId, employeeId, status, from, to } = {}) {
    const params = { branchId: branchId || undefined, employeeId: employeeId || undefined, status: status || undefined, from, to };
    return unwrap(await api.get('/shifts', { params }));
  },

  async getShift(id) {
    return unwrap(await api.get(`/shifts/${id}`));
  },

  // Hóa đơn bán trong ca và hóa đơn được hoàn trả bằng tiền két của ca
  async getShiftInvoices(id) {
    return unwrap(await api.get(`/shifts/${id}/invoices`)) || [];
  },

  async closeShiftById(id, { closingCash, handoverNote }) {
    return unwrap(await api.post(`/shifts/${id}/close`, { closingCash, handoverNote }));
  },
};
