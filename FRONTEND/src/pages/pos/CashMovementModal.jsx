import { useEffect, useState } from 'react';
import { Modal, Button, Input, Badge } from '../../components/common';
import { formatCurrency, formatDateTime } from '../../utils/formatters';
import { shiftService } from '../../services/shiftService';
import { MOVEMENT_LABELS } from '../shifts/shiftUtils';
import '../shifts/ShiftsPage.css';

const EXPENSE_CATEGORIES = ['Vận chuyển', 'Mua vật tư', 'Ứng lương', 'Điện nước', 'Khác'];
const EMPTY = { type: 'EXPENSE', category: 'Vận chuyển', amount: '', reason: '', counterpart: '' };

/**
 * Thu/chi két trong ca: chi lặt vặt, nộp bớt tiền cho quản lý khi két đầy, nhận thêm tiền lẻ.
 * Các khoản này làm đổi tiền mặt dự kiến lúc kết ca; xóa khi ca còn mở (còn vết trong lịch sử).
 */
export default function CashMovementModal({ isOpen, onClose, session, drawerLimit, onChanged }) {
  const [rows, setRows] = useState([]);
  const [form, setForm] = useState(EMPTY);
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);

  const load = () =>
    shiftService
      .getCurrentCashMovements()
      .then(setRows)
      .catch((err) => setError(err.response?.data?.message || 'Không tải được thu/chi két.'));

  useEffect(() => {
    load();
  }, []);

  const set = (field) => (e) => setForm((prev) => ({ ...prev, [field]: e.target.value }));

  const handleAdd = async () => {
    const amount = Number(form.amount);
    if (!Number.isFinite(amount) || amount <= 0) return setError('Nhập số tiền lớn hơn 0.');
    if (!form.reason.trim()) return setError('Ghi lý do.');
    if (form.type !== 'EXPENSE' && !form.counterpart.trim()) {
      return setError(form.type === 'DROP' ? 'Ghi tên người nhận tiền.' : 'Ghi tên người đưa tiền.');
    }
    setSaving(true);
    setError('');
    try {
      await shiftService.addCashMovement({ ...form, amount });
      setForm({ ...EMPTY, type: form.type });
      await load();
      onChanged?.();
    } catch (err) {
      setError(err.response?.data?.message || 'Không ghi được khoản thu/chi.');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (row) => {
    if (!window.confirm(`Xóa khoản ${MOVEMENT_LABELS[row.type].toLowerCase()} ${formatCurrency(row.amount)}?`)) return;
    try {
      await shiftService.deleteCashMovement(row.id);
      await load();
      onChanged?.();
    } catch (err) {
      setError(err.response?.data?.message || 'Không xóa được.');
    }
  };

  const expected = Number(session?.expectedCash || 0);
  const overLimit = Number(drawerLimit) > 0 && expected > Number(drawerLimit);

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="Thu / chi két"
      maxWidth="620px"
      footer={<Button variant="secondary" onClick={onClose}>Đóng</Button>}
    >
      <div className="shift-form">
        <div className={`shift-banner${overLimit ? ' is-warning' : ''}`}>
          Tiền mặt dự kiến trong két: <strong>{formatCurrency(expected)}</strong>
          {overLimit && ` · vượt hạn mức ${formatCurrency(drawerLimit)}, nên nộp bớt quỹ.`}
        </div>

        <div className="shift-movement-form">
          <label className="shift-field-col">
            <span className="input-label">Loại</span>
            <select className="input-control" value={form.type} onChange={set('type')}>
              {Object.entries(MOVEMENT_LABELS).map(([k, v]) => (
                <option key={k} value={k}>{v}</option>
              ))}
            </select>
          </label>
          {form.type === 'EXPENSE' ? (
            <label className="shift-field-col">
              <span className="input-label">Danh mục</span>
              <select className="input-control" value={form.category} onChange={set('category')}>
                {EXPENSE_CATEGORIES.map((c) => (
                  <option key={c} value={c}>{c}</option>
                ))}
              </select>
            </label>
          ) : (
            <Input
              label={form.type === 'DROP' ? 'Người nhận tiền' : 'Người đưa tiền'}
              value={form.counterpart}
              maxLength={100}
              onChange={set('counterpart')}
            />
          )}
          <Input label="Số tiền (VNĐ)" type="number" min="1" value={form.amount} onChange={set('amount')} />
          <Input label="Lý do" value={form.reason} maxLength={255} onChange={set('reason')} />
        </div>
        <div>
          <Button variant="primary" onClick={handleAdd} disabled={saving}>Ghi khoản này</Button>
        </div>
        {error && <div className="shift-error-text">{error}</div>}

        <table className="shift-recon">
          <thead>
            <tr><th>Lúc</th><th>Loại</th><th>Số tiền</th><th>Lý do</th><th /></tr>
          </thead>
          <tbody>
            {rows.length === 0 && (
              <tr><td colSpan={5} className="shift-muted">Chưa có khoản thu/chi nào trong ca</td></tr>
            )}
            {rows.map((r) => (
              <tr key={r.id} style={r.deletedAt ? { opacity: 0.5, textDecoration: 'line-through' } : undefined}>
                <td>{formatDateTime(r.createdAt)}</td>
                <td>
                  <Badge variant={r.type === 'FLOAT_IN' ? 'success' : r.type === 'DROP' ? 'brand' : 'warning'}>
                    {MOVEMENT_LABELS[r.type]}
                  </Badge>
                </td>
                <td>{r.type === 'FLOAT_IN' ? '+' : '−'} {formatCurrency(r.amount)}</td>
                <td>
                  {r.category ? `${r.category}: ` : ''}{r.reason}
                  {r.counterpart && <div className="shift-muted">{r.type === 'DROP' ? 'Người nhận' : 'Người đưa'}: {r.counterpart}</div>}
                </td>
                <td>
                  {!r.deletedAt && (
                    <Button variant="secondary" size="sm" onClick={() => handleDelete(r)}>Xóa</Button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </Modal>
  );
}
