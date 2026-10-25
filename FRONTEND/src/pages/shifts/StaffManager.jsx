import { useCallback, useEffect, useState } from 'react';
import { Button, Badge } from '../../components/common';
import { staffService } from '../../services/staffService';

const EMPTY = { fullName: '', phone: '', position: '' };

/**
 * Danh sách nhân viên trực ca (không phải tài khoản đăng nhập). Quản lý thêm, sửa, ngưng nhân viên ở chi nhánh
 * đang đăng nhập; quản trị chọn chi nhánh.
 */
export default function StaffManager({ isAdmin, branches = [] }) {
  const [branchId, setBranchId] = useState('');
  const [rows, setRows] = useState([]);
  const [draft, setDraft] = useState(EMPTY);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  const load = useCallback(
    () =>
      staffService
        .list(isAdmin ? branchId : undefined)
        .then((list) => setRows(list.map((s) => ({ ...s, phone: s.phone || '', position: s.position || '' }))))
        .catch((err) => setError(err.response?.data?.message || 'Không tải được nhân viên.')),
    [isAdmin, branchId]
  );

  useEffect(() => {
    load();
  }, [load]);

  const change = (id, field, value) =>
    setRows((prev) => prev.map((r) => (r.id === id ? { ...r, [field]: value, dirty: true } : r)));

  const save = async (row, extra = {}) => {
    setError('');
    setMessage('');
    try {
      await staffService.update(row.id, { ...row, ...extra });
      setMessage(`Đã lưu ${row.fullName}.`);
      load();
    } catch (err) {
      setError(err.response?.data?.message || 'Không lưu được nhân viên.');
    }
  };

  const add = async (e) => {
    e.preventDefault();
    setError('');
    setMessage('');
    if (isAdmin && !branchId) return setError('Chọn chi nhánh trước khi thêm nhân viên.');
    try {
      await staffService.create({ ...draft, branchId: isAdmin ? Number(branchId) : undefined });
      setDraft(EMPTY);
      setMessage('Đã thêm nhân viên.');
      load();
    } catch (err) {
      setError(err.response?.data?.message || 'Không thêm được nhân viên.');
    }
  };

  const branchName = (id) => branches.find((b) => b.id === id)?.name || `#${id}`;

  return (
    <div className="report-card">
      <div className="report-card-title">
        <span>Nhân viên trực ca ({rows.length})</span>
        <span className="shift-muted">Không cần tài khoản: quầy dùng tài khoản chung, ca thuộc về người được xếp</span>
      </div>
      {isAdmin && (
        <select className="report-select" value={branchId} onChange={(e) => setBranchId(e.target.value)} style={{ marginBottom: 10 }}>
          <option value="">Tất cả chi nhánh</option>
          {branches.map((b) => (
            <option key={b.id} value={b.id}>{b.name}</option>
          ))}
        </select>
      )}
      {error && <div className="shift-error">{error}</div>}
      {message && <div className="shift-success">{message}</div>}
      <table className="shift-schedule">
        <thead>
          <tr>
            <th>Họ tên</th>
            <th>Số điện thoại</th>
            <th>Vị trí</th>
            {isAdmin && <th>Chi nhánh</th>}
            <th>Trạng thái</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.id}>
              <td><input className="shift-input" value={r.fullName} maxLength={100} onChange={(e) => change(r.id, 'fullName', e.target.value)} /></td>
              <td><input className="shift-input" value={r.phone} maxLength={20} onChange={(e) => change(r.id, 'phone', e.target.value)} /></td>
              <td><input className="shift-input" value={r.position} maxLength={50} onChange={(e) => change(r.id, 'position', e.target.value)} /></td>
              {isAdmin && <td>{branchName(r.branchId)}</td>}
              <td>{r.active ? <Badge variant="success">Đang làm</Badge> : <Badge variant="neutral">Đã nghỉ</Badge>}</td>
              <td style={{ display: 'flex', gap: 6 }}>
                <Button size="sm" variant="primary" disabled={!r.dirty} onClick={() => save(r)}>Lưu</Button>
                <Button size="sm" variant="secondary" onClick={() => save(r, { active: !r.active })}>
                  {r.active ? 'Cho nghỉ' : 'Đi làm lại'}
                </Button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <form className="shift-add-row" onSubmit={add}>
        <input className="shift-input" placeholder="Họ tên" required maxLength={100} value={draft.fullName}
          onChange={(e) => setDraft({ ...draft, fullName: e.target.value })} />
        <input className="shift-input" placeholder="Số điện thoại" maxLength={20} value={draft.phone}
          onChange={(e) => setDraft({ ...draft, phone: e.target.value })} />
        <input className="shift-input" placeholder="Vị trí (vd. Thu ngân)" maxLength={50} value={draft.position}
          onChange={(e) => setDraft({ ...draft, position: e.target.value })} />
        <Button type="submit" variant="primary" size="sm">Thêm nhân viên</Button>
      </form>
    </div>
  );
}
