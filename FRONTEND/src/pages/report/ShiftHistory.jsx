import { useCallback, useEffect, useState } from 'react';
import { Button, Table, Badge } from '../../components/common';
import { formatCurrency, formatDateTime } from '../../utils/formatters';
import { shiftService } from '../../services/shiftService';
import { exportToCsv } from '../../utils/exportCsv';

const isoDate = (d) => {
  const local = new Date(d.getTime() - d.getTimezoneOffset() * 60000);
  return local.toISOString().slice(0, 10);
};

const differenceCell = (val) => {
  if (val == null) return '—';
  const d = Number(val);
  if (d === 0) return <Badge variant="success">Khớp</Badge>;
  return (
    <strong style={{ color: d < 0 ? 'var(--danger-600)' : 'var(--warning-600)' }}>
      {d < 0 ? 'Thiếu ' : 'Dư '}
      {formatCurrency(Math.abs(d))}
    </strong>
  );
};

/**
 * Lịch sử ca trực: ai trực ca nào, mở/đóng lúc nào, tiền đầu ca, tiền chốt cuối ca,
 * chênh lệch và ghi chú bàn giao. Quản lý đóng hộ được ca nhân viên quên đóng.
 */
export default function ShiftHistory({ branchId, days, isOnline }) {
  const [shifts, setShifts] = useState([]);
  const [error, setError] = useState('');

  const load = useCallback(() => {
    const to = new Date();
    const from = new Date();
    from.setDate(to.getDate() - (days - 1));
    shiftService
      .listShifts({ branchId, from: isoDate(from), to: isoDate(to) })
      .then((list) => {
        setShifts(list);
        setError('');
      })
      .catch((err) => setError(err.response?.data?.message || 'Không tải được lịch sử ca.'));
  }, [branchId, days]);

  useEffect(() => {
    load();
  }, [load, isOnline]);

  const handleForceClose = async (shift) => {
    const input = window.prompt(
      `Đóng hộ ${shift.workShiftName} của ${shift.employeeName}.\n` +
        `Tiền mặt lẽ ra có trong két: ${formatCurrency(shift.expectedCash)}.\n\nNhập số tiền đếm được:`
    );
    if (input == null) return;
    const closingCash = Number(input);
    if (!Number.isFinite(closingCash) || closingCash < 0) {
      alert('Số tiền không hợp lệ.');
      return;
    }
    try {
      await shiftService.closeShiftById(shift.id, { closingCash, handoverNote: 'Quản lý đóng hộ' });
      load();
    } catch (err) {
      alert(err.response?.data?.message || 'Không đóng được ca.');
    }
  };

  const handleExport = () => {
    exportToCsv(
      `lich-su-ca-cn${branchId}.csv`,
      ['Ngày', 'Ca', 'Nhân viên', 'Mở lúc', 'Đóng lúc', 'Tiền đầu ca', 'Bán tiền mặt', 'Thẻ/CK',
        'Số hóa đơn', 'Tiền mặt dự kiến', 'Tiền đếm được', 'Chênh lệch', 'Ghi chú bàn giao', 'Trạng thái'],
      shifts.map((s) => [
        s.shiftDate, s.workShiftName, s.employeeName, formatDateTime(s.openedAt),
        s.closedAt ? formatDateTime(s.closedAt) : '', s.openingCash, s.cashSales, s.nonCashSales,
        s.invoiceCount, s.expectedCash, s.closingCash ?? '', s.cashDifference ?? '', s.handoverNote || '',
        s.status === 'OPEN' ? 'Đang mở' : 'Đã đóng'
      ])
    );
  };

  const columns = [
    {
      key: 'shiftDate',
      title: 'Ngày',
      render: (val) => (val ? String(val).split('-').reverse().join('/') : '')
    },
    {
      key: 'workShiftName',
      title: 'Ca',
      render: (val, s) => (
        <div>
          <div>{val}</div>
          <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>{s.workShiftTime}</div>
        </div>
      )
    },
    { key: 'employeeName', title: 'Nhân viên' },
    {
      key: 'openedAt',
      title: 'Mở / đóng',
      render: (val, s) => (
        <div style={{ fontSize: '12px' }}>
          <div>{formatDateTime(val)}</div>
          <div style={{ color: 'var(--text-muted)' }}>{s.closedAt ? formatDateTime(s.closedAt) : 'Chưa đóng'}</div>
        </div>
      )
    },
    { key: 'openingCash', title: 'Đầu ca', align: 'right', render: (val) => formatCurrency(val) },
    {
      key: 'cashSales',
      title: 'Bán tiền mặt',
      align: 'right',
      render: (val, s) => (
        <div>
          <div>{formatCurrency(val)}</div>
          <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>{s.invoiceCount} hóa đơn</div>
        </div>
      )
    },
    { key: 'nonCashSales', title: 'Thẻ / CK', align: 'right', render: (val) => formatCurrency(val) },
    { key: 'expectedCash', title: 'Két dự kiến', align: 'right', render: (val) => formatCurrency(val) },
    {
      key: 'closingCash',
      title: 'Đếm được',
      align: 'right',
      render: (val) => (val == null ? '—' : formatCurrency(val))
    },
    { key: 'cashDifference', title: 'Chênh lệch', align: 'right', render: (val) => differenceCell(val) },
    {
      key: 'handoverNote',
      title: 'Bàn giao',
      render: (val) => <span style={{ fontSize: '12px' }}>{val || ''}</span>
    },
    {
      key: 'status',
      title: '',
      align: 'center',
      render: (val, s) =>
        val === 'OPEN' ? (
          <Button variant="secondary" size="sm" onClick={() => handleForceClose(s)}>
            Đóng hộ
          </Button>
        ) : (
          <Badge variant="neutral">Đã đóng</Badge>
        )
    }
  ];

  return (
    <div className="report-card">
      <div className="report-card-title">
        <span>Lịch sử ca làm việc ({shifts.length})</span>
        <Button variant="secondary" size="sm" onClick={handleExport} disabled={shifts.length === 0}>
          Xuất CSV
        </Button>
      </div>
      {error && <div style={{ color: 'var(--danger-600)', marginBottom: '8px' }}>{error}</div>}
      <Table columns={columns} data={shifts} rowKey="id" emptyMessage="Chưa có ca nào trong khoảng thời gian này" />
    </div>
  );
}
