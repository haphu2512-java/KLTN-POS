import { useState } from 'react';
import { Modal, Button, Input } from '../../components/common';
import { productService } from '../../services/productService';

const EMPTY = { barcode: '', name: '', category: '', unit: '', costPrice: '', salePrice: '', imageUrl: '' };

/**
 * Thêm / sửa sản phẩm (ADMIN, MANAGER, WAREHOUSE). Sửa thì có thêm nút ngừng kinh doanh (xóa mềm).
 * product = null là thêm mới.
 */
export default function ProductFormModal({ isOpen, onClose, product = null, categories = [], onSaved }) {
  const isEdit = Boolean(product?.id);
  const [form, setForm] = useState(EMPTY);
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);

  const [prevKey, setPrevKey] = useState(null);
  const key = isOpen ? `open-${product?.id ?? 'new'}` : null;
  if (key !== prevKey) {
    setPrevKey(key);
    if (isOpen) {
      setForm(
        product
          ? {
              barcode: product.barcode || '',
              name: product.name || '',
              category: product.category || '',
              unit: product.unit || '',
              costPrice: product.costPrice ?? '',
              salePrice: product.salePrice ?? '',
              imageUrl: product.imageUrl || ''
            }
          : EMPTY
      );
      setError('');
    }
  }

  const set = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }));

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!form.name.trim()) return setError('Nhập tên sản phẩm.');
    if (form.salePrice === '' || Number(form.salePrice) < 0) return setError('Nhập giá bán hợp lệ.');
    if (form.costPrice !== '' && Number(form.costPrice) < 0) return setError('Giá vốn không được âm.');
    setSaving(true);
    setError('');
    const payload = {
      barcode: form.barcode.trim() || undefined,
      name: form.name.trim(),
      category: form.category.trim() || undefined,
      unit: form.unit.trim() || undefined,
      costPrice: form.costPrice === '' ? undefined : Number(form.costPrice),
      salePrice: Number(form.salePrice),
      imageUrl: form.imageUrl.trim()
    };
    try {
      const saved = isEdit
        ? await productService.updateProduct(product.id, payload)
        : await productService.createProduct(payload);
      onSaved?.(saved, isEdit ? 'updated' : 'created');
    } catch (err) {
      setError(err.response?.data?.message || 'Không lưu được sản phẩm.');
    } finally {
      setSaving(false);
    }
  };

  const handleDeactivate = async () => {
    if (!window.confirm(`Ngừng kinh doanh "${product.name}"? Sản phẩm sẽ ẩn khỏi danh mục bán hàng, lịch sử bán vẫn giữ.`)) return;
    setSaving(true);
    try {
      await productService.deleteProduct(product.id);
      onSaved?.(product, 'deleted');
    } catch (err) {
      setError(err.response?.data?.message || 'Không ngừng kinh doanh được sản phẩm.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={isEdit ? `Sửa sản phẩm: ${product.name}` : 'Thêm sản phẩm mới'}
      maxWidth="560px"
      footer={
        <div style={{ display: 'flex', width: '100%', justifyContent: 'space-between', gap: '8px' }}>
          <div>
            {isEdit && (
              <Button variant="danger" onClick={handleDeactivate} disabled={saving}>
                Ngừng kinh doanh
              </Button>
            )}
          </div>
          <div style={{ display: 'flex', gap: '8px' }}>
            <Button variant="secondary" onClick={onClose}>Hủy</Button>
            <Button variant="primary" type="submit" form="product-form" disabled={saving}>
              {saving ? 'Đang lưu...' : isEdit ? 'Lưu thay đổi' : 'Thêm sản phẩm'}
            </Button>
          </div>
        </div>
      }
    >
      <form id="product-form" onSubmit={handleSubmit} style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
        <div style={{ gridColumn: '1 / -1' }}>
          <Input label="Tên sản phẩm *" value={form.name} onChange={set('name')} autoFocus required />
        </div>
        <Input
          label="Mã vạch"
          value={form.barcode}
          onChange={set('barcode')}
          placeholder={isEdit ? '' : 'Bỏ trống để hệ thống tự sinh'}
        />
        <Input label="Danh mục" value={form.category} onChange={set('category')} list="product-categories" />
        <datalist id="product-categories">
          {categories.map((c) => <option key={c} value={c} />)}
        </datalist>
        <Input label="Đơn vị tính" value={form.unit} onChange={set('unit')} placeholder="Hộp, Gói, Chai..." />
        <Input label="Link ảnh" value={form.imageUrl} onChange={set('imageUrl')} placeholder="https://..." />
        <Input label="Giá vốn (₫)" type="number" min="0" value={form.costPrice} onChange={set('costPrice')} />
        <Input label="Giá bán (₫) *" type="number" min="0" value={form.salePrice} onChange={set('salePrice')} required />
        {error && (
          <div style={{ gridColumn: '1 / -1', color: 'var(--danger-700, #b91c1c)', fontSize: '13px' }}>{error}</div>
        )}
      </form>
    </Modal>
  );
}
