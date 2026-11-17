import { useEffect, useRef, useState } from 'react';
import Modal from './Modal.jsx';
import Button from './Button.jsx';
import Input from './Input.jsx';
import { subscribeToasts } from './toastStore.js';
import './Toast.css';

const ICON = { success: '✓', error: '!', warning: '!', info: 'i' };
const LABEL = { success: 'Thành công', error: 'Lỗi', warning: 'Lưu ý', info: 'Thông báo' };

function ToastItem({ toast, onClose }) {
  const timer = useRef(null);
  const start = () => {
    timer.current = setTimeout(onClose, toast.duration);
  };
  const stop = () => clearTimeout(timer.current);
  useEffect(() => {
    start();
    return stop;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div
      className={`toast toast-${toast.type}`}
      role={toast.type === 'error' ? 'alert' : 'status'}
      onMouseEnter={stop}
      onMouseLeave={start}
    >
      <span className="toast-icon" aria-hidden="true">{ICON[toast.type]}</span>
      <div className="toast-content">
        <div className="toast-title">{LABEL[toast.type]}</div>
        <div className="toast-message">{toast.message}</div>
      </div>
      <button type="button" className="toast-close" onClick={onClose} aria-label="Đóng thông báo">×</button>
    </div>
  );
}

/** Hộp nhập một giá trị (thay cho window.prompt) */
function PromptModal({ prompt, onDone }) {
  const [value, setValue] = useState(prompt.defaultValue != null ? String(prompt.defaultValue) : '');
  const [error, setError] = useState(null);
  const inputRef = useRef(null);

  useEffect(() => {
    // Chọn sẵn giá trị mặc định để gõ đè ngay
    const t = setTimeout(() => inputRef.current?.select(), 0);
    return () => clearTimeout(t);
  }, []);

  const submit = (e) => {
    e?.preventDefault();
    const err = prompt.validate ? prompt.validate(value) : null;
    if (err) {
      setError(err);
      return;
    }
    onDone(value);
  };

  return (
    <Modal
      isOpen
      onClose={() => onDone(null)}
      title={prompt.title || 'Nhập thông tin'}
      maxWidth="420px"
      footer={
        <>
          <Button variant="secondary" onClick={() => onDone(null)}>
            {prompt.cancelText || 'Hủy'}
          </Button>
          <Button variant="primary" onClick={submit}>
            {prompt.confirmText || 'Đồng ý'}
          </Button>
        </>
      }
    >
      <form onSubmit={submit}>
        {prompt.message && <p className="confirm-message">{prompt.message}</p>}
        <Input
          ref={inputRef}
          type={prompt.inputType || 'text'}
          min={prompt.min}
          step={prompt.step}
          placeholder={prompt.placeholder}
          value={value}
          error={error}
          autoFocus
          onChange={(e) => {
            setValue(e.target.value);
            if (error) setError(null);
          }}
        />
      </form>
    </Modal>
  );
}

/** Gắn một lần ở gốc ứng dụng: hiện toast góc trên bên phải và hộp xác nhận */
export default function ToastHost() {
  const [toasts, setToasts] = useState([]);
  const [confirms, setConfirms] = useState([]);
  const [prompts, setPrompts] = useState([]);

  useEffect(
    () =>
      subscribeToasts((event) => {
        if (event.kind === 'toast')
          // Cùng nội dung đang hiện thì không chồng thêm một thông báo nữa
          setToasts((list) =>
            list.some((t) => t.type === event.toast.type && t.message === event.toast.message)
              ? list
              : [...list.slice(-4), event.toast]
          );
        else if (event.kind === 'dismiss') setToasts((list) => list.filter((t) => t.id !== event.id));
        else if (event.kind === 'confirm') setConfirms((list) => [...list, event.confirm]);
        else if (event.kind === 'prompt') setPrompts((list) => [...list, event.prompt]);
      }),
    []
  );

  const remove = (id) => setToasts((list) => list.filter((t) => t.id !== id));
  const current = confirms[0];
  const answer = (value) => {
    current.resolve(value);
    setConfirms((list) => list.slice(1));
  };
  const currentPrompt = prompts[0];
  const answerPrompt = (value) => {
    currentPrompt.resolve(value);
    setPrompts((list) => list.slice(1));
  };

  return (
    <>
      <div className="toast-stack" aria-live="polite">
        {toasts.map((t) => (
          <ToastItem key={t.id} toast={t} onClose={() => remove(t.id)} />
        ))}
      </div>
      {/* Hộp nhập luôn giữ nguyên khi có hộp xác nhận chen vào (xác nhận hiện đè lên trên), để không mất giá trị đang gõ */}
      {currentPrompt && <PromptModal key={currentPrompt.id} prompt={currentPrompt} onDone={answerPrompt} />}
      {current && (
        <Modal
          isOpen
          onClose={() => answer(false)}
          title={current.title || 'Xác nhận'}
          maxWidth="420px"
          footer={
            <>
              <Button variant="secondary" onClick={() => answer(false)}>
                {current.cancelText || 'Hủy'}
              </Button>
              <Button variant={current.danger ? 'danger' : 'primary'} onClick={() => answer(true)} autoFocus>
                {current.confirmText || 'Đồng ý'}
              </Button>
            </>
          }
        >
          <p className="confirm-message">{current.message}</p>
        </Modal>
      )}
    </>
  );
}
