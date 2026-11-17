import { useEffect, useMemo, useRef, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { Button, Badge, Table } from '../../components/common';
import { purchaseOrderService } from '../../services/purchaseOrderService';
import { purchaseService } from '../../services/purchaseService';
import { productService } from '../../services/productService';
import { formatCurrency, formatDateTime } from '../../utils/formatters';
import '../../components/layout/ManagePage.css';
import { confirmDialog } from '../../components/common/toastStore';
import { DATA_EVENTS, emitDataChange } from '../../utils/dataEvents';

const STATUS = {
  DRAFT: { label: 'Nháp', variant: 'brand' },
  SENT: { label: 'Đã gửi NCC', variant: 'warning' },
  PARTIALLY_RECEIVED: { label: 'Nhận một phần', variant: 'warning' },
  RECEIVED: { label: 'Đã nhận đủ', variant: 'success' },
  CANCELLED: { label: 'Đã hủy', variant: 'neutral' },
};

const errMsg = (err, fallback) => err?.response?.data?.message || fallback;
const remainingOf = (l) => Math.max(0, (l.orderedQty || 0) - (l.receivedQty || 0));
const catKey = (c) => (c || '').trim().toLowerCase();

/**
 * Đơn đặt hàng nhà cung cấp: lập nháp từ đề xuất AI (mỗi NCC một đơn) hoặc tạo tay, sửa số lượng / đơn giá,
 * gửi NCC, rồi nhận hàng (đủ hoặc từng phần). Mỗi lần nhận tạo một phiếu nhập kho, tồn kho tăng đúng số nhận.
 */
export default function PurchaseOrderPage() {
  const location = useLocation();
  const openedFromKey = useRef(null);
  const [orders, setOrders] = useState([]);
  const [current, setCurrent] = useState(null);
  const [suppliers, setSuppliers] = useState([]);
  const [products, setProducts] = useState([]);
  const [days, setDays] = useState(7);
  const [addTerm, setAddTerm] = useState('');
  const [receiveQty, setReceiveQty] = useState({});
  // Hạn dùng của lô nhận lần này, theo productId (tùy chọn)
  const [receiveExpiry, setReceiveExpiry] = useState({});
  const [message, setMessage] = useState(null);
  const [busy, setBusy] = useState(false);
  // Mặc định ô NCC chỉ hiện NCC có bán danh mục của mặt hàng trong đơn; bật để chọn NCC bất kỳ
  const [showAllSuppliers, setShowAllSuppliers] = useState(false);

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
    if (order?.id !== current?.id) {
      setShowAllSuppliers(false);
      // Danh mục NCC có thể vừa sửa ở trang Đối tác: tải lại để gợi ý đúng
      purchaseService.getSuppliers().then(setSuppliers).catch(() => {});
    }
    setCurrent(order);
    const qty = {};
    (order?.lines || []).forEach((l) => {
      qty[l.productId] = remainingOf(l);
    });
    setReceiveQty(qty);
    setReceiveExpiry({});
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
    openedFromKey.current = location.key;
    purchaseService.getSuppliers().then(setSuppliers).catch(() => {});
    productService.getProducts().then(setProducts).catch(() => {});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Trang được giữ lại khi chuyển màn hình: lần sau đến từ trang Dự báo thì tải lại và mở đơn vừa lập
  useEffect(() => {
    if (openedFromKey.current === location.key || !location.state?.openOrderId) return;
    openedFromKey.current = location.key;
    const openId = location.state.openOrderId;
    loadList().then((list) => {
      const found = list.find((o) => o.id === openId);
      if (found) select(found);
    });
    // eslint-disable-next-line react-hooks/set-state-in-effect
    if (location.state.message) setMessage({ text: location.state.message });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [location.key]);

  const run = async (fn, okText) => {
    setBusy(true);
    setMessage(null);
    try {
      const res = await fn();
      if (okText) setMessage({ text: typeof okText === 'function' ? okText(res) : okText });
      return res;
    } catch (err) {
      if (err?.response?.data?.code === 'NO_SUGGESTION') {
        // AI không có gì cần nhập: báo thông tin, không phải lỗi
        setMessage({ text: 'Hiện AI chưa đề xuất nhập mặt hàng nào cho chi nhánh này, chưa cần lập đơn. Bạn vẫn có thể tạo đơn trống và thêm hàng thủ công.' });
      } else {
        setMessage({ error: true, text: errMsg(err, 'Thao tác thất bại') });
      }
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
      (list) => {
        const noSupplier = list.find((o) => !o.supplierId);
        return `Đã lập ${list.length} đơn nháp từ đề xuất AI (${days} ngày), mỗi NCC một đơn theo danh mục.`
          + (noSupplier ? ` ${noSupplier.lines?.length || 0} mặt hàng chưa có NCC bán đúng danh mục, nằm ở đơn #${noSupplier.id}.` : ' Kiểm tra số lượng rồi gửi đơn.');
      }
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

  // Danh mục của từng dòng: lấy trên dòng đơn, đơn cũ chưa lưu thì tra theo sản phẩm
  const productCategory = useMemo(() => new Map(products.map((p) => [p.id, p.category])), [products]);
  const lineCategory = (l) => l.category || productCategory.get(l.productId) || null;
  const orderCategories = useMemo(() => {
    const m = new Map();
    lines.forEach((l) => {
      const c = lineCategory(l);
      if (c) m.set(catKey(c), c);
    });
    return m;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [lines, productCategory]);
  const supplierCats = (sup) => new Set((sup?.categories || []).map(catKey));
  const anySupplierHasCategories = suppliers.some((sup) => (sup.categories || []).length > 0);
  const currentSupplier = suppliers.find((sup) => sup.id === current?.supplierId);

  /** NCC gợi ý cho đơn: bán ít nhất một danh mục trong đơn, NCC bán đủ lên trước */
  const supplierChoices = useMemo(() => {
    const total = orderCategories.size;
    const rows = suppliers.map((sup) => {
      const cats = supplierCats(sup);
      const matched = [...orderCategories.keys()].filter((k) => cats.has(k)).length;
      return { sup, matched, total };
    });
    if (showAllSuppliers || !anySupplierHasCategories || total === 0) return { rows, filtered: false };
    const fit = rows.filter((r) => r.matched > 0 || r.sup.id === current?.supplierId).sort((a, b) => b.matched - a.matched);
    return { rows: fit, filtered: true };
  }, [suppliers, orderCategories, showAllSuppliers, anySupplierHasCategories, current?.supplierId]);

  const supplierLabel = ({ sup, matched, total }) => {
    if (!anySupplierHasCategories || total === 0) return sup.name;
    if (matched === total) return `${sup.name} (đủ danh mục)`;
    if (matched === 0) return `${sup.name} (không bán danh mục trong đơn)`;
    return `${sup.name} (bán ${matched}/${total} danh mục)`;
  };

  // Danh mục trong đơn mà NCC đang chọn không bán
  const mismatchedCategories = currentSupplier && anySupplierHasCategories
    ? [...orderCategories.entries()].filter(([k]) => !supplierCats(currentSupplier).has(k)).map(([, c]) => c)
    : [];
  const unsoldCategories = anySupplierHasCategories
    ? [...orderCategories.entries()].filter(([k]) => !suppliers.some((sup) => supplierCats(sup).has(k))).map(([, c]) => c)
    : [];

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
      .map((l) => ({
        productId: l.productId,
        quantity: Number(receiveQty[l.productId]) || 0,
        unitCost: l.unitCost,
        expiryDate: receiveExpiry[l.productId] || undefined,
      }))
      .filter((l) => l.quantity > 0);
    if (payload.length === 0) {
      setMessage({ error: true, text: 'Nhập số lượng nhận cho ít nhất một mặt hàng' });
      return;
    }
    const res = await run(
      () => purchaseOrderService.receive(current.id, payload),
      (r) => `Đã nhận hàng, tạo phiếu nhập kho #${r?.stockIn?.id ?? ''} và cộng tồn kho`
    );
    if (res) {
      // Trang Kho hàng đang được giữ sống ở nền: báo để tải lại lịch sử phiếu nhập và tồn kho
      emitDataChange(DATA_EVENTS.STOCK_IN_CHANGED, {
        branchId: res.stockIn?.branch?.id ?? res.order?.branchId,
        stockInId: res.stockIn?.id,
      });
    }
    if (res?.order) await refreshWith(res.order);
  };

  const cancel = async () => {
    if (!(await confirmDialog(`Hủy đơn đặt hàng #${current.id}?`, { title: 'Hủy đơn đặt hàng', confirmText: 'Hủy đơn', cancelText: 'Không', danger: true }))) return;
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
          <div className="mp-muted">
            {l.barcode}
            {lineCategory(l) ? ` · ${lineCategory(l)}` : ''}
            {currentSupplier && mismatchedCategories.some((c) => catKey(c) === catKey(lineCategory(l))) && (
              <span style={{ color: 'var(--danger-600)' }}> · NCC đang chọn không bán danh mục này</span>
            )}
          </div>
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
          {
            key: 'expiry',
            title: 'Hạn dùng',
            width: 140,
            render: (_, l) => (
              <input
                type="date"
                className="mp-input"
                min={new Date().toISOString().slice(0, 10)}
                value={receiveExpiry[l.productId] || ''}
                disabled={remainingOf(l) === 0}
                aria-label={`Hạn dùng lô nhận ${l.productName}`}
                title="Không bắt buộc; có hạn thì hệ thống cảnh báo cận date"
                onChange={(e) => setReceiveExpiry((x) => ({ ...x, [l.productId]: e.target.value }))}
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

        <div className="mp-split">
          <div className="mp-card">
            <Table columns={listColumns} data={orders} onRowClick={select} emptyText="Chưa có đơn đặt hàng" maxHeight="65vh" />
          </div>

          <div className="mp-card">
            {!current ? (
              <div className="mp-empty-state">
                <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                  <path d="M3 3h2l2.4 12.2a2 2 0 0 0 2 1.6h8.2a2 2 0 0 0 2-1.6L21 7H6M10 21a1 1 0 1 0 0-2 1 1 0 0 0 0 2zM18 21a1 1 0 1 0 0-2 1 1 0 0 0 0 2z" />
                </svg>
                <strong>Chưa chọn đơn đặt hàng</strong>
                <span>
                  Chọn một đơn bên trái, hoặc bấm “Lập đơn từ đề xuất AI” để hệ thống gom các mặt hàng cần nhập theo
                  nhà cung cấp có bán đúng danh mục.
                </span>
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
                    <>
                      <select id="po-supplier" className="mp-select" value={current.supplierId || ''} onChange={(e) => e.target.value && setSupplier(e.target.value)}>
                        <option value="">
                          {supplierChoices.filtered && supplierChoices.rows.length === 0
                            ? 'Chưa có NCC nào bán danh mục trong đơn'
                            : 'Chọn nhà cung cấp...'}
                        </option>
                        {supplierChoices.rows.map((r) => (
                          <option key={r.sup.id} value={r.sup.id}>{supplierLabel(r)}</option>
                        ))}
                      </select>
                      {anySupplierHasCategories && orderCategories.size > 0 && (
                        <label className="mp-muted" style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                          <input type="checkbox" checked={showAllSuppliers} onChange={(e) => setShowAllSuppliers(e.target.checked)} />
                          Hiện tất cả NCC
                        </label>
                      )}
                    </>
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
                  {editable && unsoldCategories.length > 0 && (
                    <span className="mp-muted" style={{ flexBasis: '100%' }}>
                      Chưa NCC nào bán danh mục: {unsoldCategories.join(', ')}. Gán danh mục cho NCC ở trang Đối tác.
                    </span>
                  )}
                  {editable && mismatchedCategories.length > 0 && (
                    <span style={{ flexBasis: '100%', color: 'var(--danger-600)', fontSize: 13 }}>
                      {currentSupplier.name} không bán: {mismatchedCategories.join(', ')}. Bỏ các mặt hàng này khỏi đơn hoặc chọn NCC khác.
                    </span>
                  )}
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
