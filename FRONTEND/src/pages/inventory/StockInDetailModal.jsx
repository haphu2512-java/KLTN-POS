import React from 'react';
import { Modal, Button, Table } from '../../components/common';
import { formatCurrency } from '../../utils/formatters';

export default function StockInDetailModal({
  isOpen,
  onClose,
  slip
}) {
  if (!slip) return null;

  const columns = [
    {
      key: 'stt',
      title: 'STT',
      width: '40px',
      align: 'center',
      render: (_, __, idx) => idx + 1
    },
    {
      key: 'productName',
      title: 'Tên hàng hóa',
      render: (val) => <span className="font-medium">{val}</span>
    },
    {
      key: 'quantity',
      title: 'Số lượng nhập',
      width: '120px',
      align: 'center',
      render: (val) => <span className="font-semibold tabular-nums">{val}</span>
    },
    {
      key: 'unitCost',
      title: 'Đơn giá vốn',
      width: '130px',
      align: 'right',
      render: (val) => <span className="tabular-nums">{formatCurrency(val)}</span>
    },
    {
      key: 'total',
      title: 'Thành tiền',
      width: '140px',
      align: 'right',
      render: (_, row) => (
        <span className="font-bold tabular-nums text-success">
          {formatCurrency(row.quantity * row.unitCost)}
        </span>
      )
    }
  ];

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={`Chi Tiết Phiếu Nhập Kho: ${slip.id}`}
      maxWidth="720px"
      footer={
        <Button variant="secondary" onClick={onClose}>
          Đóng (Esc)
        </Button>
      }
    >
      <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
        {/* Info Grid */}
        <div style={{
          display: 'grid',
          gridTemplateColumns: '1fr 1fr',
          gap: '10px',
          padding: '12px 14px',
          backgroundColor: 'var(--brand-50)',
          borderRadius: 'var(--radius-sm)',
          fontSize: '13px'
        }}>
          <div>
            <span className="text-muted">Nhà cung cấp: </span>
            <strong>{slip.supplierName}</strong>
          </div>
          <div>
            <span className="text-muted">Chi nhánh nhận: </span>
            <strong>{slip.branchName}</strong>
          </div>
          <div>
            <span className="text-muted">Thời gian nhập: </span>
            <span>{slip.createdDate}</span>
          </div>
          <div>
            <span className="text-muted">Người lập phiếu: </span>
            <span>{slip.employeeName}</span>
          </div>
        </div>

        {/* Items Table */}
        <Table
          columns={columns}
          data={slip.items}
          emptyText="Không có chi tiết hàng hóa"
        />

        {/* Total summary */}
        <div style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          padding: '10px 14px',
          backgroundColor: 'var(--brand-50)',
          borderRadius: 'var(--radius-sm)'
        }}>
          <span className="text-muted">Tổng số lượng nhập: <strong>{slip.totalItems} món</strong></span>
          <div>
            <span className="text-muted" style={{ marginRight: '8px' }}>Tổng giá trị:</span>
            <span className="font-bold tabular-nums text-success" style={{ fontSize: '18px' }}>
              {formatCurrency(slip.totalCost)}
            </span>
          </div>
        </div>
      </div>
    </Modal>
  );
}
