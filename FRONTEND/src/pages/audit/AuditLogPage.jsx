import { useEffect, useState } from 'react';
import { Button, Table, Badge } from '../../components/common';
import { auditService } from '../../services/auditService';
import { formatDateTime } from '../../utils/formatters';
import useBranches, { branchNameOf } from '../../hooks/useBranches';
import '../../components/layout/ManagePage.css';

const PAGE_SIZE = 50;
// Ngày theo giờ máy (không dùng toISOString vì đổi sang UTC, sáng sớm sẽ lùi một ngày)
const isoDate = (d) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

// Thao tác cần chú ý (mất tiền, mất hàng, dò mật khẩu) tô màu cảnh báo
const WARN = new Set([
  'LOGIN_FAILED', 'LOGIN_LOCKED', 'DISCOUNT_PIN_FAILED', 'SHIFT_CASH_VARIANCE', 'REFUND', 'STOCK_ADJUSTMENT',
  'PRICE_CHANGE', 'USER_UPDATE', 'USER_STATUS', 'PASSWORD_RESET',
]);

/** Nhật ký thao tác: ai làm gì, lúc nào, từ máy nào. ADMIN xem mọi chi nhánh, MANAGER xem chi nhánh mình. */
export default function AuditLogPage({ currentUser, branchId }) {
  const isAdmin = currentUser?.role === 'ADMIN';
  const branches = useBranches(branchId ? [{ id: branchId, name: `Chi nhánh ${branchId}` }] : []);
  const today = new Date();
  const [filters, setFilters] = useState({
    from: isoDate(new Date(today.getTime() - 7 * 86400000)),
    to: isoDate(today),
    action: '',
    username: '',
    branchId: '',
  });
  const [actions, setActions] = useState([]);
  const [page, setPage] = useState(0);
  const [data, setData] = useState({ items: [], total: 0 });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    auditService.actions().then(setActions).catch(() => setActions([]));
  }, []);

  const load = async (p = page, f = filters) => {
    setLoading(true);
    setError('');
    try {
      setData(await auditService.search({ ...f, page: p, size: PAGE_SIZE }));
    } catch (err) {
      setError(err?.response?.data?.message || 'Không tải được nhật ký');
      setData({ items: [], total: 0 });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    // Tải lần đầu; các lần sau tải khi bấm Lọc hoặc đổi trang
    // eslint-disable-next-line react-hooks/set-state-in-effect
    load(0);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const setF = (k) => (e) => setFilters((f) => ({ ...f, [k]: e.target.value }));
  const apply = (e) => {
    e?.preventDefault();
    setPage(0);
    load(0);
  };
  const go = (p) => {
    setPage(p);
    load(p);
  };
  const totalPages = Math.max(1, Math.ceil((data.total || 0) / PAGE_SIZE));

  const columns = [
    { key: 'createdAt', title: 'Thời gian', width: 150, render: (v) => formatDateTime(new Date(v)) },
    {
      key: 'actionLabel',
      title: 'Thao tác',
      width: 210,
      render: (v, row) => <Badge variant={WARN.has(row.action) ? 'warning' : 'neutral'}>{v}</Badge>,
    },
    { key: 'summary', title: 'Nội dung' },
    {
      key: 'actorUsername',
      title: 'Người thao tác',
      width: 150,
      render: (v, row) => (
        <div>
          <div>{v || '—'}</div>
          {row.actorRole && <div className="mp-muted">{row.actorRole}</div>}
        </div>
      ),
    },
    {
      key: 'branchId',
      title: 'Chi nhánh',
      width: 140,
      render: (v) => (v ? branchNameOf(branches, v) : <span className="mp-muted">—</span>),
    },
    { key: 'ipAddress', title: 'Máy (IP)', width: 120, render: (v) => <span className="mp-muted">{v || '—'}</span> },
  ];

  return (
    <div className="mp-page">
      <header className="page-header">
        <h1 className="page-title">Nhật ký thao tác</h1>
        <span className="page-subtitle">
          Ghi lại đổi giá, giảm giá, hoàn trả, mở/đóng ca, chênh lệch két, nhập/điều chỉnh kho, phân quyền và đăng nhập sai
        </span>
      </header>

      <div className="mp-content">
        <form className="mp-toolbar" onSubmit={apply}>
          <div className="mp-toolbar-left">
            <label className="mp-muted" htmlFor="audit-from">Từ</label>
            <input id="audit-from" type="date" className="mp-input" value={filters.from} onChange={setF('from')} />
            <label className="mp-muted" htmlFor="audit-to">đến</label>
            <input id="audit-to" type="date" className="mp-input" value={filters.to} onChange={setF('to')} />
            <select className="mp-select" value={filters.action} onChange={setF('action')} aria-label="Loại thao tác">
              <option value="">Mọi thao tác</option>
              {actions.map((a) => (
                <option key={a.value} value={a.value}>{a.label}</option>
              ))}
            </select>
            <input
              className="mp-input"
              placeholder="Tài khoản..."
              value={filters.username}
              onChange={setF('username')}
              aria-label="Tài khoản"
            />
            {isAdmin && (
              <select className="mp-select" value={filters.branchId} onChange={setF('branchId')} aria-label="Chi nhánh">
                <option value="">Mọi chi nhánh</option>
                {branches.map((b) => (
                  <option key={b.id} value={b.id}>{b.name}</option>
                ))}
              </select>
            )}
          </div>
          <Button type="submit" variant="primary">Lọc</Button>
        </form>

        {error && <div className="mp-card mp-card-pad mp-diff-neg">{error}</div>}

        <div className="mp-card">
          <Table columns={columns} data={data.items || []} loading={loading} emptyText="Không có thao tác nào trong khoảng này" />
          <div className="mp-pager">
            <span className="mp-muted">{data.total || 0} dòng</span>
            <div className="mp-actions">
              <Button size="sm" variant="secondary" disabled={page <= 0 || loading} onClick={() => go(page - 1)}>
                Trang trước
              </Button>
              <span className="mp-muted" style={{ alignSelf: 'center' }}>
                {page + 1} / {totalPages}
              </span>
              <Button size="sm" variant="secondary" disabled={page + 1 >= totalPages || loading} onClick={() => go(page + 1)}>
                Trang sau
              </Button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
