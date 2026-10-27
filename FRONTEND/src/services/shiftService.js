import api from './api';

const unwrap = (res) => (res?.data !== undefined ? res.data : res);

export const shiftService = {
  /**
   * Lịch ca chi nhánh đang dùng (ca riêng, hoặc lịch mẫu chung nếu chi nhánh chưa cấu hình riêng).
   * ADMIN truyền branchId để xem chi nhánh khác, template = true để xem lịch mẫu chung.
   */
  async getWorkShifts({ branchId, template } = {}) {
    const params = {};
    if (branchId) params.branchId = branchId;
    if (template) params.template = true;
    return unwrap(await api.get('/shifts/work-shifts', { params })) || [];
  },

  /** branchId trống: thêm vào lịch mẫu chung */
  async createWorkShift({ name, startTime, endTime, branchId }) {
    return unwrap(await api.post('/shifts/work-shifts', { name, startTime, endTime, branchId: branchId || null }));
  },

  async deleteWorkShift(id) {
    return unwrap(await api.delete(`/shifts/work-shifts/${id}`));
  },

  /** Tạo lịch ca riêng cho chi nhánh bằng cách chép lịch mẫu chung */
  async customizeBranchShifts(branchId) {
    return unwrap(await api.post('/shifts/work-shifts/customize', null, { params: { branchId } }));
  },

  async updateWorkShift(id, { name, startTime, endTime, active }) {
    return unwrap(await api.put(`/shifts/work-shifts/${id}`, { name, startTime, endTime, active }));
  },

  /**
   * Ca đang mở ở chi nhánh đăng nhập. Khi chưa mở ca: { suggestedWorkShiftId, assignedStaffId, assignedStaffName,
   * suggestedOpeningCash, handoverExpected, previousShift }. Kèm cấu hình: differenceThreshold, standardFloat,
   * drawerLimit, openEarlyMinutes.
   */
  async getCurrent() {
    return unwrap(await api.get('/shifts/current'));
  },

  /**
   * Ca luôn mở tại chi nhánh của phiên đăng nhập, cho nhân viên được xếp. staffMemberId chỉ gửi khi quản lý
   * chọn người trực thay (kèm overrideReason).
   */
  async openShift({ workShiftId, openingCash, note, staffMemberId, handoverReason, overrideReason }) {
    return unwrap(
      await api.post('/shifts/open', { workShiftId, openingCash, note, staffMemberId, handoverReason, overrideReason })
    );
  },

  // { closingCash, denominations, countedVietqr, countedCard, differenceReason, leftInDrawer, handoverNote }
  async closeCurrent(body) {
    return unwrap(await api.post('/shifts/current/close', body));
  },

  // Thu/chi két: type EXPENSE | DROP | FLOAT_IN
  async getCurrentCashMovements() {
    return unwrap(await api.get('/shifts/current/cash-movements')) || [];
  },

  async addCashMovement({ type, category, amount, reason, counterpart }) {
    return unwrap(await api.post('/shifts/current/cash-movements', { type, category, amount, reason, counterpart }));
  },

  async deleteCashMovement(id) {
    return unwrap(await api.delete(`/shifts/cash-movements/${id}`));
  },

  async getShiftCashMovements(id) {
    return unwrap(await api.get(`/shifts/${id}/cash-movements`)) || [];
  },

  async reviewShift(id, note) {
    return unwrap(await api.post(`/shifts/${id}/review`, { note }));
  },

  // Lịch xếp ca của chi nhánh (quản trị truyền branchId)
  async getAssignments({ from, to, branchId }) {
    return unwrap(await api.get('/shifts/assignments', { params: { from, to, branchId: branchId || undefined } })) || [];
  },

  // staffMemberId trống là bỏ xếp ô đó
  async assign({ workShiftId, shiftDate, staffMemberId, branchId }) {
    return unwrap(
      await api.put('/shifts/assignments', { workShiftId, shiftDate, staffMemberId: staffMemberId || null }, {
        params: { branchId: branchId || undefined }
      })
    );
  },

  /**
   * Lịch sử ca theo bộ lọc. Máy chủ tự giới hạn: trừ quản trị, chỉ nhận ca ở chi nhánh đang đăng nhập.
   * Trả về { shifts, totalShifts, openShifts, pendingReviewShifts, mismatchedShifts, totalShortage, totalOverage }.
   */
  async listShifts({ branchId, staffId, status, from, to } = {}) {
    const params = { branchId: branchId || undefined, staffId: staffId || undefined, status: status || undefined, from, to };
    return unwrap(await api.get('/shifts', { params }));
  },

  async getShift(id) {
    return unwrap(await api.get(`/shifts/${id}`));
  },

  // Hóa đơn bán trong ca và hóa đơn được hoàn trả bằng tiền két của ca
  async getShiftInvoices(id) {
    return unwrap(await api.get(`/shifts/${id}/invoices`)) || [];
  },

  async closeShiftById(id, body) {
    return unwrap(await api.post(`/shifts/${id}/close`, body));
  },
};
