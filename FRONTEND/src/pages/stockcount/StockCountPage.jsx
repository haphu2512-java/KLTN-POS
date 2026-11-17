import { useEffect, useMemo, useRef, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { Button, Badge, Table } from '../../components/common';
import { stockCountService } from '../../services/stockCountService';
import { productService } from '../../services/productService';
import { formatCurrency, formatDateTime } from '../../utils/formatters';
import '../../components/layout/ManagePage.css';

const STATUS = {
  DRAFT: { label: 'Đang đếm', variant: 'brand' },
  SUBMITTED: { label: 'Chờ duyệt', variant: 'warning' },
  APPROVED: { label: 'Đã duyệt', variant: 'success' },
  CANCELLED: { label: 'Đã hủy', variant: 'neutral' },
};

const errMsg = (err, fallback) => err?.response?.data?.message || fallback;

const toLocalDateStr = (dateVal) => {
  if (!dateVal) return '';
  const d = new Date(dateVal);
  if (isNaN(d.getTime())) return '';
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};

const formatDateOnly = (dateStr) => {
  if (!dateStr) return '';
  const parts = dateStr.split('-');
  if (parts.length === 3) {
    return `${parts[2]}/${parts[1]}/${parts[0]}`;
  }
  return dateStr;
};

/**
 * Kiểm kê và điều chỉnh tồn kho: nhân viên kho tạo phiếu, quét mã (máy quét USB gõ mã + Enter) để đếm,
 * chọn lý do cho dòng lệch rồi gửi duyệt; quản lý duyệt thì tồn được cộng/trừ đúng phần chênh lệch.
 */
export default function StockCountPage({ currentUser }) {
  const canApprove = currentUser?.role === 'ADMIN' || currentUser?.role === 'MANAGER';
  const [counts, setCounts] = useState([]);
  const [current, setCurrent] = useState(null);
  const [reasons, setReasons] = useState([]);
  const [products, setProducts] = useState([]);
  const [shrinkage, setShrinkage] = useState(null);
  const [scan, setScan] = useState('');
  const [note, setNote] = useState('');
  const [message, setMessage] = useState(null);
  const [busy, setBusy] = useState(false);
  const scanRef = useRef(null);
  const location = useLocation();
  const openedFromKey = useRef(null);

  // Bộ lọc danh sách phiếu kiểm kê bên trái: mặc định lọc theo ngày hôm nay
  const [datePreset, setDatePreset] = useState('today');
  const [fromDate, setFromDate] = useState(() => toLocalDateStr(new Date()));
  const [toDate, setToDate] = useState(() => toLocalDateStr(new Date()));
  const [searchKeyword, setSearchKeyword] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');

  const handlePresetChange = (preset) => {
    setDatePreset(preset);
    const today = new Date();
    if (preset === 'today') {
      const s = toLocalDateStr(today);
      setFromDate(s);
      setToDate(s);
    } else if (preset === 'yesterday') {
      const y = new Date(today);
      y.setDate(y.getDate() - 1);
      const s = toLocalDateStr(y);
      setFromDate(s);
      setToDate(s);
    } else if (preset === '7days') {
      const p = new Date(today);
      p.setDate(p.getDate() - 6);
      setFromDate(toLocalDateStr(p));
      setToDate(toLocalDateStr(today));
    } else if (preset === '30days') {
      const p = new Date(today);
      p.setDate(p.getDate() - 29);
      setFromDate(toLocalDateStr(p));
      setToDate(toLocalDateStr(today));
    } else if (preset === 'all') {
      setFromDate('');
      setToDate('');
    }
  };

  const resetFilters = () => {
    const today = toLocalDateStr(new Date());
    setDatePreset('today');
    setFromDate(today);
    setToDate(today);
    setSearchKeyword('');
    setStatusFilter('ALL');
  };

  const filteredCounts = useMemo(() => {
    return counts.filter((c) => {
      // 1. Lọc theo ngày tạo
      if (fromDate || toDate) {
        const countDate = toLocalDateStr(c.createdAt);
        if (fromDate && countDate < fromDate) return false;
        if (toDate && countDate > toDate) return false;
      }
      // 2. Lọc theo trạng thái
      if (statusFilter !== 'ALL' && c.status !== statusFilter) {
        return false;
      }
      // 3. Lọc theo từ khóa (mã phiếu #, ghi chú, người tạo, người duyệt)
      if (searchKeyword.trim()) {
        const kw = searchKeyword.trim().toLowerCase();
        const idMatch = String(c.id).includes(kw) || `#${c.id}`.toLowerCase().includes(kw);
        const noteMatch = (c.note || '').toLowerCase().includes(kw);
        const creatorMatch = (c.createdByName || '').toLowerCase().includes(kw);
        const approverMatch = (c.approvedByName || '').toLowerCase().includes(kw);
        if (!idMatch && !noteMatch && !creatorMatch && !approverMatch) return false;
      }
      return true;
    });
  }, [counts, fromDate, toDate, statusFilter, searchKeyword]);

  const todayStr = toLocalDateStr(new Date());
  const isFiltered = datePreset !== 'today'
    || fromDate !== todayStr
    || toDate !== todayStr
    || Boolean(searchKeyword)
    || statusFilter !== 'ALL';

  const loadList = async () => {
    try {
      setCounts(await stockCountService.list());
    } catch (err) {
      setMessage({ error: true, text: errMsg(err, 'Không tải được danh sách phiếu') });
    }
  };
  const loadShrinkage = () => stockCountService.shrinkage().then(setShrinkage).catch(() => setShrinkage(null));

  useEffect(() => {
    // Tải dữ liệu lần đầu
    // eslint-disable-next-line react-hooks/set-state-in-effect
    loadList();
    loadShrinkage();
    // Mở sẵn phiếu vừa lập từ nơi khác (vd. phiếu hủy hàng hết hạn ở trang Kho)
    if (location.state?.openCountId) {
      stockCountService.get(location.state.openCountId).then((sc) => {
        setCurrent(sc);
        const countDate = toLocalDateStr(sc?.createdAt);
        const today = toLocalDateStr(new Date());
        if (countDate && countDate !== today) {
          setDatePreset('all');
          setFromDate('');
          setToDate('');
        }
      }).catch(() => {});
      if (location.state.message) setMessage({ text: location.state.message });
    }
    openedFromKey.current = location.key;
    stockCountService.reasons().then(setReasons).catch(() => {});
    productService.getProducts().then(setProducts).catch(() => {});
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Trang được giữ lại khi chuyển màn hình: lần sau được mở kèm phiếu thì tải lại danh sách và mở phiếu đó
  useEffect(() => {
    if (openedFromKey.current === location.key || !location.state?.openCountId) return;
    openedFromKey.current = location.key;
    loadList();
    stockCountService.get(location.state.openCountId).then((sc) => {
      setCurrent(sc);
      const countDate = toLocalDateStr(sc?.createdAt);
      const today = toLocalDateStr(new Date());
      if (countDate && countDate !== today) {
        setDatePreset('all');
        setFromDate('');
        setToDate('');
      }
    }).catch(() => {});
    // eslint-disable-next-line react-hooks/set-state-in-effect
    if (location.state.message) setMessage({ text: location.state.message });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [location.key]);

  const editable = current?.status === 'DRAFT';
  const run = async (fn, okText) => {
    setBusy(true);
    setMessage(null);
    try {
      const res = await fn();
      if (res?.id) setCurrent(res);
      if (okText) setMessage({ text: okText });
      return res;
    } catch (err) {
      setMessage({ error: true, text: errMsg(err, 'Thao tác thất bại') });
      return null;
    } finally {
      setBusy(false);
    }
  };

  const createCount = async () => {
    const res = await run(() => stockCountService.create(note.trim()), 'Đã tạo phiếu, quét mã để bắt đầu đếm');
    if (res) {
      setNote('');
      resetFilters();
      loadList();
      setTimeout(() => scanRef.current?.focus(), 50);
    }
  };

  const open = (c) => run(() => stockCountService.get(c.id));

  // Mỗi lần quét (hoặc gõ mã / chọn tên rồi Enter) cộng thêm 1
  const handleScan = async (e) => {
    e.preventDefault();
    const term = scan.trim();
    if (!term || !current) return;
    const byName = products.find((p) => p.name.toLowerCase() === term.toLowerCase());
    const line = byName ? { productId: byName.id, add: true, countedQty: 1 } : { barcode: term, add: true, countedQty: 1 };
    const res = await run(() => stockCountService.upsertLine(current.id, line));
    if (res && (current.lines || []).length !== (res.lines || []).length) loadList();
    setScan('');
    scanRef.current?.focus();
  };

  const setLine = (l, patch) =>
    run(() =>
      stockCountService.upsertLine(current.id, {
        productId: l.productId,
        countedQty: patch.countedQty ?? l.countedQty,
        reason: patch.reason ?? l.reason ?? null,
      })
    );

  const act = async (fn, text) => {
    const res = await run(fn, text);
    if (res) {
      loadList();
      loadShrinkage();
    }
  };

  const lines = useMemo(() => current?.lines || [], [current]);
  const totals = useMemo(() => {
    let value = 0;
    let diffLines = 0;
    for (const l of lines) {
      const d = l.countedQty - l.systemQty;
      if (d !== 0) diffLines += 1;
      value += d * (Number(l.unitCost) || 0);
    }
    return { value, diffLines };
  }, [lines]);

  const lineColumns = [
    {
      key: 'productName',
      title: 'Sản phẩm',
      render: (v, l) => (
        <div>
          <div>{v}</div>
          <div className="mp-muted">{l.barcode}</div>
        </div>
      ),
    },
    { key: 'systemQty', title: 'Tồn hệ thống', align: 'right', width: 110 },
    {
      key: 'countedQty',
      title: 'Đếm được',
      align: 'right',
      width: 120,
      render: (v, l) =>
        editable ? (
          <input
            type="number"
            min={0}
            className="mp-input mp-qty"
            defaultValue={v}
            key={`${l.productId}-${v}`}
            aria-label={`Số đếm được ${l.productName}`}
            onBlur={(e) => Number(e.target.value) !== v && setLine(l, { countedQty: Math.max(0, Number(e.target.value) || 0) })}
          />
        ) : (
          v
        ),
    },
    {
      key: 'difference',
      title: 'Chênh lệch',
      align: 'right',
      width: 100,
      render: (_, l) => {
        const d = l.countedQty - l.systemQty;
        return <span className={d < 0 ? 'mp-diff-neg' : d > 0 ? 'mp-diff-pos' : ''}>{d > 0 ? `+${d}` : d}</span>;
      },
    },
    {
      key: 'reason',
      title: 'Lý do',
      width: 190,
      render: (v, l) => {
        const d = l.countedQty - l.systemQty;
        if (d === 0) return <span className="mp-muted">—</span>;
        return editable ? (
          <select
            className="mp-select"
            value={v || ''}
            onChange={(e) => setLine(l, { reason: e.target.value })}
            aria-label={`Lý do chênh lệch ${l.productName}`}
          >
            <option value="">Chọn lý do...</option>
            {reasons.map((r) => (
              <option key={r.value} value={r.value}>{r.label}</option>
            ))}
          </select>
        ) : (
          reasons.find((r) => r.value === v)?.label || v || '—'
        );
      },
    },
    ...(editable
      ? [
          {
            key: 'remove',
            title: '',
            width: 60,
            render: (_, l) => (
              <Button size="sm" variant="ghost" onClick={() => run(() => stockCountService.removeLine(current.id, l.productId))}>
                Xóa
              </Button>
            ),
          },
        ]
      : []),
  ];

  const listColumns = [
    { key: 'id', title: 'Phiếu', width: 64, render: (v) => <strong>#{v}</strong> },
    {
      key: 'createdAt',
      title: 'Ngày tạo',
      render: (v, c) => (
        <div>
          <div className="mp-nowrap">{formatDateTime(v)}</div>
          {c.note && <div className="mp-muted">{c.note}</div>}
        </div>
      ),
    },
    { key: 'lines', title: 'Mặt hàng', align: 'right', width: 90, render: (v) => (v || []).length },
    {
      key: 'status',
      title: 'Trạng thái',
      width: 120,
      render: (v) => (
        <span className="mp-nowrap">
          <Badge variant={STATUS[v]?.variant}>{STATUS[v]?.label || v}</Badge>
        </span>
      ),
    },
  ];

  return (
    <div className="mp-page">
      <header className="page-header">
        <h1 className="page-title">Kiểm kê kho</h1>
        <span className="page-subtitle">
          Đếm hàng thực tế, ghi lý do chênh lệch (hỏng, hết hạn, mất...) và để quản lý duyệt điều chỉnh tồn
        </span>
      </header>

      <div className="mp-content">
        {shrinkage && (
          <div className="mp-card mp-card-pad mp-summary">
            <span>
              Hao hụt tháng này (giá vốn): <strong className="mp-diff-neg">{formatCurrency(shrinkage.totalLoss)}</strong>
            </span>
            <span>
              Dư so với sổ: <strong className="mp-diff-pos">{formatCurrency(shrinkage.totalGain)}</strong>
            </span>
            {(shrinkage.byReason || []).map((r) => (
              <span key={r.reason || 'none'} className="mp-muted">
                {r.label}: {r.quantity > 0 ? `+${r.quantity}` : r.quantity} ({formatCurrency(r.value)})
              </span>
            ))}
          </div>
        )}

        {message && (
          <div className={`mp-card mp-card-pad ${message.error ? 'mp-diff-neg' : ''}`} role="status">
            {message.text}
          </div>
        )}

        <div className="mp-split">
          <div className="mp-card">
            <div className="mp-card-head">
              <strong>Phiếu kiểm kê</strong>
              <span className="mp-muted">
                {filteredCounts.length !== counts.length ? `${filteredCounts.length} / ${counts.length} phiếu` : `${counts.length} phiếu`}
              </span>
            </div>
            <div className="mp-card-pad" style={{ display: 'flex', gap: 8, borderBottom: '1px solid var(--border-subtle)' }}>
              <input
                className="mp-input"
                style={{ flex: 1 }}
                placeholder="Ghi chú (vd. Kiểm kê cuối tháng)"
                value={note}
                onChange={(e) => setNote(e.target.value)}
              />
              <Button variant="primary" onClick={createCount} disabled={busy}>Tạo phiếu</Button>
            </div>

            {/* Khung bộ lọc ngày, tìm kiếm & trạng thái */}
            <div style={{ padding: '12px', borderBottom: '1px solid var(--border-subtle)', display: 'flex', flexDirection: 'column', gap: '8px', background: 'var(--bg-surface)' }}>
              {/* Hàng 1: Bộ lọc Ngày */}
              <div style={{ display: 'flex', gap: '6px', alignItems: 'center', flexWrap: 'wrap' }}>
                <select
                  className="mp-select"
                  value={datePreset}
                  onChange={(e) => handlePresetChange(e.target.value)}
                  style={{ fontSize: '13px', padding: '6px 8px', height: '32px' }}
                  aria-label="Chọn khoảng ngày"
                >
                  <option value="today">Hôm nay</option>
                  <option value="yesterday">Hôm qua</option>
                  <option value="7days">7 ngày qua</option>
                  <option value="30days">30 ngày qua</option>
                  <option value="all">Tất cả ngày</option>
                  <option value="custom">Tùy chọn...</option>
                </select>

                <div style={{ display: 'flex', alignItems: 'center', gap: '4px', flex: 1, minWidth: '200px' }}>
                  <input
                    type="date"
                    className="mp-input"
                    style={{ padding: '4px 6px', fontSize: '12px', flex: 1, height: '32px' }}
                    value={fromDate}
                    max={toDate || undefined}
                    title="Từ ngày"
                    aria-label="Từ ngày"
                    onChange={(e) => {
                      setFromDate(e.target.value);
                      setDatePreset('custom');
                    }}
                  />
                  <span className="mp-muted" style={{ padding: '0 2px' }}>–</span>
                  <input
                    type="date"
                    className="mp-input"
                    style={{ padding: '4px 6px', fontSize: '12px', flex: 1, height: '32px' }}
                    value={toDate}
                    min={fromDate || undefined}
                    title="Đến ngày"
                    aria-label="Đến ngày"
                    onChange={(e) => {
                      setToDate(e.target.value);
                      setDatePreset('custom');
                    }}
                  />
                </div>

                {isFiltered && (
                  <Button
                    size="sm"
                    variant="ghost"
                    style={{ height: '32px', padding: '0 8px', fontSize: '12px', color: 'var(--text-muted)' }}
                    onClick={resetFilters}
                    title="Đặt lại về hôm nay"
                  >
                    ✕ Đặt lại
                  </Button>
                )}
              </div>

              {/* Hàng 2: Tìm kiếm mã phiếu/ghi chú & lọc trạng thái */}
              <div style={{ display: 'flex', gap: '6px', alignItems: 'center' }}>
                <input
                  type="text"
                  className="mp-input"
                  style={{ flex: 1, padding: '5px 8px', fontSize: '12px', height: '32px' }}
                  placeholder="Tìm theo #phiếu, ghi chú, người tạo..."
                  value={searchKeyword}
                  onChange={(e) => setSearchKeyword(e.target.value)}
                  aria-label="Tìm kiếm phiếu kiểm kê"
                />
                <select
                  className="mp-select"
                  style={{ padding: '5px 8px', fontSize: '12px', height: '32px' }}
                  value={statusFilter}
                  onChange={(e) => setStatusFilter(e.target.value)}
                  aria-label="Lọc theo trạng thái"
                >
                  <option value="ALL">Mọi trạng thái</option>
                  <option value="DRAFT">Đang đếm</option>
                  <option value="SUBMITTED">Chờ duyệt</option>
                  <option value="APPROVED">Đã duyệt</option>
                  <option value="CANCELLED">Đã hủy</option>
                </select>
              </div>

              {/* Thông tin số lượng kết quả */}
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: '12px', color: 'var(--text-muted)', paddingTop: '2px' }}>
                <span>Hiển thị <strong>{filteredCounts.length}</strong> / {counts.length} phiếu</span>
                {(fromDate || toDate) && (
                  <span>
                    {fromDate === toDate
                      ? `Ngày ${formatDateOnly(fromDate)}`
                      : `${fromDate ? formatDateOnly(fromDate) : '...'} → ${toDate ? formatDateOnly(toDate) : '...'}`}
                  </span>
                )}
              </div>
            </div>

            <Table
              columns={listColumns}
              data={filteredCounts}
              onRowClick={open}
              selectedKey={current?.id}
              className="mp-flush-table"
              emptyText={counts.length === 0 ? 'Chưa có phiếu kiểm kê' : 'Không tìm thấy phiếu phù hợp bộ lọc'}
              maxHeight="55vh"
            />
          </div>

          <div className="mp-card">
            {!current ? (
              <div className="mp-empty-state">
                <strong>Chưa chọn phiếu kiểm kê</strong>
                <span>Chọn một phiếu trong danh sách bên trái hoặc tạo phiếu mới để bắt đầu đếm hàng.</span>
              </div>
            ) : (
              <>
                <div className="mp-toolbar" style={{ border: 'none', borderRadius: 0 }}>
                  <div className="mp-toolbar-left">
                    <strong>Phiếu #{current.id}</strong>
                    <Badge variant={STATUS[current.status]?.variant}>{STATUS[current.status]?.label}</Badge>
                    {current.note && <span className="mp-muted">{current.note}</span>}
                    <span className="mp-muted">
                      Tạo bởi {current.createdByName || '—'}
                      {current.approvedByName ? ` · duyệt bởi ${current.approvedByName}` : ''}
                    </span>
                  </div>
                  <div className="mp-actions">
                    {editable && (
                      <Button variant="secondary" disabled={busy || lines.length === 0}
                        onClick={() => act(() => stockCountService.submit(current.id), 'Đã gửi quản lý duyệt')}>
                        Gửi duyệt
                      </Button>
                    )}
                    {canApprove && (current.status === 'SUBMITTED' || current.status === 'DRAFT') && (
                      <Button variant="primary" disabled={busy || lines.length === 0}
                        onClick={() => act(() => stockCountService.approve(current.id), 'Đã duyệt, tồn kho đã được điều chỉnh')}>
                        Duyệt điều chỉnh
                      </Button>
                    )}
                    {(current.status === 'DRAFT' || current.status === 'SUBMITTED') && (
                      <Button variant="ghost" disabled={busy}
                        onClick={() => act(() => stockCountService.cancel(current.id), 'Đã hủy phiếu')}>
                        Hủy phiếu
                      </Button>
                    )}
                  </div>
                </div>

                {editable && (
                  <form onSubmit={handleScan} className="mp-card-pad" style={{ display: 'flex', gap: 8, borderTop: '1px solid var(--border-subtle)' }}>
                    <input
                      ref={scanRef}
                      className="mp-input"
                      style={{ flex: 1 }}
                      list="stockcount-products"
                      placeholder="Quét mã vạch hoặc gõ tên sản phẩm rồi Enter (mỗi lần +1)"
                      value={scan}
                      onChange={(e) => setScan(e.target.value)}
                      aria-label="Quét mã vạch"
                    />
                    <datalist id="stockcount-products">
                      {products.slice(0, 2000).map((p) => (
                        <option key={p.id} value={p.name}>{p.barcode}</option>
                      ))}
                    </datalist>
                    <Button type="submit" variant="secondary" disabled={busy || !scan.trim()}>Thêm</Button>
                  </form>
                )}

                <Table columns={lineColumns} data={lines} rowKey="productId" className="mp-flush-table" emptyText="Chưa đếm mặt hàng nào" maxHeight="55vh" />

                <div className="mp-pager">
                  <span className="mp-muted">
                    {lines.length} mặt hàng · {totals.diffLines} dòng lệch
                  </span>
                  <span>
                    Giá trị chênh lệch:{' '}
                    <strong className={totals.value < 0 ? 'mp-diff-neg' : totals.value > 0 ? 'mp-diff-pos' : ''}>
                      {formatCurrency(current.varianceValue ?? totals.value)}
                    </strong>
                  </span>
                </div>
              </>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
