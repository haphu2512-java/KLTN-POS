import { useEffect, useRef } from 'react';
import './Modal.css';

/**
 * Hộp thoại dùng chung. Chỉ đóng bằng nút × / nút Hủy (hoặc phím Esc);
 * bấm ra ngoài vùng tối KHÔNG đóng, tránh mất dữ liệu đang nhập do bấm nhầm.
 */
export default function Modal({
  isOpen,
  onClose,
  title,
  children,
  footer,
  maxWidth = '520px'
}) {
  const overlayRef = useRef(null);

  useEffect(() => {
    function handleKeyDown(e) {
      if (e.key !== 'Escape' || !isOpen || !onClose) return;
      // Chỉ hộp thoại trên cùng đang hiển thị mới đóng (bỏ qua hộp thoại trong trang đang ẩn
      // khi chuyển màn hình, và hộp bên dưới khi đang mở hộp xác nhận chồng lên)
      const shown = [...document.querySelectorAll('.modal-overlay')].filter((el) => el.getClientRects().length > 0);
      if (shown[shown.length - 1] === overlayRef.current) {
        onClose();
      }
    }
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  return (
    <div className="modal-overlay" ref={overlayRef}>
      <div className="modal-dialog" style={{ maxWidth }} role="dialog" aria-modal="true">
        <div className="modal-header">
          <h3 className="modal-title">{title}</h3>
          <button
            type="button"
            className="modal-close-btn"
            onClick={onClose}
            aria-label="Đóng"
          >×</button>
        </div>
        <div className="modal-body">{children}</div>
        {footer && <div className="modal-footer">{footer}</div>}
      </div>
    </div>
  );
}
