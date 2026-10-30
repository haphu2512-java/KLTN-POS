import { useState, useEffect, useRef } from 'react';
import { Modal, Input, Button, Table } from '../../components/common';

export default function CustomerModal({
  isOpen,
  onClose,
  customers = [],
  onSelectCustomer,
  onAddNewCustomer
}) {
  const [searchTerm, setSearchTerm] = useState('');
  const [isAddingNew, setIsAddingNew] = useState(false);
  const [newName, setNewName] = useState('');
  const [newPhone, setNewPhone] = useState('');
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState('');
  const searchInputRef = useRef(null);

  // Reset form mỗi lần mở modal (điều chỉnh state khi prop đổi, không cần effect)
  const [wasOpen, setWasOpen] = useState(isOpen);
  if (isOpen !== wasOpen) {
    setWasOpen(isOpen);
    if (isOpen) {
      setSearchTerm('');
      setIsAddingNew(false);
      setNewName('');
      setNewPhone('');
      setSaveError('');
    }
  }

  useEffect(() => {
    if (!isOpen) return;
    const timer = setTimeout(() => searchInputRef.current?.focus(), 80);
    return () => clearTimeout(timer);
  }, [isOpen]);

  const filtered = customers.filter(
    (c) =>
      (c.name || '').toLowerCase().includes(searchTerm.toLowerCase()) ||
      (c.phone || '').includes(searchTerm)
  );

  // Lưu khách vào máy chủ trước rồi mới chọn, để hóa đơn gắn đúng khách và được tích điểm
  const handleSaveNew = async (e) => {
    e.preventDefault();
    if (!newName.trim() || saving) return;
    setSaving(true);
    setSaveError('');
    try {
      const created = await onAddNewCustomer({ name: newName.trim(), phone: newPhone.trim() });
      onSelectCustomer(created);
      onClose();
    } catch (err) {
      setSaveError(err.response?.data?.message || err.message || 'Không lưu được khách hàng');
    } finally {
      setSaving(false);
    }
  };

  const columns = [
    {
      key: 'name',
      title: 'Tên khách hàng',
      render: (val, row) => (
        <div>
          <span className="font-medium">{val}</span>
          {row.phone && (
            <span className="text-muted" style={{ display: 'block', fontSize: '11px' }}>
              SĐT: {row.phone}
            </span>
          )}
        </div>
      )
    },
    {
      key: 'points',
      title: 'Điểm tích lũy',
      width: '120px',
      align: 'right',
      render: (val) => <span className="tabular-nums font-semibold">{val ?? 0} điểm</span>
    },
    {
      key: 'action',
      title: '',
      width: '80px',
      align: 'center',
      render: (_, row) => (
        <Button
          size="sm"
          variant="primary"
          onClick={() => {
            onSelectCustomer(row);
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
      title={isAddingNew ? 'Thêm mới khách hàng' : 'Chọn khách hàng (F3)'}
      maxWidth="560px"
      footer={
        isAddingNew ? (
          <>
            <Button variant="secondary" onClick={() => setIsAddingNew(false)}>
              Quay lại danh sách
            </Button>
            <Button variant="success" onClick={handleSaveNew} disabled={saving}>
              {saving ? 'Đang lưu...' : 'Lưu & Chọn khách'}
            </Button>
          </>
        ) : (
          <>
            <Button variant="secondary" onClick={() => setIsAddingNew(true)}>
              + Thêm khách mới
            </Button>
            <Button variant="ghost" onClick={onClose}>
              Đóng (Esc)
            </Button>
          </>
        )
      }
    >
      {isAddingNew ? (
        <form onSubmit={handleSaveNew} style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          <Input
            label="Họ và tên khách hàng *"
            value={newName}
            onChange={(e) => setNewName(e.target.value)}
            placeholder="Ví dụ: Nguyễn Văn B..."
            autoFocus
            required
          />
          <Input
            label="Số điện thoại"
            value={newPhone}
            onChange={(e) => setNewPhone(e.target.value)}
            placeholder="Ví dụ: 0987123456..."
          />
          {saveError && <div className="text-danger" style={{ fontSize: '13px' }}>{saveError}</div>}
        </form>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          <Input
            ref={searchInputRef}
            placeholder="Tìm theo tên hoặc số điện thoại..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
          />
          <Table
            columns={columns}
            data={filtered}
            maxHeight="320px"
            emptyText="Không tìm thấy khách hàng"
            onRowClick={(row) => {
              onSelectCustomer(row);
              onClose();
            }}
          />
        </div>
      )}
    </Modal>
  );
}
