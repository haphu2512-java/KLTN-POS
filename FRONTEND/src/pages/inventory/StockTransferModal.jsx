import React, { useState } from 'react';
import { Modal, Button, Input } from '../../components/common';

export default function StockTransferModal({
  isOpen,
  onClose,
  products = [],
  branches = [],
  currentBranchId = 1,
  onTransfer
}) {
  const [fromBranchId, setFromBranchId] = useState(currentBranchId);
  const [toBranchId, setToBranchId] = useState(branches.find((b) => b.id !== currentBranchId)?.id || 2);
  const [productId, setProductId] = useState(products[0]?.id || 1);
  const [quantity, setQuantity] = useState(1);
  const [note, setNote] = useState('');

  const selectedProduct = products.find((p) => p.id === Number(productId));

  const handleSubmit = (e) => {
    e.preventDefault();
    if (fromBranchId === toBranchId) {
      alert('Chi nhánh xuất và chi nhánh nhận không được trùng nhau!');
      return;
    }
    if (!selectedProduct) return;
    if (quantity > selectedProduct.stock) {
      alert(`Tồn kho tại chi nhánh xuất chỉ còn ${selectedProduct.stock} ${selectedProduct.unit}!`);
      return;
    }

    onTransfer({
      fromBranchId: Number(fromBranchId),
      toBranchId: Number(toBranchId),
      productId: Number(productId),
      quantity: Number(quantity),
      note
    });

    onClose();
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="Điều Chuyển Hàng Hóa Giữa Các Chi Nhánh"
      maxWidth="560px"
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Hủy (Esc)
          </Button>
          <Button variant="primary" onClick={handleSubmit}>
            ✔ Xác nhận điều chuyển
          </Button>
        </>
      }
    >
      <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
          <div>
            <label className="input-label" style={{ marginBottom: '4px', display: 'block' }}>
              Từ Chi Nhánh (Xuất)
            </label>
            <select
              value={fromBranchId}
              onChange={(e) => setFromBranchId(Number(e.target.value))}
              style={{
                width: '100%',
                height: '36px',
                border: '1px solid var(--border-subtle)',
                borderRadius: 'var(--radius-sm)',
                padding: '0 8px'
              }}
            >
              {branches.map((b) => (
                <option key={b.id} value={b.id}>{b.name}</option>
              ))}
            </select>
          </div>

          <div>
            <label className="input-label" style={{ marginBottom: '4px', display: 'block' }}>
              Đến Chi Nhánh (Nhận)
            </label>
            <select
              value={toBranchId}
              onChange={(e) => setToBranchId(Number(e.target.value))}
              style={{
                width: '100%',
                height: '36px',
                border: '1px solid var(--border-subtle)',
                borderRadius: 'var(--radius-sm)',
                padding: '0 8px'
              }}
            >
              {branches.map((b) => (
                <option key={b.id} value={b.id}>{b.name}</option>
              ))}
            </select>
          </div>
        </div>

        <div>
          <label className="input-label" style={{ marginBottom: '4px', display: 'block' }}>
            Sản phẩm cần điều chuyển
          </label>
          <select
            value={productId}
            onChange={(e) => setProductId(Number(e.target.value))}
            style={{
              width: '100%',
              height: '36px',
              border: '1px solid var(--border-subtle)',
              borderRadius: 'var(--radius-sm)',
              padding: '0 8px'
            }}
          >
            {products.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name} ({p.unit}) - Tồn hiện tại: {p.stock}
              </option>
            ))}
          </select>
        </div>

        <div>
          <Input
            label={`Số lượng chuyển (Tối đa: ${selectedProduct?.stock || 0} ${selectedProduct?.unit || ''})`}
            type="number"
            min="1"
            max={selectedProduct?.stock || 1}
            value={quantity}
            onChange={(e) => setQuantity(Math.max(1, Number(e.target.value) || 1))}
            required
          />
        </div>

        <div>
          <Input
            label="Ghi chú điều chuyển"
            value={note}
            onChange={(e) => setNote(e.target.value)}
            placeholder="Ví dụ: Bổ sung hàng thiếu hụt cuối tuần..."
          />
        </div>
      </form>
    </Modal>
  );
}
