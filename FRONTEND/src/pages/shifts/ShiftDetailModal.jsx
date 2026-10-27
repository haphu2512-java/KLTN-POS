import { useEffect, useState } from 'react';
import { Modal, Button, Badge, Table } from '../../components/common';
import { formatCurrency, formatDateTime } from '../../utils/formatters';
import { shiftService } from '../../services/shiftService';
import DifferenceCell from './DifferenceCell';
import { PAYMENT_LABELS, MOVEMENT_LABELS, formatDay } from './shiftUtils';
import { findClues } from './shiftClues';

/**
 * Chi tiết một ca để dò nguyên nhân thiếu/dư: cách tính tiền két, đối soát VietQR/thẻ, nhận/bàn giao két,
 * thu/chi két và từng hóa đơn làm tiền két thay đổi. Quản lý/quản trị đóng hộ ca quên đóng và duyệt ca lệch.
 */
export default function ShiftDetailModal({ shiftId, canForceClose, onClose, onChanged }) {
  const [shift, setShift] = useState(null);
  const [invoices, setInvoices] = useState([]);
  const [error, setError] = useState('');
  const [closing, setClosing] = useState(false);
  const [countedCash, setCountedCash] = useState('');
  const [closeNote, setCloseNote] = useState('');
  const [closeReason, setCloseReason] = useState('');
  const [movements, setMovements] = useState([]);
  const [reviewNote, setReviewNote] = useState('');

  useEffect(() => {
    let alive = true;
    Promise.all([
      shiftService.getShift(shiftId),
      shiftService.getShiftInvoices(shiftId),
      shiftService.getShiftCashMovements(shiftId)
    ])
      .then(([s, list, moves]) => {
        if (!alive) return;
        setShift(s);
        setInvoices(list);
        setMovements(moves);
      })
      .catch((err) => alive && setError(err.response?.data?.message || 'Không tải được chi tiết ca.'));
    return () => {
      alive = false;
    };
  }, [shiftId]);

  const handleForceClose = async () => {
    const amount = Number(countedCash);
    if (countedCash === '' || !Number.isFinite(amount) || amount < 0) {
      setError('Nhập số tiền đếm được hợp lệ (không âm).');
      return;
    }
    setClosing(true);
    try {
      const closed = await shiftService.closeShiftById(shiftId, {
        closingCash: amount,
        differenceReason: closeReason.trim() || undefined,
        handoverNote: closeNote.trim() || 'Quản lý đóng hộ'
      });
      setShift(closed);
      setError('');
      onChanged?.();
    } catch (err) {
      setError(err.response?.data?.message || 'Không đóng được ca.');
    } finally {
      setClosing(false);
    }
  };

  const handleReview = async () => {
    if (!reviewNote.trim()) {
      setError('Ghi cách xử lý chênh lệch trước khi duyệt.');
      return;
    }
    setClosing(true);
    try {
      setShift(await shiftService.reviewShift(shiftId, reviewNote.trim()));
      setError('');
      onChanged?.();
    } catch (err) {
      setError(err.response?.data?.message || 'Không duyệt được ca.');
    } finally {
      setClosing(false);
    }
  };

  const line = (label, value, opts = {}) => (
    <div className={`shift-calc-row${opts.total ? ' is-total' : ''}`}>
      <span>{label}</span>
      <span>{value}</span>
    </div>
  );

  const clues = shift ? findClues(shift, invoices) : [];
  const legacyClosed = shift?.status === 'CLOSED' && shift.cashRefunds == null;

  const invoiceColumns = [
    { key: 'id', title: 'HĐ', width: '64px', render: (val) => `#${val}` },
    { key: 'createdAt', title: 'Lúc bán', render: (val) => formatDateTime(val) },
    {
      key: 'paymentMethod',
      title: 'Thanh toán',
      render: (val) => (val === 'CASH' ? <strong>{PAYMENT_LABELS[val]}</strong> : PAYMENT_LABELS[val] || val)
    },
    { key: 'totalAmount', title: 'Tổng tiền', align: 'right', render: (val) => formatCurrency(val) },
    {
      key: 'receivedAmount',
      title: 'Khách đưa / thối',
      align: 'right',
      render: (val, inv) =>
        inv.paymentMethod === 'CASH' ? (
          <span>
            {formatCurrency(val)}
            <span className="shift-muted"> / {formatCurrency(inv.changeAmount)}</span>
          </span>
        ) : (
          '—'
        )
    },
    {
      key: 'kind',
      title: 'Ảnh hưởng két',
      render: (val, inv) => {
        if (val === 'REFUND_OTHER_SHIFT') {
          return <Badge variant="warning">Hoàn trả HĐ ca khác</Badge>;
        }
        if (inv.status === 'REFUNDED') {
          return inv.refundCountedHere ? (
            <Badge variant="warning">Đã hoàn trả trong ca</Badge>
          ) : (
            <Badge variant="neutral">Hoàn trả ở ca khác</Badge>
          );
        }
        return inv.paymentMethod === 'CASH' ? <Badge variant="success">Vào két</Badge> : <Badge variant="neutral">Không vào két</Badge>;
      }
    }
  ];

  return (
    <Modal
      isOpen
      onClose={onClose}
      maxWidth="860px"
      title={shift ? `${shift.workShiftName} · ${shift.employeeName} · ${formatDay(shift.shiftDate)}` : 'Chi tiết ca'}
    >
      {error && <div className="shift-error">{error}</div>}
      {!shift ? (
        !error && <div className="shift-muted">Đang tải...</div>
      ) : (
        <div className="shift-detail">
          <div className="shift-detail-grid">
            <section className="shift-detail-card">
              <h4>Tiền mặt trong két</h4>
              {line('Tiền đầu ca', formatCurrency(shift.openingCash))}
              {line(`+ Bán tiền mặt`, formatCurrency(shift.cashSales))}
              {line(
                `− Hoàn trả tiền mặt${shift.refundedCount ? ` (${shift.refundedCount} HĐ)` : ''}`,
                legacyClosed ? 'đã trừ vào doanh số' : formatCurrency(shift.cashRefunds)
              )}
              {Number(shift.expenses) > 0 && line('− Chi trong ca', formatCurrency(shift.expenses))}
              {Number(shift.drops) > 0 && line('− Nộp quỹ giữa ca', formatCurrency(shift.drops))}
              {Number(shift.floatIn) > 0 && line('+ Bổ sung tiền lẻ', formatCurrency(shift.floatIn))}
              {line('= Tiền mặt dự kiến', formatCurrency(shift.expectedCash), { total: true })}
              {line('Tiền đếm được', shift.closingCash == null ? 'Chưa đóng ca' : formatCurrency(shift.closingCash))}
              <div className="shift-calc-row is-total">
                <span>Chênh lệch</span>
                <DifferenceCell value={shift.cashDifference} status={shift.status} />
              </div>
              {shift.leftInDrawer != null && line('Để lại cho ca sau', formatCurrency(shift.leftInDrawer))}
              {shift.finalDrop != null && line('Nộp quỹ cuối ca', formatCurrency(shift.finalDrop))}
            </section>

            <section className="shift-detail-card">
              <h4>Đối soát không tiền mặt</h4>
              {line('VietQR hệ thống', formatCurrency(shift.expectedVietqr))}
              {shift.countedVietqr != null && line('VietQR theo sao kê', formatCurrency(shift.countedVietqr))}
              {shift.vietqrDifference != null && (
                <div className="shift-calc-row"><span>Chênh lệch VietQR</span><DifferenceCell value={shift.vietqrDifference} status={shift.status} /></div>
              )}
              {line('Thẻ hệ thống', formatCurrency(shift.expectedCard))}
              {shift.countedCard != null && line('Thẻ theo máy POS', formatCurrency(shift.countedCard))}
              {shift.cardDifference != null && (
                <div className="shift-calc-row"><span>Chênh lệch thẻ</span><DifferenceCell value={shift.cardDifference} status={shift.status} /></div>
              )}
              {shift.differenceReason && <p className="shift-note"><strong>Lý do chênh lệch:</strong> {shift.differenceReason}</p>}
              {shift.reviewNote && (
                <p className="shift-note">
                  <strong>Đã duyệt bởi {shift.reviewedByName || `#${shift.reviewedBy}`}:</strong> {shift.reviewNote}
                </p>
              )}
            </section>

            <section className="shift-detail-card">
              <h4>Thông tin ca</h4>
              {line('Chi nhánh', shift.branchName || `#${shift.branchId}`)}
              {line('Giờ ca', shift.workShiftTime)}
              {line('Nhân viên trực', shift.employeeName)}
              {shift.openedByName && line('Tài khoản mở ca', shift.openedByName)}
              {line('Mở lúc', formatDateTime(shift.openedAt))}
              {line('Đóng lúc', shift.closedAt ? formatDateTime(shift.closedAt) : '—')}
              {shift.closedByOther && line('Người đóng hộ', shift.closedByName || `#${shift.closedBy}`)}
              {line(`Số hóa đơn bán`, shift.invoiceCount)}
              {line('Thẻ / chuyển khoản', formatCurrency(shift.nonCashSales))}
              {shift.handoverDifference != null && Number(shift.handoverDifference) !== 0 && (
                <div className="shift-calc-row">
                  <span>Lệch khi nhận két (ca trước để lại {formatCurrency(shift.handoverExpected)})</span>
                  <DifferenceCell value={shift.handoverDifference} />
                </div>
              )}
              {shift.handoverReason && <p className="shift-note"><strong>Lý do lệch nhận két:</strong> {shift.handoverReason}</p>}
              {shift.openOverrideReason && <p className="shift-note"><strong>Mở ca ngoại lệ:</strong> {shift.openOverrideReason}</p>}
              {shift.openingNote && <p className="shift-note"><strong>Ghi chú mở ca:</strong> {shift.openingNote}</p>}
              {shift.handoverNote && <p className="shift-note"><strong>Bàn giao:</strong> {shift.handoverNote}</p>}
            </section>
          </div>

          {clues.length > 0 && (
            <section className="shift-clues">
              <h4>Gợi ý dò chênh lệch</h4>
              <ul>
                {clues.map((c) => (
                  <li key={c}>{c}</li>
                ))}
              </ul>
            </section>
          )}

          {shift.status === 'PENDING_REVIEW' && canForceClose && (
            <section className="shift-detail-card shift-force-close">
              <h4>Duyệt ca lệch tiền</h4>
              <p className="shift-muted">Ghi cách xử lý (trừ lương, bỏ qua, đã tìm ra...). Duyệt xong ca được khóa sổ.</p>
              <div className="shift-force-row">
                <input
                  type="text"
                  className="shift-input"
                  placeholder="Cách xử lý chênh lệch"
                  value={reviewNote}
                  maxLength={500}
                  onChange={(e) => setReviewNote(e.target.value)}
                />
                <Button variant="primary" onClick={handleReview} disabled={closing}>Duyệt và khóa sổ</Button>
              </div>
            </section>
          )}

          {shift.status === 'OPEN' && canForceClose && (
            <section className="shift-detail-card shift-force-close">
              <h4>Đóng hộ ca quên đóng</h4>
              <p className="shift-muted">
                Đếm tiền mặt thực có trong két của quầy này. Tiền dự kiến hiện tại: {formatCurrency(shift.expectedCash)}.
              </p>
              <div className="shift-force-row">
                <input
                  type="number"
                  min="0"
                  className="shift-input"
                  placeholder="Tiền đếm được"
                  value={countedCash}
                  onChange={(e) => setCountedCash(e.target.value)}
                />
                <input
                  type="text"
                  className="shift-input"
                  placeholder="Lý do chênh lệch (nếu lệch)"
                  value={closeReason}
                  maxLength={500}
                  onChange={(e) => setCloseReason(e.target.value)}
                />
                <input
                  type="text"
                  className="shift-input"
                  placeholder="Ghi chú (vd: nhân viên quên đóng ca)"
                  value={closeNote}
                  maxLength={500}
                  onChange={(e) => setCloseNote(e.target.value)}
                />
                <Button variant="primary" onClick={handleForceClose} disabled={closing}>
                  {closing ? 'Đang đóng...' : 'Đóng ca'}
                </Button>
              </div>
            </section>
          )}

          {movements.length > 0 && (
            <section>
              <h4 className="shift-section-title">Thu/chi két ({movements.filter((m) => !m.deletedAt).length})</h4>
              <table className="shift-recon">
                <tbody>
                  {movements.map((m) => (
                    <tr key={m.id} style={m.deletedAt ? { opacity: 0.5, textDecoration: 'line-through' } : undefined}>
                      <td>{formatDateTime(m.createdAt)}</td>
                      <td>{MOVEMENT_LABELS[m.type]}</td>
                      <td>{m.type === 'FLOAT_IN' ? '+' : '−'} {formatCurrency(m.amount)}</td>
                      <td>
                        {m.category ? `${m.category}: ` : ''}{m.reason}
                        {m.counterpart ? ` · ${m.counterpart}` : ''}
                      </td>
                      <td className="shift-muted">{m.createdByName}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </section>
          )}

          <section>
            <h4 className="shift-section-title">Hóa đơn của ca ({invoices.length})</h4>
            <Table
              columns={invoiceColumns}
              data={invoices}
              rowKey="id"
              maxHeight="300px"
              emptyText="Ca này chưa có hóa đơn nào"
            />
          </section>
        </div>
      )}
    </Modal>
  );
}
