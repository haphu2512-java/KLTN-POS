import api from './api';

export const assistantService = {
  /** Gửi câu hỏi cho trợ lý; trả về { reply, intent, source, suggestions } */
  async ask(message) {
    const res = await api.post('/assistant/chat', { message });
    return res?.data !== undefined ? res.data : res;
  },
};
