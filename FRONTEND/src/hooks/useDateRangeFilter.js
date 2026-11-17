import { useMemo, useState } from 'react';

/** yyyy-mm-dd theo giờ máy (không dùng toISOString vì đổi sang UTC làm lệch ngày trước 7 giờ sáng) */
export const toLocalDateStr = (dateVal) => {
  if (!dateVal) return '';
  const d = new Date(dateVal);
  if (Number.isNaN(d.getTime())) return '';
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};

/** yyyy-mm-dd → dd/mm/yyyy */
export const formatDateOnly = (dateStr) => {
  if (!dateStr) return '';
  const parts = dateStr.split('-');
  return parts.length === 3 ? `${parts[2]}/${parts[1]}/${parts[0]}` : dateStr;
};

export const DATE_PRESETS = [
  { value: 'today', label: 'Hôm nay' },
  { value: 'yesterday', label: 'Hôm qua' },
  { value: '7days', label: '7 ngày qua' },
  { value: '30days', label: '30 ngày qua' },
  { value: 'all', label: 'Tất cả ngày' },
  { value: 'custom', label: 'Tùy chọn...' },
];

const shift = (base, days) => {
  const d = new Date(base);
  d.setDate(d.getDate() + days);
  return toLocalDateStr(d);
};

/** Khoảng ngày của một preset, tính theo ngày hiện tại lúc gọi (app để qua nửa đêm vẫn đúng) */
const rangeOf = (preset, now = new Date()) => {
  const today = toLocalDateStr(now);
  switch (preset) {
    case 'today': return [today, today];
    case 'yesterday': return [shift(now, -1), shift(now, -1)];
    case '7days': return [shift(now, -6), today];
    case '30days': return [shift(now, -29), today];
    default: return ['', ''];
  }
};

/**
 * Lọc danh sách đơn / phiếu theo ngày tạo, trạng thái và từ khóa. Bản ghi còn đang mở (openStatuses, vd. đơn đã gửi
 * chờ hàng về, phiếu đang đếm) luôn hiện dù tạo ngày nào, để việc dở dang không biến mất khi sang ngày mới.
 *
 * @param items     danh sách gốc (mỗi phần tử có createdAt, status)
 * @param options   { openStatuses: string[], matchKeyword: (item, kwLower) => boolean, defaultPreset }
 */
export function useDateRangeFilter(items, { openStatuses = [], matchKeyword, defaultPreset = 'today' } = {}) {
  const [preset, setPreset] = useState(defaultPreset);
  const [custom, setCustom] = useState(() => rangeOf(defaultPreset));
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState('ALL');

  const [fromDate, toDate] = preset === 'custom' ? custom : rangeOf(preset);

  const changePreset = (p) => {
    if (p === 'custom') setCustom(rangeOf(preset));
    setPreset(p);
  };
  const setFromDate = (v) => { setCustom([v, toDate]); setPreset('custom'); };
  const setToDate = (v) => { setCustom([fromDate, v]); setPreset('custom'); };

  const reset = () => {
    setPreset(defaultPreset);
    setKeyword('');
    setStatus('ALL');
  };

  /** Mở một bản ghi cụ thể (vd. đến từ trang khác): bản ghi cũ hơn khoảng đang lọc thì bỏ lọc ngày để nó hiện ra */
  const reveal = (item) => {
    const d = toLocalDateStr(item?.createdAt);
    if (!d || openStatuses.includes(item?.status)) return;
    if ((fromDate && d < fromDate) || (toDate && d > toDate)) setPreset('all');
  };

  const { filtered, openCarried } = useMemo(() => {
    const kw = keyword.trim().toLowerCase();
    const out = [];
    let carried = 0;
    for (const it of items || []) {
      if (status !== 'ALL' && it.status !== status) continue;
      if (kw && !(matchKeyword ? matchKeyword(it, kw) : String(it.id).includes(kw.replace(/^#/, '')))) continue;
      if (fromDate || toDate) {
        const d = toLocalDateStr(it.createdAt);
        const inRange = !((fromDate && d < fromDate) || (toDate && d > toDate));
        if (!inRange) {
          if (!openStatuses.includes(it.status)) continue;
          carried += 1;
        }
      }
      out.push(it);
    }
    return { filtered: out, openCarried: carried };
    // matchKeyword / openStatuses là hằng của trang gọi
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [items, fromDate, toDate, status, keyword]);

  const isFiltered = preset !== defaultPreset || Boolean(keyword) || status !== 'ALL';

  return {
    preset, changePreset, fromDate, toDate, setFromDate, setToDate,
    keyword, setKeyword, status, setStatus,
    filtered, openCarried, isFiltered, reset, reveal,
    total: (items || []).length,
  };
}
