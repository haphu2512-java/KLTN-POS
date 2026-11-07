import { useEffect, useRef, useState } from 'react';
import Modal from './Modal.jsx';
import Button from './Button.jsx';
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

/** Gắn một lần ở gốc ứng dụng: hiện toast góc trên bên phải và hộp xác nhận */
export default function ToastHost() {
  const [toasts, setToasts] = useState([]);
  const [confirms, setConfirms] = useState([]);

  useEffect(
    () =>
      subscribeToasts((event) => {
        if (event.kind === 'toast') setToasts((list) => [...list.slice(-4), event.toast]);
        else if (event.kind === 'dismiss') setToasts((list) => list.filter((t) => t.id !== event.id));
        else if (event.kind === 'confirm') setConfirms((list) => [...list, event.confirm]);
      }),
    []
  );

  const remove = (id) => setToasts((list) => list.filter((t) => t.id !== id));
  const current = confirms[0];
  const answer = (value) => {
    current.resolve(value);
    setConfirms((list) => list.slice(1));
  };

  return (
    <>
      <div className="toast-stack" aria-live="polite">
        {toasts.map((t) => (
          <ToastItem key={t.id} toast={t} onClose={() => remove(t.id)} />
        ))}
      </div>
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
