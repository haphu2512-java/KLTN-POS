import { useEffect, useState } from 'react';
import { Button, Table, Badge, Modal } from '../../components/common';
import { loyaltyService } from '../../services/loyaltyService';
import { formatNumber, formatDateTime } from '../../utils/formatters';

const TYPE_LABELS = {
  EARN: { label: 'Tích điểm', variant: 'success' },
  REDEEM: { label: 'Đổi điểm', variant: 'brand' },
  REFUND_EARN: { label: 'Thu hồi (hoàn trả)', variant: 'danger' },
  REFUND_REDEEM: { label: 'Trả lại (hoàn trả)', variant: 'neutral' },
  ADJUST: { label: 'Điều chỉnh', variant: 'warning' }
};

/** Sổ điểm của một khách; quản lý điều chỉnh điểm tay (bắt buộc lý do) */
export default function PointHistoryModal({ customer, onClose, canAdjust = false, onAdjusted }) {
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(false);
  const [points, setPoints] = useState('');
  const [reason, setReason] = useState('');
  const [saving, setSaving] = useState(false);

  const load = async (id) => {
    setLoading(true);
    try {
      setRows(await loyaltyService.getTransactions(id));
    } catch (err) {
      console.error('Lỗi tải lịch sử điểm:', err);
      setRows([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!customer?.id) return;
    // Tải lại mỗi lần mở cho khách khác (hàm load tự bật cờ loading)
    // eslint-disable-next-line react-hooks/set-state-in-effect
    load(customer.id);
    setPoints('');
    setReason('');
  }, [customer?.id]);

  const handleAdjust = async (e) => {
    e.preventDefault();
    const delta = Math.trunc(Number(points));
    if (!delta || !reason.trim()) {
      alert('Nhập số điểm (âm để trừ) và lý do điều chỉnh');
      return;
    }
    setSaving(true);
    try {
      const updated = await loyaltyService.adjust(customer.id, delta, reason.trim());
      setPoints('');
      setReason('');
      await load(customer.id);
      onAdjusted?.(updated);
    } catch (err) {
      alert(err.response?.data?.message || 'Không điều chỉnh được điểm');
    } finally {
      setSaving(false);
    }
  };

  const columns = [
    { key: 'createdAt', title: 'Thời gian', width: '150px', render: (val) => (val ? formatDateTime(val) : '—') },
    {
      key: 'type',
      title: 'Loại',
      render: (val) => {
        const t = TYPE_LABELS[val] || { label: val, variant: 'neutral' };
        return <Badge variant={t.variant}>{t.label}</Badge>;
      }
    },
    {
      key: 'points',
      title: 'Điểm',
      align: 'right',
      render: (val) => (
        <span className={`tabular-nums font-semibold ${val > 0 ? 'text-success' : 'text-danger'}`}>
          {val > 0 ? '+' : ''}{formatNumber(val)}
        </span>
      )
    },
    { key: 'balanceAfter', title: 'Số dư', align: 'right', render: (val) => <span className="tabular-nums">{formatNumber(val)}</span> },
    {
      key: 'invoiceId',
      title: 'Hóa đơn / ghi chú',
      render: (val, row) => (
        <span style={{ fontSize: '12px' }}>
          {val ? `HD${String(val).padStart(6, '0')}` : ''}
          {val && row.note ? ' · ' : ''}
          {row.note || (val ? '' : '—')}
        </span>
      )
    }
  ];

  return (
    <Modal
      isOpen={!!customer}
      onClose={onClose}
      title={customer ? `Lịch sử điểm: ${customer.name} (${formatNumber(customer.points ?? 0)} điểm)` : ''}
      width="720px"
    >
      <Table columns={columns} data={rows} loading={loading} maxHeight="360px" emptyText="Chưa có biến động điểm" />

      {canAdjust && (
        <form onSubmit={handleAdjust} style={{ display: 'flex', gap: '8px', marginTop: '16px', alignItems: 'center' }}>
          <input
            type="number"
            className="partner-form-input"
            style={{ width: '120px' }}
            placeholder="+/- điểm"
            value={points}
            onChange={(e) => setPoints(e.target.value)}
          />
          <input
            type="text"
            className="partner-form-input"
            style={{ flex: 1 }}
            placeholder="Lý do điều chỉnh (bắt buộc)"
            maxLength={255}
            value={reason}
            onChange={(e) => setReason(e.target.value)}
          />
          <Button type="submit" variant="primary" disabled={saving}>
            {saving ? 'Đang lưu...' : 'Điều chỉnh'}
          </Button>
        </form>
      )}
    </Modal>
  );
}
