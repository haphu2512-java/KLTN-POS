import React, { useRef } from 'react';
import { Tabs, Table, Button, Input } from '../../components/common';
import { formatCurrency } from '../../utils/formatters';

export default function PosCart({
  tabs,
  activeTabId,
  onSelectTab,
  onCloseTab,
  onAddTab,
  cartItems,
  onUpdateQuantity,
  onRemoveItem,
  onOpenSearch,
  barcodeInput,
  onChangeBarcodeInput,
  onSubmitBarcode,
  orderNote,
  onChangeOrderNote
}) {
  const barcodeRef = useRef(null);

  const columns = [
    {
      key: 'stt',
      title: 'STT',
      width: '45px',
      align: 'center',
      render: (_, __, idx) => idx + 1
    },
    {
      key: 'name',
      title: 'Tên hàng hóa / Mã vạch',
      render: (_, row) => (
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          {row.imageUrl && (
            <img
              src={row.imageUrl}
              alt=""
              style={{
                width: 32,
                height: 32,
                objectFit: 'contain',
                borderRadius: 4,
                backgroundColor: '#fff',
                border: '1px solid #e2e8f0',
                flexShrink: 0
              }}
              onError={(e) => { e.currentTarget.style.display = 'none'; }}
            />
          )}
          <div>
            <div className="font-medium" style={{ fontSize: '13px' }}>
              {row.name}
            </div>
            <div
              className="text-muted"
              style={{ fontSize: '11px', fontFamily: 'var(--font-mono)' }}
            >
              {row.barcode}
            </div>
          </div>
        </div>
      )
    },
    {
      key: 'unit',
      title: 'ĐVT',
      width: '65px',
      align: 'center'
    },
    {
      key: 'price',
      title: 'Đơn giá',
      width: '110px',
      align: 'right',
      render: (val) => <span className="tabular-nums">{formatCurrency(val)}</span>
    },
    {
      key: 'quantity',
      title: 'Số lượng',
      width: '130px',
      align: 'center',
      render: (val, row) => (
        <div style={{ display: 'inline-flex', alignItems: 'center', gap: '5px' }}>
          <button
            type="button"
            className="cart-qty-btn"
            onClick={() => onUpdateQuantity(row.id, -1)}
            title="Giảm 1"
          >
            -
          </button>
          <span
            className="font-bold tabular-nums"
            style={{ minWidth: '28px', textAlign: 'center', fontSize: '14px' }}
          >
            {val}
          </span>
          <button
            type="button"
            className="cart-qty-btn"
            onClick={() => onUpdateQuantity(row.id, 1)}
            title="Tăng 1"
          >
            +
          </button>
        </div>
      )
    },
    {
      key: 'total',
      title: 'Thành tiền',
      width: '125px',
      align: 'right',
      render: (_, row) => (
        <span className="font-bold tabular-nums text-success">
          {formatCurrency(row.price * row.quantity)}
        </span>
      )
    },
    {
      key: 'action',
      title: '',
      width: '50px',
      align: 'center',
      render: (_, row) => (
        <button
          type="button"
          className="cart-delete-btn"
          onClick={() => onRemoveItem(row.id)}
          title="Xóa khỏi đơn (Del)"
        >
          ✕
        </button>
      )
    }
  ];

  return (
    <div className="pos-cart-panel">
      {/* Tab bar for Multi-Orders */}
      <div className="pos-cart-tabs">
        <Tabs
          tabs={tabs}
          activeId={activeTabId}
          onSelect={onSelectTab}
          onClose={onCloseTab}
          onAdd={onAddTab}
        />
      </div>

      {/* Barcode / Search Input Bar */}
      <div className="pos-cart-searchbar">
        <form
          onSubmit={(e) => {
            e.preventDefault();
            onSubmitBarcode();
          }}
          style={{ flex: 1 }}
        >
          <Input
            ref={barcodeRef}
            value={barcodeInput}
            onChange={(e) => onChangeBarcodeInput(e.target.value)}
            placeholder="[F2] Quét mã vạch hoặc gõ tên sản phẩm..."
            prefix="🔍"
            hotkey="F2"
            autoFocus
          />
        </form>
        <Button
          variant="secondary"
          onClick={onOpenSearch}
          title="Mở danh sách tra cứu đầy đủ"
        >
          Tra cứu (F2)
        </Button>
      </div>

      {/* Products Table */}
      <div className="pos-cart-table-wrapper">
        <Table
          columns={columns}
          data={cartItems}
          emptyText="Chưa có sản phẩm nào. Đưa mã vạch vào máy quét hoặc nhấn F2 để tìm kiếm."
        />
      </div>

      {/* Cart Footer note & count */}
      <div className="pos-cart-footer">
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flex: 1, maxWidth: '420px' }}>
          <span className="text-muted" style={{ fontSize: '12px', whiteSpace: 'nowrap' }}>Ghi chú:</span>
          <input
            type="text"
            className="pos-note-input"
            value={orderNote}
            onChange={(e) => onChangeOrderNote(e.target.value)}
            placeholder="Ghi chú đơn hàng (giao sau, đóng gói cẩn thận...)"
          />
        </div>
        <div style={{ fontSize: '13px' }}>
          <span className="text-muted">Tổng số mặt hàng: </span>
          <strong className="tabular-nums">
            {cartItems.reduce((sum, item) => sum + item.quantity, 0)}
          </strong>
        </div>
      </div>
    </div>
  );
}
