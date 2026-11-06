import { useEffect, useMemo, useState } from 'react';
import { Modal, Button, Badge, Loading } from '../../components/common';
import { saleService } from '../../services/saleService';
import { formatCurrency, formatDateTime } from '../../utils/formatters';
import { estimateRefund, remainingQty } from '../../utils/returnCalc';
import { printReturnSlip, returnSlipFrom } from '../../utils/receiptPrinter';
import ManagerApprovalModal from './ManagerApprovalModal';
import { PAYMENT_LABELS } from '../shifts/shiftUtils';

const cell = { padding: '8px 10px', borderBottom: '1px solid var(--border-subtle)' };

/**
 * Hoàn trả từng món: chọn số lượng trả của từng dòng, nhập lý do; thu ngân cần quản lý nhập PIN.
 * Tiền hoàn hiển thị là ước tính cùng quy tắc với server (chia đều khuyến mãi, giảm giá, điểm theo giá trị món).
 * Xong thì in phiếu hoàn trả K80 và gọi onDone(result).
 */
export default function ReturnModal({ isOpen, onClose, invoiceId, cashierName = 'Thu ngân', onDone }) {
  const [info, setInfo] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [picks, setPicks] = useState({});
  const [reason, setReason] = useState('');
  const [saving, setSaving] = useState(false);
  const [approvalOpen, setApprovalOpen] = useState(false);
  const [result, setResult] = useState(null);
  const [printMsg, setPrintMsg] = useState('');

  useEffect(() => {
    if (!isOpen || !invoiceId) return;
    let alive = true;
    // Mở lại modal cho hóa đơn khác: xóa lựa chọn cũ rồi tải
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setLoading(true);
    setError('');
    setInfo(null);
    setPicks({});
    setReason('');
    setResult(null);
    setPrintMsg('');
    saleService
      .getReturnInfo(invoiceId)
      .then((data) => alive && setInfo(data))
      .catch((err) => alive && setError(err.response?.data?.message || 'Không tải được hóa đơn'))
      .finally(() => alive && setLoading(false));
    return () => {
      alive = false;
    };
  }, [isOpen, invoiceId]);

  const invoice = info?.invoice;
  const details = invoice?.details || [];
  const estimate = useMemo(() => estimateRefund(invoice, picks), [invoice, picks]);
  const needsReason = info?.approvalRequired || info?.overdue;
  const canSubmit = estimate.lines.length > 0 && (!needsReason || reason.trim()) && !saving;
  const alreadyRefunded = invoice?.status === 'REFUNDED';

  const setQty = (d, v) => {
    const n = Math.max(0, Math.min(remainingQty(d), Number.parseInt(v, 10) || 0));
    setPicks((p) => ({ ...p, [d.id]: n }));
  };
  const pickAll = () => setPicks(Object.fromEntries(details.map((d) => [d.id, remainingQty(d)])));

  const submit = async (approvalToken) => {
    setSaving(true);
    setError('');
    try {
      const items = estimate.lines.map((l) => ({ detailId: l.id, quantity: l.quantity }));
      const res = await saleService.createReturn(invoice.id, { items, reason: reason.trim(), approvalToken });
      setResult(res);
      const printed = await printReturnSlip(returnSlipFrom(res.invoice, res.invoiceReturn, cashierName));
      setPrintMsg(printed?.success ? 'Đã gửi lệnh in phiếu hoàn trả.' : `Chưa in được phiếu: ${printed?.error || 'lỗi máy in'}`);
      onDone?.(res);
    } catch (err) {
      setError(err.response?.data?.message || 'Hoàn trả thất bại');
    } finally {
      setSaving(false);
    }
  };

  const handleConfirm = () => {
    if (!canSubmit) return;
    if (info?.approvalRequired) setApprovalOpen(true);
    else submit(null);
  };

  const reprint = async () => {
    const printed = await printReturnSlip(returnSlipFrom(result.invoice, result.invoiceReturn, cashierName));
    setPrintMsg(printed?.success ? 'Đã gửi lệnh in phiếu hoàn trả.' : `Chưa in được phiếu: ${printed?.error || 'lỗi máy in'}`);
  };

  const code = invoice ? `HD${String(invoice.id).padStart(6, '0')}` : '';

  return (
    <>
      <Modal
        isOpen={isOpen}
        onClose={onClose}
        title={invoice ? `Trả hàng - hóa đơn ${code}` : 'Trả hàng'}
        maxWidth="760px"
        footer={
          result ? (
            <>
              <Button variant="secondary" onClick={reprint}>In lại phiếu</Button>
              <Button variant="primary" onClick={onClose}>Xong</Button>
            </>
          ) : (
            <>
              <Button variant="secondary" onClick={onClose}>Hủy</Button>
              <Button variant="danger" onClick={handleConfirm} disabled={!canSubmit || alreadyRefunded}>
                {saving ? 'Đang hoàn trả...' : `Hoàn ${formatCurrency(estimate.total)}`}
              </Button>
            </>
          )
        }
      >
        {loading && <Loading text="Đang tải hóa đơn..." />}
        {error && (
          <div style={{ color: 'var(--danger-700, #b91c1c)', fontSize: '13px', marginBottom: '10px' }}>{error}</div>
        )}

        {result && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', fontSize: '14px' }}>
            <div>
              Đã hoàn <strong>{formatCurrency(result.invoiceReturn.refundAmount)}</strong> cho khách (
              {PAYMENT_LABELS[result.invoiceReturn.paymentMethod] || result.invoiceReturn.paymentMethod}).
            </div>
            {(result.invoiceReturn.pointsReturned > 0 || result.invoiceReturn.pointsRevoked > 0) && (
              <div style={{ fontSize: "13px", color: "var(--text-muted)" }}>
                Điểm: trả lại {result.invoiceReturn.pointsReturned || 0}, thu hồi {result.invoiceReturn.pointsRevoked || 0}.
              </div>
            )}
            <div>
              Hóa đơn {code}:{' '}
              {result.invoice.status === 'REFUNDED' ? (
                <Badge variant="danger">Đã hoàn trả hết</Badge>
              ) : (
                <Badge variant="warning">Hoàn trả một phần</Badge>
              )}
            </div>
            {printMsg && <div style={{ fontSize: '13px', color: 'var(--text-muted)' }}>{printMsg}</div>}
          </div>
        )}

        {invoice && !result && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '16px', fontSize: '13px' }}>
              <span>Ngày mua: <strong>{formatDateTime(invoice.createdAt)}</strong></span>
              <span>Khách trả: <strong>{formatCurrency(invoice.totalAmount)}</strong></span>
              {Number(invoice.refundedAmount) > 0 && (
                <span>Đã hoàn trước đó: <strong>{formatCurrency(invoice.refundedAmount)}</strong></span>
              )}
              <span>Khách: {invoice.customer?.name || 'Khách lẻ'}</span>
              <span>
                Đổi trả đến hết ngày{' '}
                {new Date(new Date(info.deadline).getTime() - 1).toLocaleDateString('vi-VN')} ({info.returnDays} ngày){' '}
                {info.overdue ? <Badge variant="danger">Quá hạn</Badge> : <Badge variant="success">Còn hạn</Badge>}
              </span>
            </div>

            {alreadyRefunded && (
              <div style={{ color: 'var(--danger-700, #b91c1c)', fontSize: '13px' }}>Hóa đơn này đã được hoàn trả hết.</div>
            )}

            <div style={{ border: '1px solid var(--border-subtle)', borderRadius: '8px', overflow: 'hidden' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '13px' }}>
                <thead>
                  <tr style={{ background: 'var(--bg-muted, #f8fafc)' }}>
                    <th style={{ ...cell, textAlign: 'left' }}>Sản phẩm</th>
                    <th style={{ ...cell, textAlign: 'center', width: 60 }}>Đã mua</th>
                    <th style={{ ...cell, textAlign: 'center', width: 60 }}>Đã trả</th>
                    <th style={{ ...cell, textAlign: 'right', width: 100 }}>Đơn giá</th>
                    <th style={{ ...cell, textAlign: 'center', width: 130 }}>Trả lần này</th>
                    <th style={{ ...cell, textAlign: 'right', width: 110 }}>Tiền hoàn</th>
                  </tr>
                </thead>
                <tbody>
                  {details.map((d) => {
                    const left = remainingQty(d);
                    const line = estimate.lines.find((l) => l.id === d.id);
                    return (
                      <tr key={d.id}>
                        <td style={cell}>{d.product?.name}</td>
                        <td style={{ ...cell, textAlign: 'center' }}>{d.quantity}</td>
                        <td style={{ ...cell, textAlign: 'center' }}>{d.returnedQuantity || 0}</td>
                        <td style={{ ...cell, textAlign: 'right' }}>{formatCurrency(d.unitPrice)}</td>
                        <td style={{ ...cell, textAlign: 'center', whiteSpace: 'nowrap' }}>
                          <input
                            type="number"
                            min={0}
                            max={left}
                            value={picks[d.id] ?? 0}
                            disabled={left === 0}
                            onChange={(e) => setQty(d, e.target.value)}
                            aria-label={`Số lượng trả ${d.product?.name}`}
                            style={{ width: 70, textAlign: 'right', padding: '4px 6px' }}
                          />
                          <span style={{ color: 'var(--text-muted)' }}> / {left}</span>
                        </td>
                        <td style={{ ...cell, textAlign: 'right', fontWeight: 600 }}>
                          {line ? formatCurrency(line.amount) : '—'}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>

            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '12px' }}>
              <Button size="sm" variant="secondary" onClick={pickAll} disabled={alreadyRefunded}>Trả hết</Button>
              <span style={{ fontSize: '13px', color: 'var(--text-muted)' }}>
                Tiền hoàn chia đều khuyến mãi, giảm giá và điểm đã dùng theo giá trị món
              </span>
            </div>

            <div>
              <label htmlFor="return-reason" style={{ display: 'block', fontSize: '12px', color: 'var(--text-muted)', marginBottom: 4 }}>
                Lý do trả hàng {needsReason ? '(bắt buộc)' : ''}
              </label>
              <input
                id="return-reason"
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                placeholder="vd: Hộp móp, hết hạn, khách đổi ý..."
                style={{ width: '100%', padding: '8px 10px', boxSizing: 'border-box' }}
              />
            </div>

            {info.approvalRequired && (
              <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                Thu ngân hoàn trả cần quản lý nhập PIN duyệt ở bước tiếp theo.
              </div>
            )}
            {info.returns?.length > 0 && (
              <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                Đã có {info.returns.length} phiếu hoàn trả cho hóa đơn này:{' '}
                {info.returns
                  .map((r) => `${formatDateTime(r.createdAt)} - ${formatCurrency(r.refundAmount)}${r.createdByName ? ` (${r.createdByName})` : ''}`)
                  .join('; ')}
              </div>
            )}
          </div>
        )}
      </Modal>

      <ManagerApprovalModal
        isOpen={approvalOpen}
        onClose={() => setApprovalOpen(false)}
        purpose="refund"
        amount={estimate.total}
        reason={reason.trim()}
        onApproved={(grant) => {
          setApprovalOpen(false);
          submit(grant.token);
        }}
      />
    </>
  );
}
