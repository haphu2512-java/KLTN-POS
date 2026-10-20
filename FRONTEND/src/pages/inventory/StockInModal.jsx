import { useState } from 'react';
import { Modal, Button } from '../../components/common';
import { formatCurrency } from '../../utils/formatters';

export default function StockInModal({
  isOpen,
  onClose,
  products = [],
  suppliers = [],
  branches = [],
  currentBranchId = 1,
  onSaveStockIn
}) {
  const [supplierId, setSupplierId] = useState(suppliers[0]?.id || 1);
  const [branchId, setBranchId] = useState(currentBranchId);
  const [note, setNote] = useState('');
  
  // Stock-in line items
  const [items, setItems] = useState([
    {
      productId: products[0]?.id || 1,
      productName: products[0]?.name || '',
      barcode: products[0]?.barcode || '',
      unit: products[0]?.unit || 'Cái',
      quantity: 10,
      unitCost: products[0]?.costPrice || 20000
    }
  ]);

  const handleAddItemRow = () => {
    const defaultProduct = products[0];
    if (!defaultProduct) return;
    setItems([
      ...items,
      {
        productId: defaultProduct.id,
        productName: defaultProduct.name,
        barcode: defaultProduct.barcode,
        unit: defaultProduct.unit,
        quantity: 10,
        unitCost: defaultProduct.costPrice
      }
    ]);
  };

  const handleProductChange = (index, prodId) => {
    const found = products.find((p) => p.id === Number(prodId));
    if (!found) return;
    const updated = [...items];
    updated[index] = {
      ...updated[index],
      productId: found.id,
      productName: found.name,
      barcode: found.barcode,
      unit: found.unit,
      unitCost: found.costPrice
    };
    setItems(updated);
  };

  const handleQuantityChange = (index, qty) => {
    const updated = [...items];
    updated[index].quantity = Math.max(1, Number(qty) || 1);
    setItems(updated);
  };

  const handleUnitCostChange = (index, cost) => {
    const updated = [...items];
    updated[index].unitCost = Math.max(0, Number(cost) || 0);
    setItems(updated);
  };

  const handleRemoveRow = (index) => {
    if (items.length <= 1) {
      alert('Phiếu nhập phải có ít nhất 1 sản phẩm!');
      return;
    }
    setItems(items.filter((_, idx) => idx !== index));
  };

  const totalCost = items.reduce(
    (sum, item) => sum + item.quantity * item.unitCost,
    0
  );

  const handleSubmit = (e) => {
    e.preventDefault();
    if (items.length === 0) {
      alert('Vui lòng thêm sản phẩm nhập kho!');
      return;
    }

    const supplier = suppliers.find((s) => s.id === Number(supplierId));
    const branch = branches.find((b) => b.id === Number(branchId));

    const newStockIn = {
      id: `NK-${Date.now().toString().slice(-6)}`,
      supplierId: Number(supplierId),
      supplierName: supplier?.name || 'Nhà cung cấp',
      branchId: Number(branchId),
      branchName: branch?.name || 'Chi nhánh',
      createdDate: new Date().toLocaleString('vi-VN'),
      employeeName: 'Nguyễn Văn A',
      totalItems: items.reduce((sum, item) => sum + item.quantity, 0),
      totalCost,
      items,
      note
    };

    onSaveStockIn(newStockIn);
    onClose();
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="Tạo Phiếu Nhập Kho Hàng Hóa (Stock-In)"
      maxWidth="780px"
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Hủy (Esc)
          </Button>
          <Button variant="success" onClick={handleSubmit}>
            ✔ Hoàn tất & Nhập Kho
          </Button>
        </>
      }
    >
      <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
        {/* Header fields */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '14px' }}>
          <div>
            <label className="input-label" style={{ marginBottom: '4px', display: 'block' }}>
              Nhà cung cấp *
            </label>
            <select
              className="input-control selectable"
              value={supplierId}
              onChange={(e) => setSupplierId(e.target.value)}
              style={{
                width: '100%',
                height: '36px',
                border: '1px solid var(--border-subtle)',
                borderRadius: 'var(--radius-sm)',
                padding: '0 8px',
                backgroundColor: 'var(--bg-surface)'
              }}
            >
              {suppliers.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className="input-label" style={{ marginBottom: '4px', display: 'block' }}>
              Chi nhánh nhập kho *
            </label>
            <select
              className="input-control selectable"
              value={branchId}
              onChange={(e) => setBranchId(e.target.value)}
              style={{
                width: '100%',
                height: '36px',
                border: '1px solid var(--border-subtle)',
                borderRadius: 'var(--radius-sm)',
                padding: '0 8px',
                backgroundColor: 'var(--bg-surface)'
              }}
            >
              {branches.map((b) => (
                <option key={b.id} value={b.id}>
                  {b.name}
                </option>
              ))}
            </select>
          </div>
        </div>

        {/* Table of items to stock-in */}
        <div>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
            <span className="font-semibold" style={{ fontSize: '13px' }}>
              Danh sách hàng hóa nhập kho ({items.length} món)
            </span>
            <Button size="sm" variant="secondary" onClick={handleAddItemRow}>
              + Thêm mặt hàng
            </Button>
          </div>

          <div style={{ border: '1px solid var(--border-subtle)', borderRadius: 'var(--radius-sm)', overflow: 'hidden' }}>
            <table className="data-table" style={{ width: '100%' }}>
              <thead>
                <tr>
                  <th style={{ width: '40px' }}>STT</th>
                  <th>Sản phẩm / Mã vạch</th>
                  <th style={{ width: '100px', textAlign: 'center' }}>Số lượng</th>
                  <th style={{ width: '130px', textAlign: 'right' }}>Giá vốn nhập (₫)</th>
                  <th style={{ width: '130px', textAlign: 'right' }}>Thành tiền</th>
                  <th style={{ width: '50px' }}></th>
                </tr>
              </thead>
              <tbody>
                {items.map((item, idx) => (
                  <tr key={idx}>
                    <td style={{ textAlign: 'center' }}>{idx + 1}</td>
                    <td>
                      <select
                        value={item.productId}
                        onChange={(e) => handleProductChange(idx, e.target.value)}
                        style={{
                          width: '100%',
                          height: '30px',
                          border: '1px solid var(--border-subtle)',
                          borderRadius: 'var(--radius-xs)',
                          padding: '0 6px',
                          fontSize: '12px'
                        }}
                      >
                        {products.map((p) => (
                          <option key={p.id} value={p.id}>
                            {p.name} ({p.unit}) - Tồn: {p.stock}
                          </option>
                        ))}
                      </select>
                    </td>
                    <td style={{ textAlign: 'center' }}>
                      <input
                        type="number"
                        min="1"
                        value={item.quantity}
                        onChange={(e) => handleQuantityChange(idx, e.target.value)}
                        style={{
                          width: '70px',
                          height: '30px',
                          textAlign: 'center',
                          border: '1px solid var(--border-subtle)',
                          borderRadius: 'var(--radius-xs)',
                          fontSize: '13px',
                          fontFamily: 'var(--font-mono)'
                        }}
                      />
                    </td>
                    <td style={{ textAlign: 'right' }}>
                      <input
                        type="number"
                        min="0"
                        step="1000"
                        value={item.unitCost}
                        onChange={(e) => handleUnitCostChange(idx, e.target.value)}
                        style={{
                          width: '110px',
                          height: '30px',
                          textAlign: 'right',
                          border: '1px solid var(--border-subtle)',
                          borderRadius: 'var(--radius-xs)',
                          padding: '0 6px',
                          fontSize: '13px',
                          fontFamily: 'var(--font-mono)'
                        }}
                      />
                    </td>
                    <td style={{ textAlign: 'right' }} className="font-semibold tabular-nums text-success">
                      {formatCurrency(item.quantity * item.unitCost)}
                    </td>
                    <td style={{ textAlign: 'center' }}>
                      <button
                        type="button"
                        className="cart-delete-btn"
                        onClick={() => handleRemoveRow(idx)}
                        title="Xóa dòng"
                      >
                        ✕
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        {/* Total Cost & Note */}
        <div style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          padding: '12px 16px',
          backgroundColor: 'var(--brand-50)',
          borderRadius: 'var(--radius-sm)',
          border: '1px solid var(--border-subtle)'
        }}>
          <div style={{ flex: 1, maxWidth: '400px' }}>
            <input
              type="text"
              placeholder="Ghi chú phiếu nhập (số hóa đơn đỏ, chứng từ...)"
              value={note}
              onChange={(e) => setNote(e.target.value)}
              className="pos-note-input"
              style={{ width: '100%', height: '32px' }}
            />
          </div>
          <div>
            <span className="text-muted" style={{ marginRight: '8px' }}>Tổng giá trị nhập kho:</span>
            <span className="font-bold tabular-nums text-success" style={{ fontSize: '20px' }}>
              {formatCurrency(totalCost)}
            </span>
          </div>
        </div>
      </form>
    </Modal>
  );
}
