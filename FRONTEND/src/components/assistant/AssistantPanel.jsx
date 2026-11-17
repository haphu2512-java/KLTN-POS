import { useEffect, useRef, useState } from 'react';
import { assistantService } from '../../services/assistantService';
import './AssistantPanel.css';

const MAX_HISTORY = 40;

function storageKey(username) {
  return `pos_assistant_${username || 'guest'}`;
}

/** Lịch sử chat giữ trong sessionStorage để không mất khi chuyển màn hình (khung ứng dụng được dựng lại) */
function loadHistory(username) {
  try {
    const raw = sessionStorage.getItem(storageKey(username));
    const parsed = raw ? JSON.parse(raw) : null;
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function saveHistory(username, messages) {
  try {
    sessionStorage.setItem(storageKey(username), JSON.stringify(messages.slice(-MAX_HISTORY)));
  } catch {
    // Không lưu được (chế độ riêng tư...) thì chỉ giữ trong bộ nhớ
  }
}

function errorText(err) {
  const msg = err?.response?.data?.message;
  if (msg) return msg;
  if (!err?.response) return 'Không kết nối được máy chủ. Thử lại sau.';
  return 'Trợ lý đang gặp lỗi. Thử lại sau.';
}

const DEFAULT_SUGGESTIONS = ['Hàng nào sắp hết?', 'Bạn làm được gì?'];

/**
 * Khung chat trợ lý: hỏi số liệu cửa hàng (doanh thu, bán chạy, tồn kho, hàng sắp hết, gợi ý nhập).
 * Backend trả lời theo vai trò và chi nhánh của phiên đăng nhập.
 */
export default function AssistantPanel({ isOpen, onClose, currentUser }) {
  const username = currentUser?.username;
  const [messages, setMessages] = useState(() => loadHistory(username));
  const [suggestions, setSuggestions] = useState(DEFAULT_SUGGESTIONS);
  const [input, setInput] = useState('');
  const [isSending, setIsSending] = useState(false);
  const listRef = useRef(null);
  const inputRef = useRef(null);

  useEffect(() => {
    saveHistory(username, messages);
  }, [username, messages]);

  useEffect(() => {
    if (listRef.current) listRef.current.scrollTop = listRef.current.scrollHeight;
  }, [messages, isSending, isOpen]);

  useEffect(() => {
    if (isOpen) inputRef.current?.focus();
  }, [isOpen]);

  if (!isOpen) return null;

  const send = async (text) => {
    const question = (text ?? input).trim();
    if (!question || isSending) return;
    setInput('');
    setMessages((prev) => [...prev, { role: 'user', text: question }]);
    setIsSending(true);
    try {
      const res = await assistantService.ask(question);
      setMessages((prev) => [...prev, { role: 'bot', text: res?.reply || 'Không có câu trả lời.' }]);
      if (Array.isArray(res?.suggestions) && res.suggestions.length > 0) setSuggestions(res.suggestions);
    } catch (err) {
      setMessages((prev) => [...prev, { role: 'bot', text: errorText(err), isError: true }]);
    } finally {
      setIsSending(false);
      inputRef.current?.focus();
    }
  };

  const clear = () => setMessages([]);

  // Chặn phím lan ra phím tắt toàn cục của màn hình bán hàng (F-keys, Esc, máy quét mã)
  const handleKeyDown = (e) => {
    e.stopPropagation();
    if (e.key === 'Escape') onClose();
  };

  return (
    <section className="assistant-panel" role="dialog" aria-label="Trợ lý cửa hàng" onKeyDown={handleKeyDown}>
      <header className="assistant-header">
        <div>
          <h2 className="assistant-title">Trợ lý cửa hàng</h2>
          <p className="assistant-subtitle">Hỏi số liệu của chi nhánh đang đăng nhập</p>
        </div>
        <div className="assistant-header-actions">
          {messages.length > 0 && (
            <button type="button" className="assistant-icon-btn" onClick={clear} title="Xóa cuộc trò chuyện">
              Xóa
            </button>
          )}
          <button type="button" className="assistant-icon-btn" onClick={onClose} aria-label="Đóng trợ lý">
            ✕
          </button>
        </div>
      </header>

      <div className="assistant-messages" ref={listRef} aria-live="polite">
        {messages.length === 0 && (
          <div className="assistant-empty">
            Chào {currentUser?.fullName || username || 'bạn'}! Bạn có thể hỏi tồn kho một sản phẩm, hàng sắp hết,
            doanh thu, hàng bán chạy hoặc nên nhập thêm gì (tùy quyền của tài khoản).
          </div>
        )}
        {messages.map((m, i) => (
          <div
            key={i}
            className={`assistant-bubble is-${m.role}${m.isError ? ' is-error' : ''}`}
          >
            {m.text}
          </div>
        ))}
        {isSending && <div className="assistant-bubble is-bot is-typing">Đang tra cứu…</div>}
      </div>

      <div className="assistant-suggestions">
        {suggestions.map((s) => (
          <button key={s} type="button" className="assistant-chip" onClick={() => send(s)} disabled={isSending}>
            {s}
          </button>
        ))}
      </div>

      <form
        className="assistant-input-row"
        onSubmit={(e) => {
          e.preventDefault();
          send();
        }}
      >
        <input
          ref={inputRef}
          className="assistant-input"
          value={input}
          maxLength={500}
          onChange={(e) => setInput(e.target.value)}
          placeholder="VD: Mì Hảo Hảo còn bao nhiêu?"
          aria-label="Câu hỏi cho trợ lý"
        />
        <button type="submit" className="assistant-send" disabled={isSending || !input.trim()}>
          Gửi
        </button>
      </form>
    </section>
  );
}
