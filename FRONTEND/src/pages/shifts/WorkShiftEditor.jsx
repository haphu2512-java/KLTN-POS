import { useCallback, useEffect, useState } from 'react';
import { Button, Badge } from '../../components/common';
import { shiftService } from '../../services/shiftService';

const toTime = (t) => (t ? String(t).slice(0, 5) : '');

/**
 * Quản trị tự cấu hình lịch ca trong ngày: số ca và giờ ca của lịch mẫu chung, hoặc lịch riêng của từng
 * chi nhánh (vd. chi nhánh này 2 ca/ngày, chi nhánh kia 4 ca/ngày). Chi nhánh chưa có lịch riêng dùng lịch mẫu.
 */
export default function WorkShiftEditor({ branches = [] }) {
  const [branchId, setBranchId] = useState('');
  const [rows, setRows] = useState([]);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [draft, setDraft] = useState({ name: '', startTime: '', endTime: '' });

  const load = useCallback(
    () =>
      shiftService
        .getWorkShifts(branchId ? { branchId } : { template: true })
        .then((list) =>
          setRows(
            list.map((ws) => ({
              ...ws,
              startTime: toTime(ws.startTime),
              endTime: toTime(ws.endTime),
            })),
          ),
        )
        .catch((err) => setError(err.response?.data?.message || 'Không tải được lịch ca.')),
    [branchId],
  );

  useEffect(() => {
    load();
  }, [load]);

  // Chi nhánh được chọn nhưng lịch trả về là lịch mẫu: chi nhánh chưa có lịch ca riêng
  const usingTemplate = !!branchId && rows.every((r) => r.branchId == null);
  const editable = !usingTemplate;
  const branchName = branches.find((b) => String(b.id) === String(branchId))?.name;

  const reset = () => {
    setError('');
    setMessage('');
  };

  const customize = async () => {
    reset();
    try {
      await shiftService.customizeBranchShifts(Number(branchId));
      setMessage(`Đã tạo lịch ca riêng cho ${branchName}. Thêm, xoá hoặc sửa giờ ca bên dưới.`);
      load();
    } catch (err) {
      setError(err.response?.data?.message || 'Không tạo được lịch ca riêng.');
    }
  };

  const remove = async (row) => {
    if (!window.confirm(`Xoá ${row.name}?`)) return;
    reset();
    try {
      await shiftService.deleteWorkShift(row.id);
      setMessage(`Đã xoá ${row.name}.`);
      load();
    } catch (err) {
      setError(err.response?.data?.message || 'Không xoá được ca.');
    }
  };

  const change = (id, field, value) => setRows((prev) => prev.map((r) => (r.id === id ? { ...r, [field]: value, dirty: true } : r)));

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
      await shiftService.createWorkShift({
        ...draft,
        branchId: branchId ? Number(branchId) : null,
      });
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
        <span>Lịch ca trong ngày ({rows.length} ca)</span>
        <span className="shift-muted">Tự đặt số ca và giờ ca. Giờ kết thúc nhỏ hơn giờ bắt đầu là ca qua đêm</span>
      </div>
      <select className="report-select" value={branchId} onChange={(e) => setBranchId(e.target.value)} style={{ marginBottom: 10 }}>
        <option value="">Lịch mẫu chung (cho chi nhánh chưa có lịch riêng)</option>
        {branches.map((b) => (
          <option key={b.id} value={b.id}>
            {b.name}
          </option>
        ))}
      </select>
      {usingTemplate && (
        <div className="shift-banner">
          <span>{branchName} đang dùng lịch mẫu chung. Tạo lịch riêng để chi nhánh này có số ca và giờ ca khác.</span>
          <Button size="sm" onClick={customize}>
            Tạo lịch ca riêng
          </Button>
        </div>
      )}
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
                <input
                  className="shift-input"
                  value={r.name}
                  maxLength={50}
                  disabled={!editable}
                  onChange={(e) => change(r.id, 'name', e.target.value)}
                />
              </td>
              <td>
                <input
                  className="shift-input"
                  type="time"
                  value={r.startTime}
                  disabled={!editable}
                  onChange={(e) => change(r.id, 'startTime', e.target.value)}
                />
              </td>
              <td>
                <input
                  className="shift-input"
                  type="time"
                  value={r.endTime}
                  disabled={!editable}
                  onChange={(e) => change(r.id, 'endTime', e.target.value)}
                />
              </td>
              <td>
                <label className="shift-check">
                  <input
                    type="checkbox"
                    checked={!!r.active}
                    disabled={!editable}
                    onChange={(e) => change(r.id, 'active', e.target.checked)}
                  />
                  {r.active ? <Badge variant="success">Đang dùng</Badge> : <Badge variant="neutral">Ngưng</Badge>}
                </label>
              </td>
              <td>
                {editable && (
                  <div className="shift-row-actions">
                    <Button size="sm" variant={r.dirty ? 'primary' : 'secondary'} disabled={!r.dirty} onClick={() => save(r)}>
                      Lưu
                    </Button>
                    <Button size="sm" variant="secondary" onClick={() => remove(r)}>
                      Xoá
                    </Button>
                  </div>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      {editable && (
        <form className="shift-add-row" onSubmit={add}>
          <input
            className="shift-input"
            placeholder="Tên ca mới (vd: Ca đêm)"
            value={draft.name}
            maxLength={50}
            required
            onChange={(e) => setDraft({ ...draft, name: e.target.value })}
          />
          <input
            className="shift-input"
            type="time"
            required
            value={draft.startTime}
            onChange={(e) => setDraft({ ...draft, startTime: e.target.value })}
          />
          <input
            className="shift-input"
            type="time"
            required
            value={draft.endTime}
            onChange={(e) => setDraft({ ...draft, endTime: e.target.value })}
          />
          <Button type="submit" variant="secondary" size="sm">
            Thêm ca
          </Button>
        </form>
      )}
    </div>
  );
}
