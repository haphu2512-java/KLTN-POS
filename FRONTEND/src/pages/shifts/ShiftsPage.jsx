import { useEffect, useMemo, useState } from 'react';
import { Button, Table, Badge } from '../../components/common';
import { formatCurrency, formatDateTime } from '../../utils/formatters';
import { shiftService } from '../../services/shiftService';
import { staffService } from '../../services/staffService';
import { exportToCsv } from '../../utils/exportCsv';
import useBranches from '../../hooks/useBranches';
import ShiftDetailModal from './ShiftDetailModal';
import WorkShiftEditor from './WorkShiftEditor';
import StaffManager from './StaffManager';
import AssignmentBoard from './AssignmentBoard';
import DifferenceCell from './DifferenceCell';
import { formatDay, isoDate } from './shiftUtils';
import '../report/ReportPage.css';
import './ShiftsPage.css';

const PRESETS = [
  { label: 'Hôm nay', days: 1 },
  { label: '7 ngày', days: 7 },
  { label: '30 ngày', days: 30 }
];

const daysAgo = (n) => {
  const d = new Date();
  d.setDate(d.getDate() - (n - 1));
  return isoDate(d);
};

const STATUS_BADGE = {
  OPEN: <Badge variant="brand">Đang mở</Badge>,
  PENDING_REVIEW: <Badge variant="warning">Chờ duyệt</Badge>,
  CLOSED: <Badge variant="success">Đã khóa sổ</Badge>
};

/**
 * Tab "Ca làm".
 * - Quản trị: xem ca mọi chi nhánh, lọc theo chi nhánh / ngày / nhân viên / trạng thái, xem tổng thiếu/dư,
 *   duyệt ca lệch, đóng hộ ca quên đóng, xếp ca, quản lý nhân viên trực và sửa lịch ca cố định.
 * - Quản lý: như trên nhưng chỉ chi nhánh đang đăng nhập (máy chủ tự giới hạn), không sửa ca cố định.
 * - Tài khoản quầy và vai trò khác: xem ca và lịch xếp ca của chi nhánh mình.
 */
export default function ShiftsPage({ currentUser, isOnline = true }) {
  const role = currentUser?.role;
  const isAdmin = role === 'ADMIN';
  const isManager = role === 'MANAGER';
  const canManage = isAdmin || isManager;

  const branches = useBranches();
  const [tab, setTab] = useState('history'); // 'history' | 'assign' | 'staff' | 'schedule'
  const [from, setFrom] = useState(daysAgo(7));
  const [to, setTo] = useState(isoDate(new Date()));
  const [branchFilter, setBranchFilter] = useState('');
  const [employeeFilter, setEmployeeFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [onlyMismatched, setOnlyMismatched] = useState(false);
  const [employees, setEmployees] = useState([]);
  const [summary, setSummary] = useState(null);
  const [error, setError] = useState('');
  const [detailId, setDetailId] = useState(null);
  // Tăng lên để tải lại danh sách (ví dụ sau khi đóng hộ một ca)
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let alive = true;
    shiftService
      .listShifts({
        branchId: isAdmin ? branchFilter : undefined,
        staffId: canManage ? employeeFilter : undefined,
        status: statusFilter,
        from,
        to
      })
      .then((data) => {
        if (!alive) return;
        setSummary(data);
        setError('');
      })
      .catch((err) => alive && setError(err.response?.data?.message || 'Không tải được lịch sử ca.'));
    return () => {
      alive = false;
    };
  }, [isAdmin, canManage, branchFilter, employeeFilter, statusFilter, from, to, isOnline, reloadKey]);

  // Danh sách nhân viên trực để lọc
  useEffect(() => {
    if (!canManage) return;
    staffService
      .list(isAdmin ? branchFilter : undefined)
      .then((list) => setEmployees(list))
      .catch(() => setEmployees([]));
  }, [canManage, isAdmin, branchFilter]);

  const shifts = useMemo(() => {
    const list = summary?.shifts || [];
    return onlyMismatched ? list.filter((s) => s.cashDifference != null && Number(s.cashDifference) !== 0) : list;
  }, [summary, onlyMismatched]);

  const applyPreset = (days) => {
    setFrom(daysAgo(days));
    setTo(isoDate(new Date()));
  };

  const handleExport = () => {
    exportToCsv(
      'ca-lam',
      ['Ngày', 'Chi nhánh', 'Ca', 'Nhân viên', 'Mở lúc', 'Đóng lúc', 'Tiền đầu ca', 'Bán tiền mặt', 'Hoàn trả tiền mặt',
        'Thẻ/CK', 'Số hóa đơn', 'Tiền mặt dự kiến', 'Tiền đếm được', 'Chênh lệch', 'Ghi chú bàn giao', 'Trạng thái'],
      shifts.map((s) => [
        s.shiftDate, s.branchName, s.workShiftName, s.employeeName, formatDateTime(s.openedAt),
        s.closedAt ? formatDateTime(s.closedAt) : '', s.openingCash, s.cashSales, s.cashRefunds ?? '', s.nonCashSales,
        s.invoiceCount, s.expectedCash, s.closingCash ?? '', s.cashDifference ?? '', s.handoverNote || '',
        s.status === 'OPEN' ? 'Đang mở' : s.status === 'PENDING_REVIEW' ? 'Chờ duyệt'
          : s.closedByOther ? `Đóng hộ bởi ${s.closedByName || ''}` : 'Đã khóa sổ'
      ])
    );
  };

  const columns = [
    { key: 'shiftDate', title: 'Ngày', render: (val) => formatDay(val) },
    ...(canManage
      ? [{ key: 'branchName', title: 'Chi nhánh', render: (val, s) => val || `#${s.branchId}` }]
      : []),
    {
      key: 'workShiftName',
      title: 'Ca',
      render: (val, s) => (
        <div>
          <div>{val}</div>
          <div className="shift-muted">{s.workShiftTime}</div>
        </div>
      )
    },
    { key: 'employeeName', title: 'Nhân viên trực' },
    {
      key: 'openedAt',
      title: 'Mở / đóng',
      render: (val, s) => (
        <div className="shift-small">
          <div>{formatDateTime(val)}</div>
          <div className="shift-muted">
            {s.closedAt ? formatDateTime(s.closedAt) : 'Chưa đóng'}
            {s.closedByOther ? ' (đóng hộ)' : ''}
          </div>
        </div>
      )
    },
    { key: 'openingCash', title: 'Đầu ca', align: 'right', render: (val) => formatCurrency(val) },
    {
      key: 'cashSales',
      title: 'Bán tiền mặt',
      align: 'right',
      render: (val, s) => (
        <div>
          <div>{formatCurrency(val)}</div>
          {Number(s.cashRefunds) > 0 && <div className="shift-muted">− {formatCurrency(s.cashRefunds)} hoàn trả</div>}
        </div>
      )
    },
    { key: 'expectedCash', title: 'Két dự kiến', align: 'right', render: (val) => formatCurrency(val) },
    { key: 'closingCash', title: 'Đếm được', align: 'right', render: (val) => (val == null ? '—' : formatCurrency(val)) },
    {
      key: 'cashDifference',
      title: 'Chênh lệch',
      align: 'right',
      render: (val, s) => <DifferenceCell value={val} status={s.status} />
    },
    {
      key: 'status',
      title: 'Trạng thái',
      render: (val, s) => (
        <div>
          {STATUS_BADGE[val] || val}
          {s.overdue && <div><Badge variant="danger">Quên đóng ca</Badge></div>}
        </div>
      )
    },
    {
      key: 'actions',
      title: '',
      align: 'center',
      render: (_, s) => (
        <Button variant={s.status !== 'CLOSED' && canManage ? 'primary' : 'secondary'} size="sm" onClick={() => setDetailId(s.id)}>
          {canManage && s.status === 'OPEN' ? 'Xem / đóng hộ' : canManage && s.status === 'PENDING_REVIEW' ? 'Duyệt' : 'Chi tiết'}
        </Button>
      )
    }
  ];

  return (
    <div className="report-container">
      <header className="page-header">
        <h1 className="page-title">Ca làm</h1>
        <span className="page-subtitle">
          {isAdmin
            ? 'Tất cả chi nhánh'
            : isManager
              ? `Ca tại ${currentUser?.branchName || 'chi nhánh đang đăng nhập'}`
              : `Ca tại ${currentUser?.branchName || 'chi nhánh đang đăng nhập'}`}
        </span>
      </header>

      <main className="report-body">
        <div className="shift-tabs">
          {[
            ['history', 'Lịch sử ca & thiếu/dư', true],
            ['assign', 'Xếp ca', true],
            ['staff', 'Nhân viên trực', canManage],
            ['schedule', 'Giờ các ca', isAdmin]
          ]
            .filter(([, , show]) => show)
            .map(([key, label]) => (
              <button
                key={key}
                type="button"
                className={`report-period-btn ${tab === key ? 'active' : ''}`}
                onClick={() => setTab(key)}
              >
                {label}
              </button>
            ))}
        </div>

        {tab === 'schedule' && isAdmin ? (
          <WorkShiftEditor branches={branches} />
        ) : tab === 'assign' ? (
          <AssignmentBoard canEdit={canManage} isAdmin={isAdmin} branches={branches} onAddStaff={() => setTab('staff')} />
        ) : tab === 'staff' && canManage ? (
          <StaffManager isAdmin={isAdmin} branches={branches} />
        ) : (
          <>
            <div className="report-filter-bar shift-filter-bar">
              <div className="report-filter-group shift-filter-wrap">
                <div className="report-btn-group">
                  {PRESETS.map((p) => (
                    <button
                      key={p.days}
                      type="button"
                      className={`report-period-btn ${from === daysAgo(p.days) && to === isoDate(new Date()) ? 'active' : ''}`}
                      onClick={() => applyPreset(p.days)}
                    >
                      {p.label}
                    </button>
                  ))}
                </div>
                <label className="shift-field">
                  Từ
                  <input type="date" className="report-select" value={from} max={to} onChange={(e) => setFrom(e.target.value)} />
                </label>
                <label className="shift-field">
                  Đến
                  <input type="date" className="report-select" value={to} min={from} onChange={(e) => setTo(e.target.value)} />
                </label>
                {isAdmin && (
                  <select className="report-select" value={branchFilter} onChange={(e) => setBranchFilter(e.target.value)}>
                    <option value="">Tất cả chi nhánh</option>
                    {branches.map((b) => (
                      <option key={b.id} value={b.id}>{b.name}</option>
                    ))}
                  </select>
                )}
                {canManage && (
                  <select className="report-select" value={employeeFilter} onChange={(e) => setEmployeeFilter(e.target.value)}>
                    <option value="">Tất cả nhân viên</option>
                    {employees.map((u) => (
                      <option key={u.id} value={u.id}>{u.fullName}</option>
                    ))}
                  </select>
                )}
                <select className="report-select" value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
                  <option value="">Mọi trạng thái</option>
                  <option value="OPEN">Đang mở</option>
                  <option value="PENDING_REVIEW">Chờ duyệt</option>
                  <option value="CLOSED">Đã khóa sổ</option>
                </select>
                <label className="shift-check">
                  <input type="checkbox" checked={onlyMismatched} onChange={(e) => setOnlyMismatched(e.target.checked)} />
                  Chỉ ca lệch tiền
                </label>
              </div>
              <Button variant="secondary" size="sm" onClick={handleExport} disabled={shifts.length === 0}>
                Xuất CSV
              </Button>
            </div>

            {error && <div className="shift-error">{error}</div>}

            <div className="report-kpi-grid">
              <div className="report-kpi-card">
                <div className="report-kpi-title"><span>Số ca</span></div>
                <div className="report-kpi-value">{summary?.totalShifts ?? 0}</div>
                <div className="report-kpi-sub">
                  {summary?.openShifts ?? 0} đang mở · {summary?.pendingReviewShifts ?? 0} chờ duyệt
                </div>
              </div>
              <div className="report-kpi-card">
                <div className="report-kpi-title"><span>Ca lệch tiền</span></div>
                <div className="report-kpi-value">{summary?.mismatchedShifts ?? 0}</div>
                <div className="report-kpi-sub">Tiền đếm được khác tiền dự kiến</div>
              </div>
              <div className="report-kpi-card">
                <div className="report-kpi-title"><span>Tổng thiếu</span></div>
                <div className="report-kpi-value shift-short">{formatCurrency(summary?.totalShortage)}</div>
                <div className="report-kpi-sub">Cộng các ca thiếu tiền</div>
              </div>
              <div className="report-kpi-card">
                <div className="report-kpi-title"><span>Tổng dư</span></div>
                <div className="report-kpi-value shift-over">{formatCurrency(summary?.totalOverage)}</div>
                <div className="report-kpi-sub">Cộng các ca dư tiền</div>
              </div>
            </div>

            <div className="report-card">
              <div className="report-card-title">
                <span>Lịch sử ca ({shifts.length})</span>
                <span className="shift-muted">Bấm “Chi tiết” để xem từng hóa đơn và gợi ý dò chênh lệch</span>
              </div>
              <Table
                columns={columns}
                data={shifts}
                rowKey="id"
                emptyText={summary ? 'Không có ca nào trong khoảng thời gian này' : 'Đang tải...'}
              />
            </div>
          </>
        )}
      </main>

      {detailId && (
        <ShiftDetailModal
          shiftId={detailId}
          canForceClose={canManage}
          onClose={() => setDetailId(null)}
          onChanged={() => setReloadKey((k) => k + 1)}
        />
      )}
    </div>
  );
}
