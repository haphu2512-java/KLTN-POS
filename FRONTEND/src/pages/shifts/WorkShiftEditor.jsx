import { useEffect, useState } from 'react';
import { Button, Badge } from '../../components/common';
import { shiftService } from '../../services/shiftService';

const toTime = (t) => (t ? String(t).slice(0, 5) : '');

/** Quản trị sửa giờ các ca cố định trong ngày, thêm ca mới hoặc ngưng một ca */
export default function WorkShiftEditor() {
  const [rows, setRows] = useState([]);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [draft, setDraft] = useState({ name: '', startTime: '', endTime: '' });

  const load = () =>
    shiftService
      .getWorkShifts()
      .then((list) => setRows(list.map((ws) => ({ ...ws, startTime: toTime(ws.startTime), endTime: toTime(ws.endTime) }))))
      .catch((err) => setError(err.response?.data?.message || 'Không tải được lịch ca.'));

  useEffect(() => {
    load();
  }, []);

  const change = (id, field, value) =>
    setRows((prev) => prev.map((r) => (r.id === id ? { ...r, [field]: value, dirty: true } : r)));

  const save = async (row) => {
    setError('');
    setMessage('');
    try {
      await shiftService.updateWorkShift(row.id, row);
      setMessage(`Đã lưu ${row.name}.`);
      load();
    } catch (err) {
      setError(err.response?.data?.message || 'Không lưu được ca.');
    }
  };

  const add = async (e) => {
    e.preventDefault();
    setError('');
    setMessage('');
    try {
      await shiftService.createWorkShift(draft);
      setDraft({ name: '', startTime: '', endTime: '' });
      setMessage('Đã thêm ca mới.');
      load();
    } catch (err) {
      setError(err.response?.data?.message || 'Không thêm được ca.');
    }
  };

  return (
    <div className="report-card">
      <div className="report-card-title">
        <span>Lịch ca cố định trong ngày</span>
        <span className="shift-muted">Giờ kết thúc nhỏ hơn giờ bắt đầu là ca qua đêm</span>
      </div>
      {error && <div className="shift-error">{error}</div>}
      {message && <div className="shift-success">{message}</div>}
      <table className="shift-schedule">
        <thead>
          <tr>
            <th>Tên ca</th>
            <th>Bắt đầu</th>
            <th>Kết thúc</th>
            <th>Trạng thái</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.id}>
              <td>
                <input className="shift-input" value={r.name} maxLength={50} onChange={(e) => change(r.id, 'name', e.target.value)} />
              </td>
              <td>
                <input className="shift-input" type="time" value={r.startTime} onChange={(e) => change(r.id, 'startTime', e.target.value)} />
              </td>
              <td>
                <input className="shift-input" type="time" value={r.endTime} onChange={(e) => change(r.id, 'endTime', e.target.value)} />
              </td>
              <td>
                <label className="shift-check">
                  <input type="checkbox" checked={!!r.active} onChange={(e) => change(r.id, 'active', e.target.checked)} />
                  {r.active ? <Badge variant="success">Đang dùng</Badge> : <Badge variant="neutral">Ngưng</Badge>}
                </label>
              </td>
              <td>
                <Button size="sm" variant={r.dirty ? 'primary' : 'secondary'} disabled={!r.dirty} onClick={() => save(r)}>
                  Lưu
                </Button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      <form className="shift-add-row" onSubmit={add}>
        <input
          className="shift-input"
          placeholder="Tên ca mới (vd: Ca đêm)"
          value={draft.name}
          maxLength={50}
          required
          onChange={(e) => setDraft({ ...draft, name: e.target.value })}
        />
        <input className="shift-input" type="time" required value={draft.startTime} onChange={(e) => setDraft({ ...draft, startTime: e.target.value })} />
        <input className="shift-input" type="time" required value={draft.endTime} onChange={(e) => setDraft({ ...draft, endTime: e.target.value })} />
        <Button type="submit" variant="secondary" size="sm">
          Thêm ca
        </Button>
      </form>
    </div>
  );
}
