import { Badge } from '../../components/common';
import { formatCurrency } from '../../utils/formatters';

/** Chênh lệch tiền két: âm là thiếu, dương là dư */
export default function DifferenceCell({ value, status }) {
  if (value == null) {
    return status === 'OPEN' ? <Badge variant="brand">Đang mở</Badge> : <span>—</span>;
  }
  const d = Number(value);
  if (d === 0) return <Badge variant="success">Khớp</Badge>;
  return (
    <strong className={d < 0 ? 'shift-short' : 'shift-over'}>
      {d < 0 ? 'Thiếu ' : 'Dư '}
      {formatCurrency(Math.abs(d))}
    </strong>
  );
}
