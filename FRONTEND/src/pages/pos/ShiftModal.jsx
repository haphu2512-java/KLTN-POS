import { useEffect, useState } from 'react';
import { Modal, Button, Input } from '../../components/common';
import { formatCurrency, formatDateTime } from '../../utils/formatters';
import { shiftService } from '../../services/shiftService';

const toTime = (t) => (t ? String(t).slice(0, 5) : '');

/**
 * Mở ca (chọn ca, nhập tiền đầu ca, xem ghi chú bàn giao của ca trước)
 * hoặc đóng ca (đếm tiền mặt trong két, hệ thống so với số tiền lẽ ra phải có).
 */
export default function ShiftModal({ isOpen, onClose, current, reason, onChanged }) {
  const session = current?.session || null;
  const previous = current?.previousShift || null;
  // Hộp luôn đóng được ("Để sau", Esc, nút ×) để không khóa màn hình; chỉ nhắc khi đang bán hàng
  const saleBlocked = !session && current?.shiftRequired && reason === 'sale';

  // PosPage chỉ mount hộp này khi mở, nên giá trị ban đầu lấy thẳng từ ca hiện tại
  const [workShifts, setWorkShifts] = useState([]);
  const [workShiftId, setWorkShiftId] = useState(
    current?.suggestedWorkShiftId ? String(current.suggestedWorkShiftId) : ''
  );
  const [cash, setCash] = useState(
    session ? '' : current?.suggestedOpeningCash != null ? String(Number(current.suggestedOpeningCash)) : '0'
  );
  const [note, setNote] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [closedResult, setClosedResult] = useState(null);

  useEffect(() => {
    if (session) return;
    shiftService
      .getWorkShifts()
      .then((list) => setWorkShifts(list.filter((ws) => ws.active)))
      .catch(() => setError('Không tải được danh sách ca. Kiểm tra kết nối máy chủ.'));
  }, [session]);

  const amount = Number(cash);
  const cashValid = cash !== '' && Number.isFinite(amount) && amount >= 0;
  const difference = session && cashValid ? amount - Number(session.expectedCash || 0) : null;

  const handleSubmit = async () => {
    if (!cashValid) {
      setError('Nhập số tiền hợp lệ (không âm).');
      return;
    }
    if (!session && !workShiftId) {
      setError('Chọn ca làm việc.');
      return;
    }
    setSubmitting(true);
    setError('');
    try {
      if (session) {
        const closed = await shiftService.closeCurrent({ closingCash: amount, handoverNote: note });
        setClosedResult(closed);
      } else {
        await shiftService.openShift({ workShiftId: Number(workShiftId), openingCash: amount, note });
        onClose();
      }
      onChanged?.();
    } catch (err) {
      setError(err.response?.data?.message || 'Không kết nối được máy chủ, ca chưa được ghi nhận.');
    } finally {
      setSubmitting(false);
    }
  };

  const row = (label, value, strong) => (
    <div style={{ display: 'flex', justifyContent: 'space-between', padding: '4px 0', fontWeight: strong ? 700 : 400 }}>
      <span style={{ color: 'var(--text-muted)' }}>{label}</span>
      <span>{value}</span>
    </div>
  );

  const differenceText = (d) => {
    if (d == null) return '—';
    if (d === 0) return 'Khớp';
    return d < 0 ? `Thiếu ${formatCurrency(-d)}` : `Dư ${formatCurrency(d)}`;
  };

  if (closedResult) {
    const d = Number(closedResult.cashDifference || 0);
    return (
      <Modal
        isOpen={isOpen}
        onClose={onClose}
        title={`Đã đóng ${closedResult.workShiftName}`}
        footer={<Button variant="primary" onClick={onClose}>Xong</Button>}
      >
        <div style={{ fontSize: '13px' }}>
          {row('Số hóa đơn', closedResult.invoiceCount)}
          {row('Tiền mặt dự kiến trong két', formatCurrency(closedResult.expectedCash))}
          {row('Tiền mặt đếm được', formatCurrency(closedResult.closingCash))}
          {row('Chênh lệch', differenceText(d), true)}
          {d !== 0 && (
            <p style={{ color: 'var(--danger-600)', marginTop: '8px' }}>
              Chênh lệch đã được ghi vào lịch sử ca để quản lý đối chiếu.
            </p>
          )}
        </div>
      </Modal>
    );
  }

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={session ? `Đóng ${session.workShiftName}` : 'Mở ca làm việc'}
      maxWidth="480px"
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Để sau
          </Button>
          <Button variant="primary" onClick={handleSubmit} disabled={submitting}>
            {session ? 'Chốt tiền và đóng ca' : 'Mở ca'}
          </Button>
        </>
      }
    >
      <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', fontSize: '13px' }}>
        {session ? (
          <>
            <div>
              {row('Ca', `${session.workShiftName} (${session.workShiftTime})`)}
              {row('Mở lúc', formatDateTime(session.openedAt))}
              {row('Tiền đầu ca', formatCurrency(session.openingCash))}
              {row(`Bán tiền mặt (${session.invoiceCount} hóa đơn)`, formatCurrency(session.cashSales))}
              {row('Thẻ / chuyển khoản', formatCurrency(session.nonCashSales))}
              {row('Tiền mặt lẽ ra có trong két', formatCurrency(session.expectedCash), true)}
            </div>
            <Input
              label="Tiền mặt đếm được trong két (VNĐ)"
              type="number"
              min="0"
              value={cash}
              onChange={(e) => setCash(e.target.value)}
              autoFocus
            />
            <div style={{ fontWeight: 700, color: difference ? 'var(--danger-600)' : 'var(--text-main)' }}>
              {differenceText(difference)}
            </div>
            <Input
              label="Ghi chú bàn giao cho ca sau"
              value={note}
              maxLength={500}
              onChange={(e) => setNote(e.target.value)}
              placeholder="Vd. hết túi nilon, khách đặt 2 thùng nước giao chiều nay"
            />
          </>
        ) : (
          <>
            {saleBlocked && (
              <div style={{ background: 'var(--warning-50)', color: 'var(--warning-700)', padding: '8px 10px', borderRadius: '6px' }}>
                Cần mở ca trước khi thanh toán. Bấm "Để sau" nếu muốn tiếp tục chuẩn bị đơn.
              </div>
            )}
            {previous && (
              <div style={{ background: 'var(--bg-subtle)', padding: '8px 10px', borderRadius: '6px' }}>
                <div style={{ fontWeight: 600 }}>
                  Ca trước: {previous.workShiftName} · {previous.employeeName}
                </div>
                <div>Để lại trong két: {formatCurrency(previous.closingCash)}</div>
                {previous.handoverNote && <div>Bàn giao: {previous.handoverNote}</div>}
              </div>
            )}
            <label className="input-label" htmlFor="shift-select">
              Ca làm việc
            </label>
            <select
              id="shift-select"
              className="input-control"
              value={workShiftId}
              onChange={(e) => setWorkShiftId(e.target.value)}
            >
              <option value="">Chọn ca</option>
              {workShifts.map((ws) => (
                <option key={ws.id} value={ws.id}>
                  {ws.name} ({toTime(ws.startTime)} - {toTime(ws.endTime)})
                </option>
              ))}
            </select>
            <Input
              label="Tiền mặt đầu ca trong két (VNĐ)"
              type="number"
              min="0"
              value={cash}
              onChange={(e) => setCash(e.target.value)}
              autoFocus
            />
            <Input
              label="Ghi chú nhận ca"
              value={note}
              maxLength={500}
              onChange={(e) => setNote(e.target.value)}
              placeholder="Vd. đã đếm lại két, đủ tiền"
            />
          </>
        )}
        {error && <div style={{ color: 'var(--danger-600)' }}>{error}</div>}
      </div>
    </Modal>
  );
}
