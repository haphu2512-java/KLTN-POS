import { useMemo, useState } from 'react';

const fold = (s) =>
  (s || '')
    .toLowerCase()
    .normalize('NFD')
    .replace(/\p{M}/gu, '')
    .replace(/đ/g, 'd')
    .trim();

/**
 * Chọn nhiều danh mục sản phẩm mà nhà cung cấp bán. Danh mục lấy từ sản phẩm đang có;
 * gõ tên chưa có trong danh sách rồi Enter để thêm danh mục mới.
 */
export default function SupplierCategoryPicker({ options = [], value = [], onChange }) {
  const [query, setQuery] = useState('');
  const selected = useMemo(() => new Set(value.map(fold)), [value]);

  const filtered = useMemo(() => {
    const q = fold(query);
    return options.filter((c) => !q || fold(c).includes(q));
  }, [options, query]);

  const toggle = (c) => {
    if (selected.has(fold(c))) onChange(value.filter((v) => fold(v) !== fold(c)));
    else onChange([...value, c]);
  };

  const addTyped = () => {
    const v = query.trim();
    if (!v) return;
    const existing = options.find((c) => fold(c) === fold(v));
    if (!selected.has(fold(v))) onChange([...value, existing || v]);
    setQuery('');
  };

  const exact = options.some((c) => fold(c) === fold(query));

  return (
    <div className="supplier-cat-picker">
      {value.length > 0 ? (
        <div className="supplier-cat-chips">
          {value.map((c) => (
            <span key={c} className="supplier-cat-chip">
              {c}
              <button type="button" aria-label={`Bỏ ${c}`} onClick={() => toggle(c)}>×</button>
            </span>
          ))}
        </div>
      ) : (
        <p className="supplier-cat-hint">
          Chưa chọn danh mục nào. Khi lập đơn từ đề xuất AI, nhà cung cấp này sẽ không nhận mặt hàng nào.
        </p>
      )}
      <input
        type="text"
        className="partner-form-input"
        placeholder="Tìm danh mục, hoặc gõ tên mới rồi Enter"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === 'Enter') {
            e.preventDefault();
            addTyped();
          }
        }}
      />
      <div className="supplier-cat-list" role="listbox" aria-multiselectable="true">
        {filtered.map((c) => (
          <label key={c} className="supplier-cat-option">
            <input type="checkbox" checked={selected.has(fold(c))} onChange={() => toggle(c)} />
            <span>{c}</span>
          </label>
        ))}
        {query.trim() && !exact && (
          <button type="button" className="supplier-cat-add" onClick={addTyped}>
            + Thêm danh mục “{query.trim()}”
          </button>
        )}
        {filtered.length === 0 && !query.trim() && (
          <p className="supplier-cat-hint">Chưa có danh mục sản phẩm nào.</p>
        )}
      </div>
    </div>
  );
}
