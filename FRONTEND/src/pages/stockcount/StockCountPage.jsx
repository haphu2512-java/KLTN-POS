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
      stockCountService.get(location.state.openCountId).then(setCurrent).catch(() => {});
      if (location.state.message) setMessage({ text: location.state.message });
    }
    stockCountService.reasons().then(setReasons).catch(() => {});
    productService.getProducts().then(setProducts).catch(() => {});
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

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
    {
      key: 'id',
      title: 'Phiếu',
      render: (v, c) => (
        <div>
          <div>
            <strong>#{v}</strong>
            {c.note && <span className="mp-muted"> · {c.note}</span>}
          </div>
          <div className="mp-muted">{formatDateTime(c.createdAt)}</div>
        </div>
      ),
    },
    { key: 'lines', title: 'Mặt hàng', align: 'right', width: 84, render: (v) => (v || []).length },
    { key: 'status', title: 'Trạng thái', width: 110, render: (v) => <Badge variant={STATUS[v]?.variant}>{STATUS[v]?.label || v}</Badge> },
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
            <div className="mp-card-pad" style={{ display: 'flex', gap: 8 }}>
              <input
                className="mp-input"
                style={{ flex: 1 }}
                placeholder="Ghi chú (vd. Kiểm kê cuối tháng)"
                value={note}
                onChange={(e) => setNote(e.target.value)}
              />
              <Button variant="primary" onClick={createCount} disabled={busy}>Tạo phiếu</Button>
            </div>
            <Table
              columns={listColumns}
              data={counts}
              onRowClick={open}
              emptyText="Chưa có phiếu kiểm kê"
              maxHeight="60vh"
            />
          </div>

          <div className="mp-card">
            {!current ? (
              <div className="mp-empty">
                <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                  <path d="M9 5H7a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-2M9 5a2 2 0 0 0 2 2h2a2 2 0 0 0 2-2M9 5a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2M9 14l2 2 4-4" />
                </svg>
                <div className="mp-empty-title">Chưa chọn phiếu kiểm kê</div>
                <div className="mp-empty-text">
                  Chọn một phiếu ở danh sách bên trái, hoặc nhập ghi chú rồi bấm “Tạo phiếu” để bắt đầu quét mã đếm hàng.
                </div>
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

                <Table columns={lineColumns} data={lines} rowKey="productId" emptyText="Chưa đếm mặt hàng nào" maxHeight="55vh" />

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
