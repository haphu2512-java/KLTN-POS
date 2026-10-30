import { useEffect, useState } from 'react';
import { Modal, Button, Input, Table, Badge } from '../../components/common';
import { branchService } from '../../services/branchService';
import { invalidateBranches } from '../../hooks/useBranches';

const EMPTY = { name: '', address: '', phone: '' };

/** Quản lý chi nhánh (ADMIN): thêm, sửa thông tin, ngừng / mở lại hoạt động */
export default function BranchManager() {
  const [branches, setBranches] = useState([]);
  const [loading, setLoading] = useState(true);
  const [reloadKey, setReloadKey] = useState(0);
  const [editing, setEditing] = useState(null); // null: đóng; {}: thêm mới; branch: sửa
  const [form, setForm] = useState(EMPTY);
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let alive = true;
    branchService
      .getAllBranches()
      .then((list) => alive && setBranches(list))
      .catch(() => alive && setBranches([]))
      .finally(() => alive && setLoading(false));
    return () => {
      alive = false;
    };
  }, [reloadKey]);

  const openEditor = (branch) => {
    setEditing(branch || {});
    setForm(branch ? { name: branch.name || '', address: branch.address || '', phone: branch.phone || '' } : EMPTY);
    setError('');
  };

  const afterChange = () => {
    invalidateBranches();
    setReloadKey((k) => k + 1);
  };

  const handleSave = async (e) => {
    e.preventDefault();
    if (!form.name.trim()) return setError('Nhập tên chi nhánh.');
    setSaving(true);
    try {
      if (editing?.id) {
        await branchService.updateBranch(editing.id, { ...form, active: editing.active });
      } else {
        await branchService.createBranch(form);
      }
      setEditing(null);
      afterChange();
    } catch (err) {
      setError(err.response?.data?.message || 'Không lưu được chi nhánh.');
    } finally {
      setSaving(false);
    }
  };

  const toggleActive = async (branch) => {
    const next = !branch.active;
    if (!next && !window.confirm(`Ngừng hoạt động "${branch.name}"? Nhân viên sẽ không chọn được chi nhánh này khi đăng nhập.`)) return;
    try {
      await branchService.updateBranch(branch.id, { name: branch.name, address: branch.address, phone: branch.phone, active: next });
      afterChange();
    } catch (err) {
      alert(err.response?.data?.message || 'Không cập nhật được chi nhánh.');
    }
  };

  const columns = [
    { key: 'id', title: 'Mã', width: '70px' },
    { key: 'name', title: 'Tên chi nhánh', render: (val) => <strong>{val}</strong> },
    { key: 'address', title: 'Địa chỉ', render: (val) => val || '—' },
    { key: 'phone', title: 'Điện thoại', width: '130px', render: (val) => val || '—' },
    {
      key: 'active',
      title: 'Trạng thái',
      width: '130px',
      align: 'center',
      render: (val) => <Badge variant={val ? 'success' : 'neutral'}>{val ? 'Đang hoạt động' : 'Ngừng hoạt động'}</Badge>
    },
    {
      key: 'actions',
      title: 'Thao tác',
      width: '190px',
      align: 'center',
      render: (_, row) => (
        <div style={{ display: 'flex', gap: '6px', justifyContent: 'center' }}>
          <Button size="sm" variant="ghost" onClick={() => openEditor(row)}>Sửa</Button>
          <Button size="sm" variant={row.active ? 'danger' : 'secondary'} onClick={() => toggleActive(row)}>
            {row.active ? 'Ngừng' : 'Mở lại'}
          </Button>
        </div>
      )
    }
  ];

  return (
    <section style={{ display: 'flex', flexDirection: 'column', gap: '12px', marginTop: '24px' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <h2 style={{ margin: 0, fontSize: '16px' }}>Chi nhánh</h2>
        <Button variant="primary" onClick={() => openEditor(null)}>Thêm chi nhánh</Button>
      </div>
      <div className="staff-table-card">
        <Table columns={columns} data={branches} loading={loading} emptyMessage="Chưa có chi nhánh nào" />
      </div>

      <Modal
        isOpen={editing !== null}
        onClose={() => setEditing(null)}
        title={editing?.id ? `Sửa chi nhánh: ${editing.name}` : 'Thêm chi nhánh'}
        maxWidth="460px"
        footer={
          <>
            <Button variant="secondary" onClick={() => setEditing(null)}>Hủy</Button>
            <Button variant="primary" type="submit" form="branch-form" disabled={saving}>
              {saving ? 'Đang lưu...' : 'Lưu'}
            </Button>
          </>
        }
      >
        <form id="branch-form" onSubmit={handleSave} style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          <Input label="Tên chi nhánh *" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} autoFocus />
          <Input label="Địa chỉ" value={form.address} onChange={(e) => setForm({ ...form, address: e.target.value })} />
          <Input label="Điện thoại" value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} />
          {error && <div style={{ color: 'var(--danger-700, #b91c1c)', fontSize: '13px' }}>{error}</div>}
        </form>
      </Modal>
    </section>
  );
}
