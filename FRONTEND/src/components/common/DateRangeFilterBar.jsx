import Button from './Button';
import { DATE_PRESETS, formatDateOnly } from '../../hooks/useDateRangeFilter';

/**
 * Thanh lọc dùng chung cho danh sách đơn / phiếu: khoảng ngày, từ khóa, trạng thái.
 * @param filter        kết quả của useDateRangeFilter
 * @param statusOptions [{ value, label }]
 * @param noun          "đơn" | "phiếu"
 * @param openLabel     mô tả bản ghi đang mở luôn hiện, vd. "đơn chưa nhận xong"
 */
export default function DateRangeFilterBar({ filter, statusOptions, placeholder, noun = 'đơn', openLabel }) {
  const {
    preset, changePreset, fromDate, toDate, setFromDate, setToDate,
    keyword, setKeyword, status, setStatus, filtered, openCarried, isFiltered, reset, total,
  } = filter;
  const small = { padding: '4px 6px', fontSize: '12px', height: '32px' };

  return (
    <div style={{ padding: '12px', borderBottom: '1px solid var(--border-subtle)', display: 'flex', flexDirection: 'column', gap: '8px', background: 'var(--bg-surface)' }}>
      <div style={{ display: 'flex', gap: '6px', alignItems: 'center', flexWrap: 'wrap' }}>
        <select
          className="mp-select"
          value={preset}
          onChange={(e) => changePreset(e.target.value)}
          style={{ fontSize: '13px', padding: '6px 8px', height: '32px' }}
          aria-label="Chọn khoảng ngày"
        >
          {DATE_PRESETS.map((p) => <option key={p.value} value={p.value}>{p.label}</option>)}
        </select>

        <div style={{ display: 'flex', alignItems: 'center', gap: '4px', flex: 1, minWidth: '200px' }}>
          <input
            type="date"
            className="mp-input"
            style={{ ...small, flex: 1 }}
            value={fromDate}
            max={toDate || undefined}
            title="Từ ngày"
            aria-label="Từ ngày"
            onChange={(e) => setFromDate(e.target.value)}
          />
          <span className="mp-muted" style={{ padding: '0 2px' }}>–</span>
          <input
            type="date"
            className="mp-input"
            style={{ ...small, flex: 1 }}
            value={toDate}
            min={fromDate || undefined}
            title="Đến ngày"
            aria-label="Đến ngày"
            onChange={(e) => setToDate(e.target.value)}
          />
        </div>

        {isFiltered && (
          <Button
            size="sm"
            variant="ghost"
            style={{ height: '32px', padding: '0 8px', fontSize: '12px', color: 'var(--text-muted)' }}
            onClick={reset}
            title="Bỏ lọc, về mặc định"
          >
            ✕ Đặt lại
          </Button>
        )}
      </div>

      <div style={{ display: 'flex', gap: '6px', alignItems: 'center' }}>
        <input
          type="text"
          className="mp-input"
          style={{ flex: 1, padding: '5px 8px', fontSize: '12px', height: '32px' }}
          placeholder={placeholder}
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          aria-label={`Tìm kiếm ${noun}`}
        />
        <select
          className="mp-select"
          style={{ padding: '5px 8px', fontSize: '12px', height: '32px' }}
          value={status}
          onChange={(e) => setStatus(e.target.value)}
          aria-label="Lọc theo trạng thái"
        >
          <option value="ALL">Mọi trạng thái</option>
          {statusOptions.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
        </select>
      </div>

      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '8px', fontSize: '12px', color: 'var(--text-muted)', paddingTop: '2px' }}>
        <span>
          Hiển thị <strong>{filtered.length}</strong> / {total} {noun}
          {openCarried > 0 && ` (gồm ${openCarried} ${openLabel || `${noun} đang mở`} từ trước)`}
        </span>
        {(fromDate || toDate) && (
          <span>
            {fromDate === toDate
              ? `Ngày ${formatDateOnly(fromDate)}`
              : `${fromDate ? formatDateOnly(fromDate) : '...'} → ${toDate ? formatDateOnly(toDate) : '...'}`}
          </span>
        )}
      </div>
    </div>
  );
}
