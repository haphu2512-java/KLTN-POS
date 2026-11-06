import { useEffect, useState } from 'react';
import { Modal, Button, Table, Badge } from '../../components/common';
import { expiryService } from '../../services/expiryService';
import { formatCurrency } from '../../utils/formatters';

const fmtDate = (d) => (d ? new Date(d).toLocaleDateString('vi-VN') : '—');

/**
 * Danh sách lô còn hàng đã hết hạn hoặc sắp hết hạn. Số còn lại mỗi lô suy từ tồn kho theo giả định bán
 * hàng hết hạn trước (FEFO). Từ đây lập phiếu kiểm kê hủy hàng hết hạn để quản lý duyệt.
 */
export default function ExpiryModal({ isOpen, onClose, branchId, canDispose, onDisposalCreated }) {
  const [days, setDays] = useState(30);
  const [report, setReport] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!isOpen) return;
    let alive = true;
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setLoading(true);
    expiryService
      .report(days, branchId)
      .then((r) => alive && (setReport(r), setError('')))
      .catch((err) => alive && setError(err?.response?.data?.message || 'Không tải được danh sách lô'))
      .finally(() => alive && setLoading(false));
    return () => {
      alive = false;
    };
  }, [isOpen, days, branchId]);

  const dispose = async () => {
    try {
      const count = await expiryService.disposalCount();
      onDisposalCreated?.(count);
    } catch (err) {
      setError(err?.response?.data?.message || 'Không lập được phiếu hủy');
    }
  };

  const columns = [
    {
      key: 'productName',
      title: 'Sản phẩm',
      render: (v, l) => (
        <div>
          <div className="font-medium">{v}</div>
          <div className="text-muted" style={{ fontSize: 12 }}>{l.barcode}</div>
        </div>
      ),
    },
    { key: 'lotNumber', title: 'Số lô', width: '100px', render: (v) => v || '—' },
    { key: 'expiryDate', title: 'Hạn dùng', width: '110px', render: (v) => fmtDate(v) },
    {
      key: 'daysLeft',
      title: 'Còn',
      width: '120px',
      render: (v, l) =>
        l.status === 'EXPIRED' ? (
          <Badge variant="danger">Quá hạn {-v} ngày</Badge>
        ) : (
          <Badge variant="warning">{v === 0 ? 'Hết hạn hôm nay' : `${v} ngày`}</Badge>
        ),
    },
    { key: 'remainingQty', title: 'SL còn', width: '80px', align: 'right' },
    { key: 'value', title: 'Giá vốn', width: '110px', align: 'right', render: (v) => formatCurrency(v || 0) },
  ];

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="Hàng cận date và hết hạn"
      maxWidth="860px"
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Đóng</Button>
          {canDispose && (
            <Button variant="danger" onClick={dispose} disabled={!report?.expiredLots}
              title="Lập phiếu kiểm kê với lý do Hết hạn cho các lô đã quá hạn, quản lý duyệt thì trừ tồn">
              Lập phiếu hủy hàng hết hạn
            </Button>
          )}
        </>
      }
    >
      <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 16, alignItems: 'center', fontSize: 13 }}>
          <label>
            Cảnh báo trước{' '}
            <select value={days} onChange={(e) => setDays(Number(e.target.value))} aria-label="Số ngày cảnh báo">
              <option value={7}>7 ngày</option>
              <option value={30}>30 ngày</option>
              <option value={60}>60 ngày</option>
              <option value={90}>90 ngày</option>
            </select>
          </label>
          {report && (
            <>
              <span>Đã hết hạn: <strong className="text-danger">{report.expiredLots} lô ({formatCurrency(report.expiredValue)})</strong></span>
              <span>Sắp hết hạn: <strong style={{ color: 'var(--warning-700)' }}>{report.nearLots} lô ({formatCurrency(report.nearValue)})</strong></span>
            </>
          )}
        </div>
        {error && <div className="text-danger" role="alert">{error}</div>}
        <Table
          columns={columns}
          data={(report?.lots || []).map((l) => ({ ...l, key: `${l.stockInId}-${l.productId}-${l.lotNumber}-${l.expiryDate}` }))}
          loading={loading}
          rowKey="key"
          emptyText="Không có lô nào hết hạn hoặc sắp hết hạn"
          maxHeight="55vh"
        />
        <p className="text-muted" style={{ fontSize: 12, margin: 0 }}>
          Chỉ theo dõi hàng nhập có khai hạn dùng. Số còn lại mỗi lô ước theo nguyên tắc bán hàng hết hạn trước (FEFO).
        </p>
      </div>
    </Modal>
  );
}
