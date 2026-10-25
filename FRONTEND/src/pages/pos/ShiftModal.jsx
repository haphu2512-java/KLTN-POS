import { useEffect, useMemo, useState } from 'react';
import { Modal, Button, Input } from '../../components/common';
import { formatCurrency, formatDateTime } from '../../utils/formatters';
import { shiftService } from '../../services/shiftService';
import { staffService } from '../../services/staffService';
import '../shifts/ShiftsPage.css';

const toTime = (t) => (t ? String(t).slice(0, 5) : '');
const DENOMINATIONS = [500000, 200000, 100000, 50000, 20000, 10000, 5000, 2000, 1000, 500];

const differenceText = (d) => {
  if (d == null) return '—';
  if (d === 0) return 'Khớp';
  return d < 0 ? `Thiếu ${formatCurrency(-d)}` : `Dư ${formatCurrency(d)}`;
};

const diffColor = (d) => (d == null || d === 0 ? 'var(--success-700)' : d < 0 ? 'var(--danger-600)' : 'var(--warning-700)');

const toAmount = (v) => {
  if (v === '' || v == null) return null;
  const n = Number(v);
  return Number.isFinite(n) && n >= 0 ? n : NaN;
};

/**
 * Mở ca: nhận két từ ca trước, ca thuộc về nhân viên quản lý đã xếp (quản lý chọn được người trực thay).
 * Đóng ca: phiếu đối soát tiền mặt (đếm theo mệnh giá), VietQR, thẻ; chia tiền để lại / nộp quỹ; ghi bàn giao.
 */
export default function ShiftModal({ isOpen, onClose, current, reason, onChanged, role }) {
  // Chốt chế độ lúc mở hộp: đóng ca xong POS tải lại ca (không còn ca mở) nhưng hộp vẫn hiện phiếu kết ca
  const [mode] = useState(current?.session ? 'close' : 'open');
  return mode === 'close' ? (
    <CloseShift isOpen={isOpen} onClose={onClose} current={current} onChanged={onChanged} />
  ) : (
    <OpenShift isOpen={isOpen} onClose={onClose} current={current} reason={reason} onChanged={onChanged} role={role} />
  );
}

function OpenShift({ isOpen, onClose, current, reason, onChanged, role }) {
  const previous = current?.previousShift || null;
  const isManager = role === 'ADMIN' || role === 'MANAGER';
  // Hộp luôn đóng được ("Để sau", Esc, nút ×) để không khóa màn hình; chỉ nhắc khi đang bán hàng
  const saleBlocked = current?.shiftRequired && reason === 'sale';
  const handoverExpected = current?.handoverExpected != null ? Number(current.handoverExpected) : null;

  const [workShifts, setWorkShifts] = useState([]);
  const [staff, setStaff] = useState([]);
  const [workShiftId, setWorkShiftId] = useState(current?.suggestedWorkShiftId ? String(current.suggestedWorkShiftId) : '');
  const [assigned, setAssigned] = useState({ id: current?.assignedStaffId || null, name: current?.assignedStaffName || '' });
  const [staffId, setStaffId] = useState(current?.assignedStaffId ? String(current.assignedStaffId) : '');
  const [cash, setCash] = useState(current?.suggestedOpeningCash != null ? String(Number(current.suggestedOpeningCash)) : '0');
  const [handoverReason, setHandoverReason] = useState('');
  const [overrideReason, setOverrideReason] = useState('');
  const [note, setNote] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    shiftService
      .getWorkShifts()
      .then((list) => setWorkShifts(list.filter((ws) => ws.active)))
      .catch(() => setError('Không tải được danh sách ca. Kiểm tra kết nối máy chủ.'));
    staffService.list().then((list) => setStaff(list.filter((s) => s.active))).catch(() => setStaff([]));
  }, []);

  // Đổi ca thì xem lại người được xếp vào ca đó hôm nay
  useEffect(() => {
    if (!workShiftId) return;
    const today = new Date();
    const iso = new Date(today.getTime() - today.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
    let alive = true;
    shiftService
      .getAssignments({ from: iso, to: iso })
      .then((list) => {
        if (!alive) return;
        const slot = list.find((a) => String(a.workShiftId) === String(workShiftId));
        setAssigned({ id: slot?.staffMemberId || null, name: slot?.staffName || '' });
        setStaffId(slot?.staffMemberId ? String(slot.staffMemberId) : '');
      })
      .catch(() => {});
    return () => {
      alive = false;
    };
  }, [workShiftId]);

  const amount = toAmount(cash);
  const cashValid = amount != null && !Number.isNaN(amount);
  const handoverDiff = cashValid && handoverExpected != null ? amount - handoverExpected : null;
  const substitute = isManager && staffId && String(staffId) !== String(assigned.id || '');

  const handleSubmit = async () => {
    if (!cashValid) return setError('Nhập số tiền hợp lệ (không âm).');
    if (!workShiftId) return setError('Chọn ca làm việc.');
    if (!staffId) {
      return setError(isManager ? 'Chọn nhân viên trực ca.' : 'Ca này chưa được xếp cho ai. Nhờ quản lý xếp ca.');
    }
    if (handoverDiff && !handoverReason.trim()) return setError('Ghi lý do tiền trong két khác số ca trước để lại.');
    setSubmitting(true);
    setError('');
    try {
      await shiftService.openShift({
        workShiftId: Number(workShiftId),
        openingCash: amount,
        note,
        staffMemberId: Number(staffId),
        handoverReason: handoverDiff ? handoverReason : undefined,
        overrideReason: overrideReason || undefined
      });
      onChanged?.();
      onClose();
    } catch (err) {
      setError(err.response?.data?.message || 'Không kết nối được máy chủ, ca chưa được ghi nhận.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="Mở ca · nhận két"
      maxWidth="500px"
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Để sau</Button>
          <Button variant="primary" onClick={handleSubmit} disabled={submitting}>Mở ca</Button>
        </>
      }
    >
      <div className="shift-form">
        {saleBlocked && (
          <div className="shift-banner is-warning">
            Cần mở ca trước khi thanh toán. Bấm "Để sau" nếu muốn tiếp tục chuẩn bị đơn.
          </div>
        )}
        {previous && (
          <div className="shift-banner">
            <div style={{ fontWeight: 600 }}>
              Nhận từ ca trước: {previous.workShiftName} · {previous.employeeName}
            </div>
            <div>Để lại trong két: {formatCurrency(handoverExpected)}</div>
            {previous.handoverNote && <div>Bàn giao: {previous.handoverNote}</div>}
          </div>
        )}

        <label className="input-label" htmlFor="shift-select">Ca làm việc</label>
        <select id="shift-select" className="input-control" value={workShiftId} onChange={(e) => setWorkShiftId(e.target.value)}>
          <option value="">Chọn ca</option>
          {workShifts.map((ws) => (
            <option key={ws.id} value={ws.id}>
              {ws.name} ({toTime(ws.startTime)} - {toTime(ws.endTime)})
            </option>
          ))}
        </select>

        <label className="input-label" htmlFor="staff-select">Nhân viên trực</label>
        {isManager ? (
          <select id="staff-select" className="input-control" value={staffId} onChange={(e) => setStaffId(e.target.value)}>
            <option value="">Chọn nhân viên</option>
            {staff.map((s) => (
              <option key={s.id} value={s.id}>
                {s.fullName}{String(s.id) === String(assigned.id || '') ? ' (được xếp)' : ''}
              </option>
            ))}
          </select>
        ) : (
          <div className={`shift-banner${assigned.id ? '' : ' is-warning'}`}>
            {assigned.id ? assigned.name : 'Ca này hôm nay chưa được xếp cho ai. Nhờ quản lý xếp ca hoặc mở ca trực thay.'}
          </div>
        )}
        {isManager && (
          <Input
            label={
              substitute
                ? `Lý do trực thay ${assigned.name || ''} (bắt buộc)`
                : !assigned.id
                  ? 'Lý do mở ca chưa xếp ai (bắt buộc)'
                  : 'Lý do nếu mở ngoài khung giờ của ca'
            }
            value={overrideReason}
            maxLength={255}
            onChange={(e) => setOverrideReason(e.target.value)}
          />
        )}

        <Input label="Tiền mặt đếm được trong két (VNĐ)" type="number" min="0" value={cash} onChange={(e) => setCash(e.target.value)} autoFocus />
        {handoverDiff != null && handoverDiff !== 0 && (
          <>
            <div style={{ fontWeight: 600, color: diffColor(handoverDiff) }}>
              Lệch với ca trước để lại: {differenceText(handoverDiff)}. Khoản này không tính vào ca của bạn.
            </div>
            <Input label="Lý do chênh lệch khi nhận két" value={handoverReason} maxLength={255} onChange={(e) => setHandoverReason(e.target.value)} />
          </>
        )}
        <Input label="Ghi chú nhận ca" value={note} maxLength={500} onChange={(e) => setNote(e.target.value)} placeholder="Vd. đã đếm lại két, đủ tiền" />
        {error && <div className="shift-error-text">{error}</div>}
      </div>
    </Modal>
  );
}

function CloseShift({ isOpen, onClose, current, onChanged }) {
  // Giữ số liệu lúc mở hộp: chốt xong POS tải lại và ca không còn trong current
  const [session] = useState(current.session);
  const threshold = Number(current?.differenceThreshold || 0);
  const standardFloat = Number(current?.standardFloat || 0);

  const [useDenoms, setUseDenoms] = useState(false);
  const [denoms, setDenoms] = useState({});
  const [cash, setCash] = useState('');
  const [vietqr, setVietqr] = useState('');
  const [card, setCard] = useState('');
  const [differenceReason, setDifferenceReason] = useState('');
  const [left, setLeft] = useState('');
  const [note, setNote] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [closedResult, setClosedResult] = useState(null);

  const denomTotal = useMemo(
    () => DENOMINATIONS.reduce((sum, d) => sum + d * (Number(denoms[d]) || 0), 0),
    [denoms]
  );
  const counted = useDenoms ? denomTotal : toAmount(cash);
  const countedValid = counted != null && !Number.isNaN(counted);

  const expectedCash = Number(session.expectedCash || 0);
  const expectedVietqr = Number(session.expectedVietqr || 0);
  const expectedCard = Number(session.expectedCard || 0);
  const vietqrAmount = toAmount(vietqr);
  const cardAmount = toAmount(card);

  const cashDiff = countedValid ? counted - expectedCash : null;
  const vietqrDiff = vietqrAmount == null || Number.isNaN(vietqrAmount) ? 0 : vietqrAmount - expectedVietqr;
  const cardDiff = cardAmount == null || Number.isNaN(cardAmount) ? 0 : cardAmount - expectedCard;
  const needsReason = [cashDiff, vietqrDiff, cardDiff].some((d) => d != null && Math.abs(d) > threshold);

  const defaultLeft = countedValid ? Math.min(counted, standardFloat || counted) : 0;
  const leftAmount = left === '' ? defaultLeft : toAmount(left);
  const finalDrop = countedValid && leftAmount != null && !Number.isNaN(leftAmount) ? counted - leftAmount : null;

  const handleSubmit = async () => {
    if (!countedValid) return setError('Nhập số tiền mặt đếm được hợp lệ.');
    if (Number.isNaN(vietqrAmount) || Number.isNaN(cardAmount)) return setError('Số tiền VietQR / thẻ không hợp lệ.');
    if (leftAmount == null || Number.isNaN(leftAmount) || leftAmount > counted) {
      return setError('Tiền để lại cho ca sau phải từ 0 tới số tiền đếm được.');
    }
    if (needsReason && !differenceReason.trim()) return setError('Có chênh lệch, ghi lý do trước khi chốt ca.');
    setSubmitting(true);
    setError('');
    try {
      const closed = await shiftService.closeCurrent({
        closingCash: counted,
        denominations: useDenoms
          ? Object.fromEntries(DENOMINATIONS.filter((d) => Number(denoms[d]) > 0).map((d) => [d, Number(denoms[d])]))
          : undefined,
        countedVietqr: vietqrAmount ?? undefined,
        countedCard: cardAmount ?? undefined,
        differenceReason: needsReason ? differenceReason : undefined,
        leftInDrawer: leftAmount,
        handoverNote: note
      });
      setClosedResult(closed);
      onChanged?.();
    } catch (err) {
      setError(err.response?.data?.message || 'Không kết nối được máy chủ, ca chưa được chốt.');
    } finally {
      setSubmitting(false);
    }
  };

  const row = (label, value, opts = {}) => (
    <div className={`shift-calc-row${opts.total ? ' is-total' : ''}`}>
      <span>{label}</span>
      <span style={opts.color ? { color: opts.color, fontWeight: 700 } : undefined}>{value}</span>
    </div>
  );

  if (closedResult) {
    const pending = closedResult.status === 'PENDING_REVIEW';
    return (
      <Modal
        isOpen={isOpen}
        onClose={onClose}
        title={`Đã chốt ${closedResult.workShiftName}`}
        maxWidth="460px"
        footer={
          <>
            <Button variant="secondary" onClick={() => window.print()}>In phiếu</Button>
            <Button variant="primary" onClick={onClose}>Xong</Button>
          </>
        }
      >
        <div className="shift-form">
          {row('Nhân viên trực', closedResult.employeeName)}
          {row('Số hóa đơn', closedResult.invoiceCount)}
          {row('Tiền mặt dự kiến', formatCurrency(closedResult.expectedCash))}
          {row('Tiền mặt đếm được', formatCurrency(closedResult.closingCash))}
          {row('Chênh lệch tiền mặt', differenceText(Number(closedResult.cashDifference)), { color: diffColor(Number(closedResult.cashDifference)) })}
          {row('Chênh lệch VietQR', differenceText(Number(closedResult.vietqrDifference)), { color: diffColor(Number(closedResult.vietqrDifference)) })}
          {row('Chênh lệch thẻ', differenceText(Number(closedResult.cardDifference)), { color: diffColor(Number(closedResult.cardDifference)) })}
          {row('Để lại cho ca sau', formatCurrency(closedResult.leftInDrawer))}
          {row('Nộp quỹ cuối ca', formatCurrency(closedResult.finalDrop), { total: true })}
          <div className={`shift-banner${pending ? ' is-warning' : ''}`}>
            {pending ? 'Ca có chênh lệch, đang chờ quản lý duyệt.' : 'Ca khớp tiền, đã khóa sổ.'}
          </div>
        </div>
      </Modal>
    );
  }

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={`Kết ca · ${session.workShiftName} · ${session.employeeName}`}
      maxWidth="600px"
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Để sau</Button>
          <Button variant="primary" onClick={handleSubmit} disabled={submitting}>Chốt ca</Button>
        </>
      }
    >
      <div className="shift-form">
        <section className="shift-detail-card">
          <h4>Tiền mặt trong két</h4>
          {row('Mở lúc', formatDateTime(session.openedAt))}
          {row('Tiền đầu ca', formatCurrency(session.openingCash))}
          {row(`+ Bán tiền mặt (${session.invoiceCount} HĐ)`, formatCurrency(session.cashSales))}
          {Number(session.cashRefunds) > 0 && row('− Hoàn trả tiền mặt', formatCurrency(session.cashRefunds))}
          {Number(session.expenses) > 0 && row('− Chi trong ca', formatCurrency(session.expenses))}
          {Number(session.drops) > 0 && row('− Nộp quỹ giữa ca', formatCurrency(session.drops))}
          {Number(session.floatIn) > 0 && row('+ Bổ sung tiền lẻ', formatCurrency(session.floatIn))}
          {row('= Tiền mặt dự kiến', formatCurrency(expectedCash), { total: true })}
        </section>

        <label className="shift-check">
          <input type="checkbox" checked={useDenoms} onChange={(e) => setUseDenoms(e.target.checked)} />
          Đếm theo mệnh giá
        </label>
        {useDenoms ? (
          <div className="shift-denoms">
            {DENOMINATIONS.map((d) => (
              <label key={d} className="shift-denom">
                <span>{formatCurrency(d)}</span>
                <input
                  type="number"
                  min="0"
                  className="shift-input"
                  value={denoms[d] ?? ''}
                  onChange={(e) => setDenoms((prev) => ({ ...prev, [d]: e.target.value }))}
                />
              </label>
            ))}
            <div className="shift-denom-total">Tổng: {formatCurrency(denomTotal)}</div>
          </div>
        ) : (
          <Input label="Tiền mặt đếm được (VNĐ)" type="number" min="0" value={cash} onChange={(e) => setCash(e.target.value)} autoFocus />
        )}

        <table className="shift-recon">
          <thead>
            <tr><th>Phương thức</th><th>Hệ thống</th><th>Thực tế</th><th>Chênh lệch</th></tr>
          </thead>
          <tbody>
            <tr>
              <td>Tiền mặt</td>
              <td>{formatCurrency(expectedCash)}</td>
              <td>{countedValid ? formatCurrency(counted) : '—'}</td>
              <td style={{ color: diffColor(cashDiff), fontWeight: 600 }}>{differenceText(cashDiff)}</td>
            </tr>
            <tr>
              <td>VietQR</td>
              <td>{formatCurrency(expectedVietqr)}</td>
              <td>
                <input type="number" min="0" className="shift-input" placeholder="Theo sao kê" value={vietqr} onChange={(e) => setVietqr(e.target.value)} />
              </td>
              <td style={{ color: diffColor(vietqrDiff), fontWeight: 600 }}>{differenceText(vietqrDiff)}</td>
            </tr>
            <tr>
              <td>Thẻ</td>
              <td>{formatCurrency(expectedCard)}</td>
              <td>
                <input type="number" min="0" className="shift-input" placeholder="Theo máy POS" value={card} onChange={(e) => setCard(e.target.value)} />
              </td>
              <td style={{ color: diffColor(cardDiff), fontWeight: 600 }}>{differenceText(cardDiff)}</td>
            </tr>
          </tbody>
        </table>
        <div className="shift-muted">VietQR và thẻ để trống là khớp với số hệ thống.</div>

        {needsReason && (
          <Input label="Lý do chênh lệch (bắt buộc)" value={differenceReason} maxLength={500} onChange={(e) => setDifferenceReason(e.target.value)} />
        )}

        <Input
          label="Để lại trong két cho ca sau (VNĐ)"
          type="number"
          min="0"
          value={left}
          placeholder={String(defaultLeft)}
          onChange={(e) => setLeft(e.target.value)}
        />
        {finalDrop != null && finalDrop >= 0 && <div className="shift-muted">Nộp quỹ cuối ca: {formatCurrency(finalDrop)}</div>}
        <Input
          label="Ghi chú bàn giao cho ca sau"
          value={note}
          maxLength={500}
          onChange={(e) => setNote(e.target.value)}
          placeholder="Vd. hết túi nilon, khách đặt 2 thùng nước giao chiều nay"
        />
        {error && <div className="shift-error-text">{error}</div>}
      </div>
    </Modal>
  );
}
