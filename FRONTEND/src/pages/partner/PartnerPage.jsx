import { useState, useEffect } from 'react';
import { Button, Table, Badge, Modal } from '../../components/common';
import { customerService } from '../../services/customerService';
import { purchaseService } from '../../services/purchaseService';
import { formatNumber, formatDateTime } from '../../utils/formatters';
import './PartnerPage.css';

export default function PartnerPage({
  isOnline = true
}) {
  const [activeTab, setActiveTab] = useState('customers'); // 'customers' | 'suppliers'
  const [customers, setCustomers] = useState([]);
  const [suppliers, setSuppliers] = useState([]);
  const [loading, setLoading] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');

  // Modals
  const [showAddCustomerModal, setShowAddCustomerModal] = useState(false);
  const [showAddSupplierModal, setShowAddSupplierModal] = useState(false);

  // Forms
  const [customerForm, setCustomerForm] = useState({ name: '', phone: '' });
  const [supplierForm, setSupplierForm] = useState({ name: '', phone: '', email: '', address: '' });

  const fetchCustomers = async (search = '') => {
    setLoading(true);
    try {
      const data = await customerService.getCustomers(search);
      setCustomers(data || []);
    } catch (err) {
      console.error('Lỗi khi tải khách hàng từ backend:', err);
      setCustomers([]);
    } finally {
      setLoading(false);
    }
  };

  const fetchSuppliers = async () => {
    setLoading(true);
    try {
      const data = await purchaseService.getSuppliers();
      setSuppliers(data || []);
    } catch (err) {
      console.error('Lỗi khi tải nhà cung cấp từ backend:', err);
      setSuppliers([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    // Tải dữ liệu khi đổi tab hoặc trạng thái kết nối là chủ ý (hàm fetch tự bật cờ loading)
    if (activeTab === 'customers') {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      fetchCustomers(searchTerm);
    } else {
      fetchSuppliers();
    }
  }, [activeTab, isOnline]);

  const handleSearch = (e) => {
    const term = e.target.value;
    setSearchTerm(term);
    if (activeTab === 'customers') {
      fetchCustomers(term);
    }
  };

  const handleCreateCustomer = async (e) => {
    e.preventDefault();
    if (!customerForm.name.trim() || !customerForm.phone.trim()) {
      alert('Vui lòng nhập tên và số điện thoại');
      return;
    }
    try {
      await customerService.createCustomer(customerForm);
      alert('Thêm khách hàng thành công!');
      setShowAddCustomerModal(false);
      setCustomerForm({ name: '', phone: '' });
      fetchCustomers(searchTerm);
    } catch (err) {
      alert(err.response?.data?.message || 'Lỗi khi tạo khách hàng');
    }
  };

  const handleCreateSupplier = async (e) => {
    e.preventDefault();
    if (!supplierForm.name.trim()) {
      alert('Vui lòng nhập tên nhà cung cấp');
      return;
    }
    try {
      await purchaseService.createSupplier(supplierForm);
      alert('Thêm nhà cung cấp thành công!');
      setShowAddSupplierModal(false);
      setSupplierForm({ name: '', phone: '', email: '', address: '' });
      fetchSuppliers();
    } catch (err) {
      alert(err.response?.data?.message || 'Lỗi khi tạo nhà cung cấp');
    }
  };

  const customerColumns = [
    { key: 'id', title: 'ID', width: '60px' },
    {
      key: 'name',
      title: 'Họ và tên',
      render: (c) => <strong style={{ color: 'var(--text-main)' }}>{c.name}</strong>
    },
    {
      key: 'phone',
      title: 'Số điện thoại',
      render: (c) => <span style={{ color: 'var(--text-main)', fontFamily: 'monospace' }}>{c.phone}</span>
    },
    {
      key: 'points',
      title: 'Điểm tích lũy',
      render: (c) => (
        <Badge variant={c.points > 50 ? 'brand' : 'secondary'}>
          {formatNumber(c.points || 0)} điểm
        </Badge>
      )
    },
    {
      key: 'createdAt',
      title: 'Ngày đăng ký',
      render: (c) => (c.createdAt ? formatDateTime(c.createdAt) : '—')
    }
  ];

  const filteredSuppliers = suppliers.filter(
    (s) =>
      s.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
      (s.phone && s.phone.includes(searchTerm)) ||
      (s.email && s.email.toLowerCase().includes(searchTerm.toLowerCase()))
  );

  const supplierColumns = [
    { key: 'id', title: 'ID', width: '60px' },
    {
      key: 'name',
      title: 'Tên nhà cung cấp',
      render: (s) => <strong style={{ color: 'var(--text-main)' }}>{s.name}</strong>
    },
    {
      key: 'phone',
      title: 'Số điện thoại',
      render: (s) => (s.phone ? <span style={{ fontFamily: 'monospace' }}>{s.phone}</span> : '—')
    },
    {
      key: 'email',
      title: 'Email',
      render: (s) => s.email || '—'
    },
    {
      key: 'address',
      title: 'Địa chỉ',
      render: (s) => s.address || '—'
    }
  ];

  return (
    <div className="partner-page">
      {/* Header */}
      <header className="page-header">
        <h1 className="page-title">Đối tác</h1>
        <span className="page-subtitle">Khách hàng thân thiết và nhà cung cấp</span>
      </header>

      {/* Content */}
      <div className="partner-content">
        <div className="partner-nav-tabs">
          <button
            className={`partner-tab-btn ${activeTab === 'customers' ? 'active' : ''}`}
            onClick={() => {
              setActiveTab('customers');
              setSearchTerm('');
            }}
          >
            Khách Hàng Thân Thiết ({customers.length})
          </button>
          <button
            className={`partner-tab-btn ${activeTab === 'suppliers' ? 'active' : ''}`}
            onClick={() => {
              setActiveTab('suppliers');
              setSearchTerm('');
            }}
          >
            Nhà Cung Cấp Hàng Hóa ({suppliers.length})
          </button>
        </div>

        <div className="partner-toolbar">
          <div>
            <input
              type="text"
              placeholder={
                activeTab === 'customers'
                  ? 'Tìm tên, số điện thoại khách hàng...'
                  : 'Tìm tên, SĐT, email nhà cung cấp...'
              }
              className="partner-search-input"
              value={searchTerm}
              onChange={handleSearch}
            />
          </div>
          <div>
            {activeTab === 'customers' ? (
              <Button variant="primary" onClick={() => setShowAddCustomerModal(true)}>
                Thêm khách hàng mới
              </Button>
            ) : (
              <Button variant="primary" onClick={() => setShowAddSupplierModal(true)}>
                Thêm nhà cung cấp mới
              </Button>
            )}
          </div>
        </div>

        <div className="partner-table-card">
          {activeTab === 'customers' ? (
            <Table
              columns={customerColumns}
              data={customers}
              loading={loading}
              emptyMessage="Không tìm thấy khách hàng nào"
            />
          ) : (
            <Table
              columns={supplierColumns}
              data={filteredSuppliers}
              loading={loading}
              emptyMessage="Không tìm thấy nhà cung cấp nào"
            />
          )}
        </div>
      </div>

      {/* Modal Add Customer */}
      <Modal
        isOpen={showAddCustomerModal}
        onClose={() => setShowAddCustomerModal(false)}
        title="Thêm khách hàng thân thiết mới"
        width="420px"
      >
        <form onSubmit={handleCreateCustomer}>
          <div className="partner-form-group">
            <label>Họ và tên khách hàng *</label>
            <input
              type="text"
              className="partner-form-input"
              placeholder="vd: Nguyễn Thị Hoa"
              value={customerForm.name}
              onChange={(e) => setCustomerForm({ ...customerForm, name: e.target.value })}
              required
            />
          </div>

          <div className="partner-form-group">
            <label>Số điện thoại *</label>
            <input
              type="tel"
              className="partner-form-input"
              placeholder="vd: 0912345678"
              value={customerForm.phone}
              onChange={(e) => setCustomerForm({ ...customerForm, phone: e.target.value })}
              required
            />
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '20px' }}>
            <Button type="button" variant="ghost" onClick={() => setShowAddCustomerModal(false)}>
              Hủy
            </Button>
            <Button type="submit" variant="primary">
              Lưu khách hàng
            </Button>
          </div>
        </form>
      </Modal>

      {/* Modal Add Supplier */}
      <Modal
        isOpen={showAddSupplierModal}
        onClose={() => setShowAddSupplierModal(false)}
        title="Thêm nhà cung cấp hàng hóa mới"
        width="460px"
      >
        <form onSubmit={handleCreateSupplier}>
          <div className="partner-form-group">
            <label>Tên nhà cung cấp *</label>
            <input
              type="text"
              className="partner-form-input"
              placeholder="vd: Công ty TNHH Thực Phẩm ABC"
              value={supplierForm.name}
              onChange={(e) => setSupplierForm({ ...supplierForm, name: e.target.value })}
              required
            />
          </div>

          <div className="partner-form-group">
            <label>Số điện thoại liên hệ</label>
            <input
              type="tel"
              className="partner-form-input"
              placeholder="vd: 028 3822 9999"
              value={supplierForm.phone}
              onChange={(e) => setSupplierForm({ ...supplierForm, phone: e.target.value })}
            />
          </div>

          <div className="partner-form-group">
            <label>Email liên hệ</label>
            <input
              type="email"
              className="partner-form-input"
              placeholder="vd: sales@supplier.com"
              value={supplierForm.email}
              onChange={(e) => setSupplierForm({ ...supplierForm, email: e.target.value })}
            />
          </div>

          <div className="partner-form-group">
            <label>Địa chỉ</label>
            <input
              type="text"
              className="partner-form-input"
              placeholder="vd: 123 Lê Lợi, Q.1, TP.HCM"
              value={supplierForm.address}
              onChange={(e) => setSupplierForm({ ...supplierForm, address: e.target.value })}
            />
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '20px' }}>
            <Button type="button" variant="ghost" onClick={() => setShowAddSupplierModal(false)}>
              Hủy
            </Button>
            <Button type="submit" variant="primary">
              Lưu nhà cung cấp
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
