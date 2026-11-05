import { useEffect, useMemo, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { Button, Badge, Table } from '../../components/common';
import { purchaseOrderService } from '../../services/purchaseOrderService';
import { purchaseService } from '../../services/purchaseService';
import { productService } from '../../services/productService';
import { formatCurrency, formatDateTime } from '../../utils/formatters';
import '../../components/layout/ManagePage.css';

const STATUS = {
  DRAFT: { label: 'Nháp', variant: 'brand' },
  SENT: { label: 'Đã gửi NCC', variant: 'warning' },
  PARTIALLY_RECEIVED: { label: 'Nhận một phần', variant: 'warning' },
  RECEIVED: { label: 'Đã nhận đủ', variant: 'success' },
  CANCELLED: { label: 'Đã hủy', variant: 'neutral' },
};

const errMsg = (err, fallback) => err?.response?.data?.message || fallback;
const remainingOf = (l) => Math.max(0, (l.orderedQty || 0) - (l.receivedQty || 0));

/**
 * Đơn đặt hàng nhà cung cấp: lập nháp từ đề xuất AI (mỗi NCC một đơn) hoặc tạo tay, sửa số lượng / đơn giá,
 * gửi NCC, rồi nhận hàng (đủ hoặc từng phần). Mỗi lần nhận tạo một phiếu nhập kho, tồn kho tăng đúng số nhận.
 */
export default function PurchaseOrderPage() {
  const location = useLocation();
  const [orders, setOrders] = useState([]);
  const [current, setCurrent] = useState(null);
  const [suppliers, setSuppliers] = useState([]);
  const [products, setProducts] = useState([]);
  const [days, setDays] = useState(7);
  const [addTerm, setAddTerm] = useState('');
  const [receiveQty, setReceiveQty] = useState({});
  const [message, setMessage] = useState(null);
  const [busy, setBusy] = useState(false);

  const loadList = async () => {
    try {
      const list = await purchaseOrderService.list();
      setOrders(list);
      return list;
    } catch (err) {
      setMessage({ error: true, text: errMsg(err, 'Không tải được danh sách đơn đặt hàng') });
      return [];
    }
  };

  const select = (order) => {
    setCurrent(order);
    const qty = {};
    (order?.lines || []).forEach((l) => {
      qty[l.productId] = remainingOf(l);
    });
    setReceiveQty(qty);
  };

  useEffect(() => {
    // Tải dữ liệu lần đầu; mở sẵn đơn vừa lập nếu đến từ trang Dự báo
    const openId = location.state?.openOrderId;
    // eslint-disable-next-line react-hooks/set-state-in-effect
    loadList().then((list) => {
      const found = openId && list.find((o) => o.id === openId);
      if (found) select(found);
    });
    if (location.state?.message) {
      setMessage({ text: location.state.message });
    }
    purchaseService.getSuppliers().then(setSuppliers).catch(() => {});
    productService.getProducts().then(setProducts).catch(() => {});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const run = async (fn, okText) => {
    setBusy(true);
    setMessage(null);
    try {
      const res = await fn();
      if (okText) setMessage({ text: typeof okText === 'function' ? okText(res) : okText });
      return res;
    } catch (err) {
      setMessage({ error: true, text: errMsg(err, 'Thao tác thất bại') });
      return null;
    } finally {
      setBusy(false);
    }
  };

  const refreshWith = async (order) => {
    if (order?.id) select(order);
    await loadList();
  };

  const createFromAi = async () => {
    const created = await run(
      () => purchaseOrderService.fromForecast(days),
      (list) => `Đã lập ${list.length} đơn nháp từ đề xuất AI (${days} ngày). Kiểm tra số lượng, chọn NCC rồi gửi đơn.`
    );
    if (created?.length) await refreshWith(created[0]);
  };

  const createEmpty = async () => {
    const order = await run(() => purchaseOrderService.create({ lines: [] }), 'Đã tạo đơn trống, thêm mặt hàng bên dưới');
    if (order) await refreshWith(order);
  };

  const editable = current?.status === 'DRAFT';
  const receivable = current?.status === 'SENT' || current?.status === 'PARTIALLY_RECEIVED';
  const lines = useMemo(() => current?.lines || [], [current]);

  const saveLines = (nextLines) =>
    run(() =>
      purchaseOrderService.update(current.id, {
        lines: nextLines.map((l) => ({ productId: l.productId, orderedQty: l.orderedQty, unitCost: l.unitCost })),
      })
    ).then((o) => o && refreshWith(o));

  const patchLine = (line, patch) => saveLines(lines.map((l) => (l.productId === line.productId ? { ...l, ...patch } : l)));

  const addProduct = (e) => {
    e.preventDefault();
    const term = addTerm.trim().toLowerCase();
    if (!term) return;
    const p = products.find((x) => x.name.toLowerCase() === term || String(x.barcode || '').toLowerCase() === term);
    if (!p) {
      setMessage({ error: true, text: 'Không tìm thấy sản phẩm theo tên hoặc mã vạch' });
      return;
    }
    setAddTerm('');
    const existing = lines.find((l) => l.productId === p.id);
    if (existing) return patchLine(existing, { orderedQty: existing.orderedQty + 1 });
    return saveLines([...lines, { productId: p.id, orderedQty: 1, unitCost: p.costPrice }]);
  };

  const setSupplier = (supplierId) =>
    run(() => purchaseOrderService.update(current.id, { supplierId: Number(supplierId) })).then((o) => o && refreshWith(o));
  const setExpected = (date) =>
    run(() => purchaseOrderService.update(current.id, { expectedDate: date || null })).then((o) => o && refreshWith(o));

  const send = async () => {
    const o = await run(() => purchaseOrderService.send(current.id), 'Đã chuyển sang Đã gửi nhà cung cấp');
    if (o) await refreshWith(o);
  };

  const receive = async () => {
    const payload = lines
      .map((l) => ({ productId: l.productId, quantity: Number(receiveQty[l.productId]) || 0, unitCost: l.unitCost }))
      .filter((l) => l.quantity > 0);
    if (payload.length === 0) {
      setMessage({ error: true, text: 'Nhập số lượng nhận cho ít nhất một mặt hàng' });
      return;
    }
    const res = await run(
      () => purchaseOrderService.receive(current.id, payload),
      (r) => `Đã nhận hàng, tạo phiếu nhập kho #${r?.stockIn?.id ?? ''} và cộng tồn kho`
    );
    if (res?.order) await refreshWith(res.order);
  };

  const cancel = async () => {
    if (!window.confirm(`Hủy đơn đặt hàng #${current.id}?`)) return;
    const o = await run(() => purchaseOrderService.cancel(current.id), 'Đã hủy đơn');
    if (o) await refreshWith(o);
  };

  const total = lines.reduce((s, l) => s + (l.orderedQty || 0) * (Number(l.unitCost) || 0), 0);

  const lineColumns = [
    {
      key: 'productName',
      title: 'Sản phẩm',
      render: (v, l) => (
        <div>
          <div>{v}</div>
          <div className="mp-muted">{l.barcode}</div>
          {l.forecastNote && (
            <div className="mp-muted" title="Lý do AI đề xuất">
              AI: {l.forecastNote}
              {l.suggestedQty != null && l.suggestedQty !== l.orderedQty ? ` · đề xuất ${l.suggestedQty}` : ''}
            </div>
          )}
        </div>
      ),
    },
    {
      key: 'orderedQty',
      title: 'Đặt',
      align: 'right',
      width: 100,
      render: (v, l) =>
        editable ? (
          <input
            type="number"
            min={1}
            className="mp-input mp-qty"
            defaultValue={v}
            key={`${l.productId}-q-${v}`}
            aria-label={`Số lượng đặt ${l.productName}`}
            onBlur={(e) => {
              const n = Math.max(0, Math.floor(Number(e.target.value) || 0));
              if (n !== v) {
                if (n === 0) saveLines(lines.filter((x) => x.productId !== l.productId));
                else patchLine(l, { orderedQty: n });
              }
            }}
          />
        ) : (
          v
        ),
    },
    {
      key: 'unitCost',
      title: 'Đơn giá',
      align: 'right',
      width: 130,
      render: (v, l) =>
        editable ? (
          <input
            type="number"
            min={0}
            step={100}
            className="mp-input mp-qty"
            style={{ width: 110 }}
            defaultValue={v ?? ''}
            key={`${l.productId}-c-${v}`}
            aria-label={`Đơn giá ${l.productName}`}
            onBlur={(e) => {
              const n = Math.max(0, Number(e.target.value) || 0);
              if (n !== Number(v)) patchLine(l, { unitCost: n });
            }}
          />
        ) : (
          formatCurrency(v || 0)
        ),
    },
    { key: 'amount', title: 'Thành tiền', align: 'right', width: 120, render: (_, l) => formatCurrency((l.orderedQty || 0) * (Number(l.unitCost) || 0)) },
    ...(current && current.status !== 'DRAFT'
      ? [{ key: 'receivedQty', title: 'Đã nhận', align: 'right', width: 90, render: (v) => v || 0 }]
      : []),
    ...(receivable
      ? [
          {
            key: 'receiveNow',
            title: 'Nhận lần này',
            align: 'right',
            width: 120,
            render: (_, l) => (
              <input
                type="number"
                min={0}
                max={remainingOf(l)}
                className="mp-input mp-qty"
                value={receiveQty[l.productId] ?? 0}
                disabled={remainingOf(l) === 0}
                aria-label={`Số nhận lần này ${l.productName}`}
                onChange={(e) =>
                  setReceiveQty((q) => ({
                    ...q,
                    [l.productId]: Math.min(remainingOf(l), Math.max(0, Math.floor(Number(e.target.value) || 0))),
                  }))
                }
              />
            ),
          },
        ]
      : []),
  ];

  const listColumns = [
    { key: 'id', title: 'Đơn', width: 60, render: (v) => `#${v}` },
    {
      key: 'supplierName',
      title: 'Nhà cung cấp',
      render: (v, o) => (
        <div>
          <div>{v || <span className="mp-diff-neg">Chưa chọn NCC</span>}</div>
          <div className="mp-muted">
            {formatDateTime(o.createdAt)}
            {o.source === 'FORECAST' ? ' · từ AI' : ''}
          </div>
        </div>
      ),
    },
    { key: 'totalAmount', title: 'Tổng', align: 'right', render: (v) => formatCurrency(v || 0) },
    { key: 'status', title: 'Trạng thái', render: (v) => <Badge variant={STATUS[v]?.variant}>{STATUS[v]?.label || v}</Badge> },
  ];

  return (
    <div className="mp-page">
      <header className="page-header">
        <h1 className="page-title">Đặt hàng nhà cung cấp</h1>
        <span className="page-subtitle">
          Lập đơn từ đề xuất nhập hàng của AI, gửi nhà cung cấp và nhận hàng vào kho
        </span>
      </header>

      <div className="mp-content">
        <div className="mp-toolbar">
          <div className="mp-toolbar-left">
            <label className="mp-muted" htmlFor="po-days">Dự báo cho</label>
            <select id="po-days" className="mp-select" value={days} onChange={(e) => setDays(Number(e.target.value))}>
              <option value={7}>7 ngày</option>
              <option value={14}>14 ngày</option>
              <option value={30}>30 ngày</option>
            </select>
            <Button variant="primary" onClick={createFromAi} disabled={busy}>Lập đơn từ đề xuất AI</Button>
          </div>
          <div className="mp-actions">
            <Button variant="secondary" onClick={createEmpty} disabled={busy}>Tạo đơn trống</Button>
          </div>
        </div>

        {message && (
          <div className={`mp-card mp-card-pad ${message.error ? 'mp-diff-neg' : ''}`} role="status">
            {message.text}
          </div>
        )}

        <div style={{ display: 'grid', gridTemplateColumns: 'minmax(300px, 400px) 1fr', gap: 16, alignItems: 'start' }}>
          <div className="mp-card">
            <Table columns={listColumns} data={orders} onRowClick={select} emptyText="Chưa có đơn đặt hàng" maxHeight="65vh" />
          </div>

          <div className="mp-card">
            {!current ? (
              <div className="mp-card-pad mp-muted">
                Chọn một đơn bên trái, hoặc bấm “Lập đơn từ đề xuất AI” để hệ thống gom các mặt hàng cần nhập theo nhà
                cung cấp gần nhất.
              </div>
            ) : (
              <>
                <div className="mp-toolbar" style={{ border: 'none', borderRadius: 0 }}>
                  <div className="mp-toolbar-left">
                    <strong>Đơn #{current.id}</strong>
                    <Badge variant={STATUS[current.status]?.variant}>{STATUS[current.status]?.label}</Badge>
                    {current.note && <span className="mp-muted">{current.note}</span>}
                  </div>
                  <div className="mp-actions">
                    {editable && (
                      <Button variant="primary" disabled={busy || lines.length === 0 || !current.supplierId} onClick={send}
                        title={!current.supplierId ? 'Chọn nhà cung cấp trước' : undefined}>
                        Gửi nhà cung cấp
                      </Button>
                    )}
                    {receivable && (
                      <Button variant="success" disabled={busy} onClick={receive}>Nhận hàng vào kho</Button>
                    )}
                    {(editable || receivable) && (
                      <Button variant="ghost" disabled={busy} onClick={cancel}>Hủy đơn</Button>
                    )}
                  </div>
                </div>

                <div className="mp-card-pad" style={{ display: 'flex', flexWrap: 'wrap', gap: 12, alignItems: 'center', borderTop: '1px solid var(--border-subtle)' }}>
                  <label className="mp-muted" htmlFor="po-supplier">Nhà cung cấp</label>
                  {editable ? (
                    <select id="po-supplier" className="mp-select" value={current.supplierId || ''} onChange={(e) => e.target.value && setSupplier(e.target.value)}>
                      <option value="">Chọn nhà cung cấp...</option>
                      {suppliers.map((s) => (
                        <option key={s.id} value={s.id}>{s.name}</option>
                      ))}
                    </select>
                  ) : (
                    <strong>{current.supplierName || '—'}</strong>
                  )}
                  <label className="mp-muted" htmlFor="po-expected">Ngày giao dự kiến</label>
                  {editable ? (
                    <input id="po-expected" type="date" className="mp-input" value={current.expectedDate || ''} onChange={(e) => setExpected(e.target.value)} />
                  ) : (
                    <span>{current.expectedDate || '—'}</span>
                  )}
                  {current.sentAt && <span className="mp-muted">Gửi lúc {formatDateTime(current.sentAt)}</span>}
                </div>

                {editable && (
                  <form onSubmit={addProduct} className="mp-card-pad" style={{ display: 'flex', gap: 8, borderTop: '1px solid var(--border-subtle)' }}>
                    <input
                      className="mp-input"
                      style={{ flex: 1 }}
                      list="po-products"
                      placeholder="Thêm mặt hàng: gõ tên hoặc quét mã vạch rồi Enter"
                      value={addTerm}
                      onChange={(e) => setAddTerm(e.target.value)}
                      aria-label="Thêm mặt hàng"
                    />
                    <datalist id="po-products">
                      {products.slice(0, 2000).map((p) => (
                        <option key={p.id} value={p.name}>{p.barcode}</option>
                      ))}
                    </datalist>
                    <Button type="submit" variant="secondary" disabled={busy || !addTerm.trim()}>Thêm</Button>
                  </form>
                )}

                <Table columns={lineColumns} data={lines} rowKey="productId" emptyText="Đơn chưa có mặt hàng nào" maxHeight="55vh" />

                <div className="mp-pager">
                  <span className="mp-muted">{lines.length} mặt hàng</span>
                  <span>
                    Tổng giá trị đơn: <strong>{formatCurrency(total)}</strong>
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
