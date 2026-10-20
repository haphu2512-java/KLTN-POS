import api from './api';

const unwrap = (res) => (res?.data !== undefined ? res.data : res);

export const shiftService = {
  async getWorkShifts() {
    return unwrap(await api.get('/shifts/work-shifts')) || [];
  },

  // { session, suggestedWorkShiftId, suggestedOpeningCash, previousShift, shiftRequired }
  async getCurrent() {
    return unwrap(await api.get('/shifts/current'));
  },

  async openShift({ workShiftId, openingCash, note }) {
    return unwrap(await api.post('/shifts/open', { workShiftId, openingCash, note }));
  },

  async closeCurrent({ closingCash, handoverNote }) {
    return unwrap(await api.post('/shifts/current/close', { closingCash, handoverNote }));
  },

  async listShifts({ branchId, from, to }) {
    return unwrap(await api.get('/shifts', { params: { branchId, from, to } })) || [];
  },

  async closeShiftById(id, { closingCash, handoverNote }) {
    return unwrap(await api.post(`/shifts/${id}/close`, { closingCash, handoverNote }));
  },
};
