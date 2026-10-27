import { useState, useEffect } from 'react';
import { Button, Table, Badge, Modal } from '../../components/common';
import { userService } from '../../services/userService';
import { formatDateTime } from '../../utils/formatters';
import useBranches, { branchNameOf } from '../../hooks/useBranches';
import './StaffPage.css';

/** Ô chọn nhiều chi nhánh được phân công; quản trị không cần (làm việc được mọi chi nhánh) */
function BranchAssignment({ role, branches, value, onChange }) {
  if (role === 'ADMIN') {
    return <div className="staff-branch-note">Quản trị viên chọn được mọi chi nhánh khi đăng nhập.</div>;
  }
  const toggle = (id) =>
    onChange(value.includes(id) ? value.filter((v) => v !== id) : [...value, id]);
  return (
    <div className="staff-branch-list">
      {branches.map((b) => (
        <label key={b.id} className="staff-branch-option">
          <input type="checkbox" checked={value.includes(b.id)} onChange={() => toggle(b.id)} />
          {b.name}
        </label>
      ))}
      <div className="staff-branch-note">
        Tài khoản chọn một trong các chi nhánh này khi đăng nhập. Không bỏ được chi nhánh đang có ca chưa đóng.
      </div>
    </div>
  );
}

export default function StaffPage({
  branchId = 1,
  isOnline = true
}) {
  const branches = useBranches([{ id: branchId, name: `Chi nhánh ${branchId}` }]);
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const [filterRole, setFilterRole] = useState('ALL');

  // Modals
  const [showAddModal, setShowAddModal] = useState(false);
  const [showEditModal, setShowEditModal] = useState(false);
  const [showPasswordModal, setShowPasswordModal] = useState(false);
  const [selectedUser, setSelectedUser] = useState(null);

  // Form states
  const [formData, setFormData] = useState({
    username: '',
    password: '',
    role: 'CASHIER',
    branchIds: [branchId || 1]
  });
  const [editFormData, setEditFormData] = useState({
    id: null,
    username: '',
    role: 'CASHIER',
    branchIds: [],
    active: true
  });
  const [newPassword, setNewPassword] = useState('');

  const fetchUsers = async () => {
    if (!isOnline) {
      // Offline fallback dummy
      setUsers([
        { id: 1, username: 'admin', fullName: 'Quản Trị Viên', role: 'ADMIN', branchIds: [], active: true, createdAt: new Date().toISOString() },
        { id: 2, username: 'cashier1', fullName: 'Thu Ngân Q1', role: 'CASHIER', branchIds: [1], active: true, createdAt: new Date().toISOString() }
      ]);
      return;
    }
    setLoading(true);
    try {
      const data = await userService.getUsers();
      setUsers(data || []);
    } catch (err) {
      console.error('Lỗi khi tải danh sách tài khoản:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    // Tải danh sách khi mở trang / đổi trạng thái kết nối là chủ ý (hàm fetch tự bật cờ loading)
    // eslint-disable-next-line react-hooks/set-state-in-effect
    fetchUsers();
  }, [isOnline]);

  const handleCreateUser = async (e) => {
    e.preventDefault();
    if (!formData.username.trim() || !formData.password.trim()) {
      alert('Vui lòng điền đầy đủ các thông tin bắt buộc');
      return;
    }
    if (formData.role !== 'ADMIN' && formData.branchIds.length === 0) {
      alert('Chọn ít nhất một chi nhánh cho tài khoản');
      return;
    }
    try {
      await userService.createUser(formData);
      alert('Tạo tài khoản thành công!');
      setShowAddModal(false);
      setFormData({ username: '', password: '', role: 'CASHIER', branchIds: [branchId || 1] });
      fetchUsers();
    } catch (err) {
      alert(err.response?.data?.message || 'Có lỗi xảy ra khi tạo tài khoản');
    }
  };

  const handleToggleStatus = async (user) => {
    if (user.username === 'admin') {
      alert('Không thể khóa tài khoản quản trị tối cao!');
      return;
    }
    const action = user.active ? 'khóa' : 'mở khóa';
    if (!window.confirm(`Bạn có chắc chắn muốn ${action} tài khoản "${user.username}"?`)) return;

    try {
      await userService.toggleUserStatus(user.id);
      fetchUsers();
    } catch (err) {
      alert(err.response?.data?.message || 'Không thể đổi trạng thái tài khoản');
    }
  };

  const handleChangePassword = async (e) => {
    e.preventDefault();
    if (!newPassword || newPassword.length < 6) {
      alert('Mật khẩu mới phải từ 6 ký tự trở lên');
      return;
    }
    try {
      await userService.changePassword(selectedUser.id, newPassword);
      alert('Đổi mật khẩu thành công!');
      setShowPasswordModal(false);
      setNewPassword('');
    } catch (err) {
      alert(err.response?.data?.message || 'Lỗi khi đổi mật khẩu');
    }
  };

  const handleOpenEdit = (user) => {
    setEditFormData({
      id: user.id,
      username: user.username,
      role: user.role || 'CASHIER',
      branchIds: user.branchIds || [],
      active: user.active !== false
    });
    setShowEditModal(true);
  };

  const handleUpdateUser = async (e) => {
    e.preventDefault();
    if (editFormData.role !== 'ADMIN' && editFormData.branchIds.length === 0) {
      alert('Chọn ít nhất một chi nhánh cho tài khoản');
      return;
    }
    try {
      await userService.updateUser(editFormData.id, {
        role: editFormData.role,
        branchIds: editFormData.branchIds,
        active: editFormData.active
      });
      alert('Cập nhật tài khoản thành công!');
      setShowEditModal(false);
      fetchUsers();
    } catch (err) {
      alert(err.response?.data?.message || 'Có lỗi xảy ra khi cập nhật');
    }
  };

  const filteredUsers = users.filter((u) => {
    const matchSearch = u.username.toLowerCase().includes(searchTerm.toLowerCase());
    const matchRole = filterRole === 'ALL' || u.role === filterRole;
    return matchSearch && matchRole;
  });

  const columns = [
    { key: 'id', title: 'ID', width: '60px' },
    {
      key: 'username',
      title: 'Tên đăng nhập',
      render: (val, u) => <strong style={{ color: 'var(--text-main)' }}>{val || u?.username}</strong>
    },
    {
      key: 'role',
      title: 'Vai trò',
      render: (val, u) => {
        const role = val || u?.role;
        let variant = 'secondary';
        let label = 'Thu ngân';
        if (role === 'ADMIN') {
          variant = 'danger';
          label = 'Quản trị viên';
        } else if (role === 'MANAGER') {
          variant = 'brand';
          label = 'Quản lý';
        } else if (role === 'WAREHOUSE') {
          variant = 'warning';
          label = 'Thủ kho';
        }
        return <Badge variant={variant}>{label}</Badge>;
      }
    },
    {
      key: 'branchIds',
      title: 'Chi nhánh được phân công',
      render: (val, u) =>
        u?.role === 'ADMIN'
          ? 'Tất cả'
          : (val || []).map((id) => branchNameOf(branches, id)).join(', ') || 'Chưa phân công'
    },
    {
      key: 'active',
      title: 'Trạng thái',
      render: (val, u) => {
        const active = val !== undefined ? val : u?.active;
        return (
          <Badge variant={active ? 'success' : 'secondary'}>
            {active ? 'Đang hoạt động' : 'Đã khóa'}
          </Badge>
        );
      }
    },
    {
      key: 'createdAt',
      title: 'Ngày tạo',
      render: (val, u) => {
        const dt = val || u?.createdAt;
        return dt ? formatDateTime(dt) : '—';
      }
    },
    {
      key: 'actions',
      title: 'Thao tác',
      render: (_, u) => (
        <div className="staff-actions-cell">
          <Button
            size="sm"
            variant="ghost"
            onClick={() => handleOpenEdit(u)}
            title="Chỉnh sửa tài khoản"
          >
            Sửa
          </Button>
          <Button
            size="sm"
            variant="ghost"
            onClick={() => {
              setSelectedUser(u);
              setShowPasswordModal(true);
            }}
            title="Đổi mật khẩu tài khoản"
          >
            Mật khẩu
          </Button>
          <Button
            size="sm"
            variant={u.active ? 'warning' : 'success'}
            onClick={() => handleToggleStatus(u)}
            title={u.active ? 'Khóa tài khoản' : 'Mở khóa'}
          >
            {u.active ? 'Khóa' : 'Kích hoạt'}
          </Button>
        </div>
      )
    }
  ];

  return (
    <div className="staff-page">
      {/* Header */}
      <header className="page-header">
        <h1 className="page-title">Tài khoản</h1>
        <span className="page-subtitle">Tài khoản đăng nhập và phân quyền theo vai trò</span>
      </header>

      {/* Content */}
      <div className="staff-content">
        <div className="staff-toolbar">
          <div className="staff-toolbar-left">
            <input
              type="text"
              placeholder="Tìm tên đăng nhập..."
              className="staff-search-input"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
            />
            <select
              className="staff-select"
              value={filterRole}
              onChange={(e) => setFilterRole(e.target.value)}
            >
              <option value="ALL">Tất cả vai trò</option>
              <option value="ADMIN">Quản trị viên (ADMIN)</option>
              <option value="MANAGER">Quản lý (MANAGER)</option>
              <option value="CASHIER">Thu ngân (CASHIER)</option>
              <option value="WAREHOUSE">Thủ kho (WAREHOUSE)</option>
            </select>
          </div>
          <div>
            <Button variant="primary" onClick={() => setShowAddModal(true)}>
              Thêm tài khoản
            </Button>
          </div>
        </div>

        <div className="staff-table-card">
          <Table
            columns={columns}
            data={filteredUsers}
            loading={loading}
            emptyMessage="Không tìm thấy tài khoản nào phù hợp"
          />
        </div>
      </div>

      {/* Modal Add User */}
      <Modal
        isOpen={showAddModal}
        onClose={() => setShowAddModal(false)}
        title="Thêm tài khoản mới"
        width="460px"
      >
        <form onSubmit={handleCreateUser}>
          <div className="staff-form-group">
            <label>Tên đăng nhập *</label>
            <input
              type="text"
              className="staff-form-input"
              placeholder="vd: quay1, quanly_q1"
              value={formData.username}
              onChange={(e) => setFormData({ ...formData, username: e.target.value })}
              required
            />
          </div>

          <div className="staff-form-group">
            <label>Mật khẩu ban đầu (từ 6 ký tự) *</label>
            <input
              type="password"
              className="staff-form-input"
              placeholder="••••••••"
              value={formData.password}
              onChange={(e) => setFormData({ ...formData, password: e.target.value })}
              required
            />
          </div>

          <div className="staff-form-group">
            <label>Vai trò *</label>
            <select
              className="staff-form-select"
              value={formData.role}
              onChange={(e) => setFormData({ ...formData, role: e.target.value })}
            >
              <option value="CASHIER">CASHIER (Thu ngân bán hàng tại quầy)</option>
              <option value="MANAGER">MANAGER (Quản lý chi nhánh)</option>
              <option value="WAREHOUSE">WAREHOUSE (Thủ kho)</option>
              <option value="ADMIN">ADMIN (Quản trị viên toàn quyền)</option>
            </select>
          </div>

          <div className="staff-form-group">
            <label>Chi nhánh làm việc</label>
            <BranchAssignment
              role={formData.role}
              branches={branches}
              value={formData.branchIds}
              onChange={(ids) => setFormData({ ...formData, branchIds: ids })}
            />
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '20px' }}>
            <Button type="button" variant="ghost" onClick={() => setShowAddModal(false)}>
              Hủy
            </Button>
            <Button type="submit" variant="primary">
              Lưu tài khoản
            </Button>
          </div>
        </form>
      </Modal>

      {/* Modal Edit User */}
      <Modal
        isOpen={showEditModal}
        onClose={() => setShowEditModal(false)}
        title={`Chỉnh sửa tài khoản "${editFormData.username}"`}
        width="460px"
      >
        <form onSubmit={handleUpdateUser}>
          <div className="staff-form-group">
            <label>Tên đăng nhập</label>
            <input
              type="text"
              className="staff-form-input"
              value={editFormData.username}
              disabled
              style={{ opacity: 0.7, cursor: 'not-allowed' }}
            />
          </div>

          <div className="staff-form-group">
            <label>Vai trò *</label>
            <select
              className="staff-form-select"
              value={editFormData.role}
              onChange={(e) => setEditFormData({ ...editFormData, role: e.target.value })}
            >
              <option value="CASHIER">CASHIER (Thu ngân bán hàng tại quầy)</option>
              <option value="MANAGER">MANAGER (Quản lý chi nhánh)</option>
              <option value="WAREHOUSE">WAREHOUSE (Thủ kho)</option>
              <option value="ADMIN">ADMIN (Quản trị viên toàn quyền)</option>
            </select>
          </div>

          <div className="staff-form-group">
            <label>Chi nhánh làm việc</label>
            <BranchAssignment
              role={editFormData.role}
              branches={branches}
              value={editFormData.branchIds}
              onChange={(ids) => setEditFormData({ ...editFormData, branchIds: ids })}
            />
          </div>

          <div className="staff-form-group" style={{ display: 'flex', alignItems: 'center', gap: '8px', marginTop: '12px' }}>
            <input
              type="checkbox"
              id="edit-active-checkbox"
              checked={editFormData.active}
              onChange={(e) => setEditFormData({ ...editFormData, active: e.target.checked })}
              disabled={editFormData.username === 'admin'}
            />
            <label htmlFor="edit-active-checkbox" style={{ margin: 0, cursor: 'pointer' }}>
              Tài khoản đang hoạt động
            </label>
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '20px' }}>
            <Button type="button" variant="ghost" onClick={() => setShowEditModal(false)}>
              Hủy
            </Button>
            <Button type="submit" variant="primary">
              Lưu thay đổi
            </Button>
          </div>
        </form>
      </Modal>

      {/* Modal Change Password */}
      <Modal
        isOpen={showPasswordModal}
        onClose={() => setShowPasswordModal(false)}
        title={`Đổi mật khẩu cho "${selectedUser?.username}"`}
        width="400px"
      >
        <form onSubmit={handleChangePassword}>
          <div className="staff-form-group">
            <label>Mật khẩu mới (tối thiểu 6 ký tự) *</label>
            <input
              type="password"
              className="staff-form-input"
              placeholder="••••••••"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              required
            />
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '20px' }}>
            <Button type="button" variant="ghost" onClick={() => setShowPasswordModal(false)}>
              Hủy
            </Button>
            <Button type="submit" variant="primary">
              Cập nhật mật khẩu
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
