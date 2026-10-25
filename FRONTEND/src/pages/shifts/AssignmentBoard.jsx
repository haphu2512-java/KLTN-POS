import { useCallback, useEffect, useMemo, useState } from 'react';
import { Button } from '../../components/common';
import { shiftService } from '../../services/shiftService';
import { staffService } from '../../services/staffService';
import { isoDate } from './shiftUtils';

const DAY_NAMES = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'];
const toTime = (t) => (t ? String(t).slice(0, 5) : '');

const mondayOf = (d) => {
  const x = new Date(d);
  const day = x.getDay();
  x.setDate(x.getDate() - (day === 0 ? 6 : day - 1));
  return x;
};

/**
 * Lịch xếp ca theo tuần: hàng là ca cố định, cột là ngày. Quản lý chọn nhân viên cho từng ô;
 * khi quầy mở ca đó, ca thuộc về người được xếp. Vai trò khác chỉ xem.
 */
export default function AssignmentBoard({ canEdit, isAdmin, branches = [] }) {
  const [weekStart, setWeekStart] = useState(() => mondayOf(new Date()));
  const [branchId, setBranchId] = useState('');
  const [workShifts, setWorkShifts] = useState([]);
  const [staff, setStaff] = useState([]);
  const [slots, setSlots] = useState([]);
  const [error, setError] = useState('');

  const days = useMemo(
    () => Array.from({ length: 7 }, (_, i) => {
      const d = new Date(weekStart);
      d.setDate(d.getDate() + i);
      return isoDate(d);
    }),
    [weekStart]
  );
  const today = isoDate(new Date());
  const needBranch = isAdmin && !branchId;

  useEffect(() => {
    shiftService.getWorkShifts().then((l) => setWorkShifts(l.filter((w) => w.active))).catch(() => {});
  }, []);

  useEffect(() => {
    if (needBranch) return;
    staffService.list(isAdmin ? branchId : undefined).then((l) => setStaff(l.filter((s) => s.active))).catch(() => setStaff([]));
  }, [branchId, isAdmin, needBranch]);

  const load = useCallback(() => {
    if (needBranch) return;
    shiftService
      .getAssignments({ from: days[0], to: days[6], branchId: isAdmin ? branchId : undefined })
      .then((list) => {
        setSlots(list);
        setError('');
      })
      .catch((err) => setError(err.response?.data?.message || 'Không tải được lịch xếp ca.'));
  }, [needBranch, days, isAdmin, branchId]);

  useEffect(load, [load]);

  const slotOf = (wsId, day) => slots.find((s) => s.workShiftId === wsId && s.shiftDate === day);

  const assign = async (wsId, day, staffMemberId) => {
    try {
      await shiftService.assign({ workShiftId: wsId, shiftDate: day, staffMemberId: staffMemberId ? Number(staffMemberId) : null,
        branchId: isAdmin ? branchId : undefined });
      load();
    } catch (err) {
      setError(err.response?.data?.message || 'Không xếp được ca.');
    }
  };

  const shiftWeek = (n) => {
    const d = new Date(weekStart);
    d.setDate(d.getDate() + 7 * n);
    setWeekStart(d);
  };

  return (
    <div className="report-card">
      <div className="report-card-title">
        <span>Xếp ca tuần {days[0].split('-').reverse().join('/')} – {days[6].split('-').reverse().join('/')}</span>
        <span className="shift-muted">Ca mở ra sẽ thuộc về người được xếp; quản lý mở trực thay phải ghi lý do</span>
      </div>
      <div className="shift-week-nav">
        <Button size="sm" variant="secondary" onClick={() => shiftWeek(-1)}>‹ Tuần trước</Button>
        <Button size="sm" variant="secondary" onClick={() => setWeekStart(mondayOf(new Date()))}>Tuần này</Button>
        <Button size="sm" variant="secondary" onClick={() => shiftWeek(1)}>Tuần sau ›</Button>
        {isAdmin && (
          <select className="report-select" value={branchId} onChange={(e) => setBranchId(e.target.value)}>
            <option value="">Chọn chi nhánh</option>
            {branches.map((b) => (
              <option key={b.id} value={b.id}>{b.name}</option>
            ))}
          </select>
        )}
      </div>
      {error && <div className="shift-error">{error}</div>}
      {needBranch ? (
        <div className="shift-muted">Chọn chi nhánh để xem và xếp ca.</div>
      ) : (
        <table className="shift-week">
          <thead>
            <tr>
              <th>Ca</th>
              {days.map((d, i) => (
                <th key={d} className={d === today ? 'is-today' : undefined}>
                  {DAY_NAMES[i]} {d.slice(8)}/{d.slice(5, 7)}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {workShifts.map((ws) => (
              <tr key={ws.id}>
                <th>
                  {ws.name}
                  <div className="shift-muted">{toTime(ws.startTime)} - {toTime(ws.endTime)}</div>
                </th>
                {days.map((d) => {
                  const slot = slotOf(ws.id, d);
                  return (
                    <td key={d}>
                      {canEdit && d >= today ? (
                        <select
                          className="shift-input"
                          value={slot?.staffMemberId || ''}
                          onChange={(e) => assign(ws.id, d, e.target.value)}
                        >
                          <option value="">—</option>
                          {staff.map((s) => (
                            <option key={s.id} value={s.id}>{s.fullName}</option>
                          ))}
                        </select>
                      ) : (
                        slot?.staffName || <span className="shift-muted">—</span>
                      )}
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
