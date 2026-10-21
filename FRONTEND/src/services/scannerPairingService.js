import api from './api';

// Mỗi máy POS là một "quầy"; id quầy do server cấp và chỉ lưu trên máy này
const TERMINAL_KEY = 'pos_terminal_id';

const unwrap = (res) => (res?.data !== undefined ? res.data : res);

export const scannerPairingService = {
  getSavedTerminalId() {
    return localStorage.getItem(TERMINAL_KEY);
  },

  // Lấy quầy đã đăng ký trên máy này; chưa có (hoặc quầy thuộc chi nhánh khác) thì đăng ký mới
  // Quầy thuộc một chi nhánh: đăng nhập vào chi nhánh khác trên cùng máy thì đăng ký quầy mới cho chi nhánh đó
  async ensureTerminal(defaultName = 'Quầy POS', branchId) {
    const savedId = localStorage.getItem(TERMINAL_KEY);
    if (savedId) {
      try {
        const saved = unwrap(await api.get(`/scanner/terminals/${savedId}`));
        if (!branchId || Number(saved.branchId) === Number(branchId)) return saved;
      } catch (err) {
        if (err.response?.status !== 404) throw err;
      }
    }
    const terminal = unwrap(await api.post('/scanner/terminals', { name: defaultName }));
    localStorage.setItem(TERMINAL_KEY, terminal.id);
    return terminal;
  },

  async rename(terminalId, name) {
    return unwrap(await api.put(`/scanner/terminals/${terminalId}`, { name }));
  },

  // { code, terminalName, expiresInSeconds }
  async createPairingCode(terminalId) {
    return unwrap(await api.post(`/scanner/terminals/${terminalId}/pairing-code`));
  },

  async listPairings(terminalId) {
    const res = unwrap(await api.get(`/scanner/terminals/${terminalId}/pairings`));
    return Array.isArray(res) ? res : [];
  },

  async unpair(terminalId, pairingId) {
    await api.delete(`/scanner/terminals/${terminalId}/pairings/${pairingId}`);
  }
};

// Nội dung mã QR mà app điện thoại đọc để ghép cặp
export const pairingQrContent = (code) => `POS-PAIR:${code}`;

export default scannerPairingService;
