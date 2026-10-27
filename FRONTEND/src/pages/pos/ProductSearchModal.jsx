import { useState, useEffect, useRef } from 'react';
import { Modal, Input, Table, Badge, Button, BarcodeView } from '../../components/common';
import { formatCurrency } from '../../utils/formatters';

export default function ProductSearchModal({
  isOpen,
  onClose,
  products = [],
  loading = false,
  onSelectProduct
}) {
  const [searchTerm, setSearchTerm] = useState('');
  const inputRef = useRef(null);

  // Xóa từ khóa mỗi lần mở modal (điều chỉnh state khi prop đổi, không cần effect)
  const [wasOpen, setWasOpen] = useState(isOpen);
  if (isOpen !== wasOpen) {
    setWasOpen(isOpen);
    if (isOpen) setSearchTerm('');
  }

  useEffect(() => {
    if (!isOpen) return;
    const timer = setTimeout(() => inputRef.current?.focus(), 80);
    return () => clearTimeout(timer);
  }, [isOpen]);

  const filtered = products.filter((p) => {
    const term = searchTerm.toLowerCase().trim();
    if (!term) return true;
    return (
      (p.name && p.name.toLowerCase().includes(term)) ||
      (p.barcode && String(p.barcode).toLowerCase().includes(term)) ||
      (p.category && p.category.toLowerCase().includes(term))
    );
  });

  const displayedProducts = filtered.slice(0, 100);

  const columns = [
    {
      key: 'barcode',
      title: 'Mã vạch (Barcode)',
      width: '150px',
      render: (val) => (
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
          <BarcodeView
            value={val}
            width={1.1}
            height={26}
            fontSize={10}
            margin={1}
          />
        </div>
      )
    },
    {
      key: 'name',
      title: 'Tên hàng hóa',
      render: (val, row) => (
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <div
            style={{
              width: 40,
              height: 40,
              minWidth: 40,
              borderRadius: 6,
              backgroundColor: '#fff',
              border: '1px solid var(--text-main)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              overflow: 'hidden',
              flexShrink: 0
            }}
          >
            {row.imageUrl ? (
              <img
                src={row.imageUrl}
                alt=""
                style={{
                  width: '100%',
                  height: '100%',
                  objectFit: 'contain'
                }}
                onError={(e) => {
                  e.currentTarget.style.display = 'none';
                  if (e.currentTarget.nextElementSibling) {
                    e.currentTarget.nextElementSibling.style.display = 'flex';
                  }
                }}
              />
            ) : null}
          </div>
          <div>
            <div className="font-medium">{val}</div>
            <span className="text-muted" style={{ fontSize: '11px' }}>
              {row.category} • ĐVT: {row.unit}
            </span>
          </div>
        </div>
      )
    },
    {
      key: 'stock',
      title: 'Tồn kho',
      width: '110px',
      align: 'center',
      render: (val) => {
        if (val <= 0) return <Badge variant="danger">Hết hàng</Badge>;
        if (val <= 5) return <Badge variant="warning">{val} (Sắp hết)</Badge>;
        return <Badge variant="success">{val}</Badge>;
      }
    },
    {
      key: 'salePrice',
      title: 'Giá bán',
      width: '110px',
      align: 'right',
      render: (val) => (
        <span className="font-semibold tabular-nums text-success">
          {formatCurrency(val)}
        </span>
      )
    },
    {
      key: 'action',
      title: '',
      width: '90px',
      align: 'center',
      render: (_, row) => (
        <Button
          size="sm"
          variant="primary"
          disabled={row.stock <= 0}
          onClick={(e) => {
            if (e && e.stopPropagation) {
              e.stopPropagation();
            }
            onSelectProduct(row);
            onClose();
          }}
        >
          Chọn
        </Button>
      )
    }
  ];

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="Tra cứu & Chọn sản phẩm (F2)"
      maxWidth="720px"
      footer={
        <Button variant="secondary" onClick={onClose}>
          Đóng (Esc)
        </Button>
      }
    >
      <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
        <Input
          ref={inputRef}
          placeholder="Nhập tên hàng hóa, mã vạch hoặc danh mục..."
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          autoFocus
        />

        <Table
          columns={columns}
          data={displayedProducts}
          loading={loading}
          loadingText="Đang nạp danh mục hàng hóa..."
          maxHeight="380px"
          emptyText="Không tìm thấy sản phẩm nào phù hợp"
          onRowClick={(row) => {
            if (row.stock > 0) {
              onSelectProduct(row);
              onClose();
            }
          }}
        />
      </div>
    </Modal>
  );
}
