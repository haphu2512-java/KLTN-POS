import { useEffect, useMemo, useState } from 'react';
import { Button, Table, Badge, Modal } from '../../components/common';
import { promotionService } from '../../services/promotionService';
import { productService } from '../../services/productService';
import { formatCurrency, formatDateTime } from '../../utils/formatters';
import { PERMS } from '../../utils/permissions';
import useBranches, { branchNameOf } from '../../hooks/useBranches';
import './PromotionPage.css';

const EMPTY_FORM = {
  name: '',
  description: '',
  type: 'PERCENT',
  value: '',
  maxDiscountAmount: '',
  minOrderAmount: '',
  category: '',
  memberOnly: false,
  minCustomerPoints: '',
  branchId: '',
  startAt: '',
  endAt: '',
  active: true
};

/** Trạng thái hiển thị của chương trình theo bật/tắt và thời gian hiệu lực */
function statusOf(p, now = new Date()) {
  if (!p.active) return { label: 'Đang tắt', variant: 'neutral' };
  if (p.startAt && new Date(p.startAt) > now) return { label: 'Sắp diễn ra', variant: 'warning' };
  if (p.endAt && new Date(p.endAt) <= now) return { label: 'Đã kết thúc', variant: 'danger' };
  return { label: 'Đang áp dụng', variant: 'success' };
}

function describeValue(p) {
  if (p.type === 'PERCENT') {
    const cap = p.maxDiscountAmount ? `, tối đa ${formatCurrency(p.maxDiscountAmount)}` : '';
    return `Giảm ${Number(p.value)}%${cap}`;
  }
  return `Giảm ${formatCurrency(p.value)}`;
}

function describeConditions(p) {
  const parts = [];
  parts.push(p.category ? `Danh mục: ${p.category}` : 'Cả đơn hàng');
  if (p.minOrderAmount) parts.push(`Đơn từ ${formatCurrency(p.minOrderAmount)}`);
  if (p.minCustomerPoints) parts.push(`Thành viên từ ${p.minCustomerPoints} điểm`);
  else if (p.memberOnly) parts.push('Chỉ khách thành viên');
  return parts.join(' · ');
}

const toInputDateTime = (v) => (v ? String(v).slice(0, 16) : '');
const numOrNull = (v) => (v === '' || v == null ? null : Number(v));

export default function PromotionPage({ isOnline = true, can = () => true, branchId }) {
  const canManage = can(PERMS.PROMOTION_MANAGE);
  const branches = useBranches(branchId ? [{ id: branchId, name: `Chi nhánh ${branchId}` }] : []);
  const [promotions, setPromotions] = useState([]);
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(false);
  const [search, setSearch] = useState('');
  const [filter, setFilter] = useState('ALL'); // ALL | RUNNING | OFF
  const [editing, setEditing] = useState(null); // null: đóng; { id?: number }
  const [form, setForm] = useState(EMPTY_FORM);
  const [saving, setSaving] = useState(false);

  const load = async () => {
    setLoading(true);
    try {
      setPromotions(await promotionService.getAll());
    } catch (err) {
      console.error('Lỗi khi tải khuyến mãi:', err);
      setPromotions([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    // Tải lại khi đổi trạng thái kết nối là chủ ý (hàm load tự bật cờ loading)
    // eslint-disable-next-line react-hooks/set-state-in-effect
    load();
  }, [isOnline]);

  // Gợi ý danh mục từ danh sách sản phẩm
  useEffect(() => {
    if (!canManage) return;
    let alive = true;
    productService
      .getProducts()
      .then((res) => {
        const list = Array.isArray(res) ? res : res?.data || [];
        const unique = [...new Set(list.map((p) => p.category).filter(Boolean))].sort((a, b) => a.localeCompare(b, 'vi'));
        if (alive) setCategories(unique);
      })
      .catch(() => {});
    return () => {
      alive = false;
    };
  }, [canManage]);

  const filtered = useMemo(() => {
    const term = search.trim().toLowerCase();
    return promotions.filter((p) => {
      if (term && !`${p.name} ${p.description || ''} ${p.category || ''}`.toLowerCase().includes(term)) return false;
      const st = statusOf(p).label;
      if (filter === 'RUNNING') return st === 'Đang áp dụng';
      if (filter === 'OFF') return st !== 'Đang áp dụng';
      return true;
    });
  }, [promotions, search, filter]);

  const openCreate = () => {
    setForm(EMPTY_FORM);
    setEditing({});
  };

  const openEdit = (p) => {
    setForm({
      name: p.name || '',
      description: p.description || '',
      type: p.type || 'PERCENT',
      value: p.value ?? '',
      maxDiscountAmount: p.maxDiscountAmount ?? '',
      minOrderAmount: p.minOrderAmount ?? '',
      category: p.category || '',
      memberOnly: !!p.memberOnly,
      minCustomerPoints: p.minCustomerPoints ?? '',
      branchId: p.branchId ?? '',
      startAt: toInputDateTime(p.startAt),
      endAt: toInputDateTime(p.endAt),
      active: p.active !== false
    });
    setEditing({ id: p.id });
  };

  const handleSave = async (e) => {
    e.preventDefault();
    if (!form.name.trim()) {
      alert('Vui lòng nhập tên chương trình');
      return;
    }
    const value = Number(form.value);
    if (!value || value <= 0) {
      alert('Mức giảm phải lớn hơn 0');
      return;
    }
    if (form.type === 'PERCENT' && value > 100) {
      alert('Giảm theo % không được vượt 100%');
      return;
    }
    const payload = {
      name: form.name.trim(),
      description: form.description.trim() || null,
      type: form.type,
      value,
      maxDiscountAmount: form.type === 'PERCENT' ? numOrNull(form.maxDiscountAmount) : null,
      minOrderAmount: numOrNull(form.minOrderAmount),
      category: form.category.trim() || null,
      memberOnly: form.memberOnly,
      minCustomerPoints: numOrNull(form.minCustomerPoints),
      branchId: numOrNull(form.branchId),
      startAt: form.startAt || null,
      endAt: form.endAt || null,
      active: form.active
    };
    setSaving(true);
    try {
      if (editing?.id) {
        await promotionService.update(editing.id, payload);
      } else {
        await promotionService.create(payload);
      }
      setEditing(null);
      load();
    } catch (err) {
      alert(err.response?.data?.message || 'Không lưu được khuyến mãi');
    } finally {
      setSaving(false);
    }
  };

  const handleToggle = async (p) => {
    try {
      const updated = await promotionService.setActive(p.id, !p.active);
      setPromotions((prev) => prev.map((x) => (x.id === p.id ? updated : x)));
    } catch (err) {
      alert(err.response?.data?.message || 'Không đổi được trạng thái');
    }
  };

  const handleDelete = async (p) => {
    if (!window.confirm(`Xóa chương trình "${p.name}"?\nHóa đơn đã bán vẫn giữ tên và số tiền khuyến mãi.`)) return;
    try {
      await promotionService.remove(p.id);
      setPromotions((prev) => prev.filter((x) => x.id !== p.id));
    } catch (err) {
      alert(err.response?.data?.message || 'Không xóa được khuyến mãi');
    }
  };

  const columns = [
    {
      key: 'name',
      title: 'Chương trình',
      render: (val, p) => (
        <div>
          <strong style={{ color: 'var(--text-main)' }}>{val}</strong>
          {p.description && <div className="promo-muted">{p.description}</div>}
        </div>
      )
    },
    { key: 'value', title: 'Mức giảm', render: (_, p) => describeValue(p) },
    { key: 'conditions', title: 'Điều kiện', render: (_, p) => <span className="promo-muted">{describeConditions(p)}</span> },
    {
      key: 'branchId',
      title: 'Chi nhánh',
      render: (val) => (val ? branchNameOf(branches, val) : 'Mọi chi nhánh')
    },
    {
      key: 'period',
      title: 'Thời gian',
      render: (_, p) => (
        <span className="promo-muted">
          {p.startAt ? formatDateTime(p.startAt) : 'Từ nay'} → {p.endAt ? formatDateTime(p.endAt) : 'Không hạn'}
        </span>
      )
    },
    {
      key: 'status',
      title: 'Trạng thái',
      render: (_, p) => {
        const st = statusOf(p);
        return <Badge variant={st.variant}>{st.label}</Badge>;
      }
    },
    ...(canManage
      ? [
          {
            key: 'actions',
            title: '',
            width: '220px',
            align: 'right',
            render: (_, p) => (
              <div className="promo-actions">
                <Button size="sm" variant="secondary" onClick={() => handleToggle(p)} disabled={!isOnline}>
                  {p.active ? 'Tắt' : 'Bật'}
                </Button>
                <Button size="sm" variant="secondary" onClick={() => openEdit(p)} disabled={!isOnline}>
                  Sửa
                </Button>
                <Button size="sm" variant="ghost" onClick={() => handleDelete(p)} disabled={!isOnline}>
                  Xóa
                </Button>
              </div>
            )
          }
        ]
      : [])
  ];

  const set = (patch) => setForm((prev) => ({ ...prev, ...patch }));

  return (
    <div className="promo-page">
      <header className="page-header">
        <h1 className="page-title">Khuyến mãi</h1>
        <span className="page-subtitle">
          {canManage
            ? 'Tạo và bật/tắt chương trình giảm giá; máy bán hàng tự chọn khuyến mãi có lợi nhất cho khách'
            : 'Các chương trình giảm giá đang có (chỉ quản trị viên được sửa)'}
        </span>
      </header>

      <div className="promo-content">
        <div className="promo-toolbar">
          <div className="promo-toolbar-left">
            <input
              type="text"
              className="promo-search-input"
              placeholder="Tìm tên, mô tả, danh mục..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
            <select className="promo-select" value={filter} onChange={(e) => setFilter(e.target.value)}>
              <option value="ALL">Tất cả</option>
              <option value="RUNNING">Đang áp dụng</option>
              <option value="OFF">Tắt / hết hạn / sắp diễn ra</option>
            </select>
          </div>
          {canManage && (
            <Button variant="primary" onClick={openCreate} disabled={!isOnline}>
              Thêm khuyến mãi
            </Button>
          )}
        </div>

        <div className="promo-table-card">
          <Table
            columns={columns}
            data={filtered}
            loading={loading}
            loadingText="Đang tải khuyến mãi..."
            emptyMessage={promotions.length ? 'Không có chương trình phù hợp' : 'Chưa có chương trình khuyến mãi nào'}
          />
        </div>
      </div>

      <Modal
        isOpen={!!editing}
        onClose={() => !saving && setEditing(null)}
        title={editing?.id ? 'Sửa khuyến mãi' : 'Thêm khuyến mãi'}
        maxWidth="560px"
      >
        <form onSubmit={handleSave}>
          <div className="promo-form-group">
            <label>Tên chương trình *</label>
            <input
              className="promo-input"
              value={form.name}
              onChange={(e) => set({ name: e.target.value })}
              placeholder="vd: Giảm 10% sữa cuối tuần"
              maxLength={100}
              required
            />
          </div>
          <div className="promo-form-group">
            <label>Mô tả</label>
            <input
              className="promo-input"
              value={form.description}
              onChange={(e) => set({ description: e.target.value })}
              placeholder="Hiện cho thu ngân khi chọn khuyến mãi"
              maxLength={255}
            />
          </div>

          <div className="promo-form-row">
            <div className="promo-form-group">
              <label>Loại giảm *</label>
              <select className="promo-input" value={form.type} onChange={(e) => set({ type: e.target.value })}>
                <option value="PERCENT">Theo %</option>
                <option value="FIXED_AMOUNT">Số tiền cố định</option>
              </select>
            </div>
            <div className="promo-form-group">
              <label>{form.type === 'PERCENT' ? 'Mức giảm (%) *' : 'Số tiền giảm (₫) *'}</label>
              <input
                type="number"
                className="promo-input"
                min="0"
                max={form.type === 'PERCENT' ? 100 : undefined}
                step="any"
                value={form.value}
                onChange={(e) => set({ value: e.target.value })}
                required
              />
            </div>
            {form.type === 'PERCENT' && (
              <div className="promo-form-group">
                <label>Giảm tối đa (₫)</label>
                <input
                  type="number"
                  className="promo-input"
                  min="0"
                  value={form.maxDiscountAmount}
                  onChange={(e) => set({ maxDiscountAmount: e.target.value })}
                  placeholder="Không giới hạn"
                />
              </div>
            )}
          </div>

          <div className="promo-form-row">
            <div className="promo-form-group">
              <label>Áp dụng cho danh mục</label>
              <input
                className="promo-input"
                list="promo-categories"
                value={form.category}
                onChange={(e) => set({ category: e.target.value })}
                placeholder="Để trống: cả đơn hàng"
                maxLength={150}
              />
              <datalist id="promo-categories">
                {categories.map((c) => (
                  <option key={c} value={c} />
                ))}
              </datalist>
            </div>
            <div className="promo-form-group">
              <label>Tiền hàng tối thiểu (₫)</label>
              <input
                type="number"
                className="promo-input"
                min="0"
                value={form.minOrderAmount}
                onChange={(e) => set({ minOrderAmount: e.target.value })}
                placeholder="Không yêu cầu"
              />
            </div>
          </div>

          <div className="promo-form-row">
            <div className="promo-form-group">
              <label>Chi nhánh</label>
              <select className="promo-input" value={form.branchId} onChange={(e) => set({ branchId: e.target.value })}>
                <option value="">Mọi chi nhánh</option>
                {branches.map((b) => (
                  <option key={b.id} value={b.id}>
                    {b.name}
                  </option>
                ))}
              </select>
            </div>
            <div className="promo-form-group">
              <label>Bắt đầu</label>
              <input
                type="datetime-local"
                className="promo-input"
                value={form.startAt}
                onChange={(e) => set({ startAt: e.target.value })}
              />
            </div>
            <div className="promo-form-group">
              <label>Kết thúc</label>
              <input
                type="datetime-local"
                className="promo-input"
                value={form.endAt}
                onChange={(e) => set({ endAt: e.target.value })}
              />
            </div>
          </div>

          <div className="promo-form-row">
            <label className="promo-check">
              <input
                type="checkbox"
                checked={form.memberOnly}
                onChange={(e) => set({ memberOnly: e.target.checked })}
              />
              Chỉ dành cho khách thành viên
            </label>
            <div className="promo-form-group">
              <label>Điểm tích lũy tối thiểu</label>
              <input
                type="number"
                className="promo-input"
                min="0"
                value={form.minCustomerPoints}
                onChange={(e) => set({ minCustomerPoints: e.target.value })}
                placeholder="Không yêu cầu"
              />
            </div>
          </div>

          <label className="promo-check">
            <input type="checkbox" checked={form.active} onChange={(e) => set({ active: e.target.checked })} />
            Bật chương trình
          </label>

          <div className="promo-form-footer">
            <Button type="button" variant="ghost" onClick={() => setEditing(null)} disabled={saving}>
              Hủy
            </Button>
            <Button type="submit" variant="primary" disabled={saving}>
              {saving ? 'Đang lưu...' : 'Lưu khuyến mãi'}
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
