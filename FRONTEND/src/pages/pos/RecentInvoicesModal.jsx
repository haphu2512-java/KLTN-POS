import { useState, useEffect, useMemo } from 'react';
import { Modal, Button, Table, Badge, Input, Loading } from '../../components/common';
import { formatCurrency, formatDateTime } from '../../utils/formatters';
import { shiftService } from '../../services/shiftService';
import { saleService } from '../../services/saleService';
import { printInvoiceReceipt, receiptFromInvoice } from '../../utils/receiptPrinter';
import { PAYMENT_LABELS } from '../shifts/shiftUtils';

export default function RecentInvoicesModal({
  isOpen,
  onClose,
  shiftSession = null,
  branchId,
  cashierName = 'Thu ngân',
  // Chỉ ADMIN/MANAGER xem được toàn bộ hóa đơn chi nhánh; thu ngân chỉ xem hóa đơn trong ca của mình
  canViewAll = false
}) {
  const defaultTab = shiftSession || !canViewAll ? 'shift' : 'all';
  const [activeTab, setActiveTab] = useState(defaultTab);
  const [invoices, setInvoices] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedInvoice, setSelectedInvoice] = useState(null);
  const [loadingDetail, setLoadingDetail] = useState(false);
  const [printStatus, setPrintStatus] = useState(null);
  const [reloadKey, setReloadKey] = useState(0);

  // Reset state khi mở modal
  const [prevOpen, setPrevOpen] = useState(isOpen);
  if (isOpen !== prevOpen) {
    setPrevOpen(isOpen);
    if (isOpen) {
      setSelectedInvoice(null);
      setSearchTerm('');
      setPrintStatus(null);
      setError('');
      setActiveTab(defaultTab);
    }
  }
  // Ca tải xong sau khi modal đã mở: chuyển sang tab ca
  const [prevShiftId, setPrevShiftId] = useState(shiftSession?.id);
  if (shiftSession?.id !== prevShiftId) {
    setPrevShiftId(shiftSession?.id);
    if (shiftSession) setActiveTab('shift');
  }
  const showingShift = activeTab === 'shift' || !canViewAll;

  // Tải danh sách hóa đơn
  useEffect(() => {
    if (!isOpen || selectedInvoice) return;
    if (showingShift && !shiftSession?.id) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setInvoices([]);
      return;
    }
    let active = true;
    setLoading(true);

    const req = showingShift
      ? shiftService.getShiftInvoices(shiftSession.id)
      : saleService.getRecentInvoices(branchId);

    req
      .then((data) => {
        if (!active) return;
        const list = Array.isArray(data) ? data : (data?.data || []);
        setInvoices(list);
        setError('');
      })
      .catch((err) => {
        if (!active) return;
        console.error('Lỗi tải hóa đơn:', err);
        setError(err.response?.data?.message || 'Không thể tải danh sách hóa đơn.');
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [isOpen, showingShift, selectedInvoice, shiftSession?.id, branchId, reloadKey]);

  // Xem chi tiết một hóa đơn và tải đầy đủ items
  // Không dùng dòng danh sách thay cho chi tiết: dòng của tab ca không có mặt hàng, in ra sẽ thành bill trống
  const handleSelectInvoice = async (inv) => {
    if (loadingDetail) return;
    setLoadingDetail(true);
    setPrintStatus(null);
    setError('');
    try {
      const full = await saleService.getInvoiceById(inv.id);
      if (!full?.id) throw new Error('empty');
      setSelectedInvoice(full);
    } catch (err) {
      console.error('Lỗi tải chi tiết hóa đơn:', err);
      setError(err.response?.data?.message || 'Không tải được chi tiết hóa đơn, vui lòng thử lại.');
    } finally {
      setLoadingDetail(false);
    }
  };

  // In lại hóa đơn K80
  const canPrint = (selectedInvoice?.details || []).length > 0;
  const handlePrintReceipt = async () => {
    if (!canPrint) return;
    setPrintStatus({ loading: true, message: 'Đang gửi lệnh in...' });
    try {
      const receipt = receiptFromInvoice(
        selectedInvoice,
        selectedInvoice.employeeName || cashierName,
        { at: new Date(), by: cashierName }
      );
      const res = await printInvoiceReceipt(receipt);
      if (res?.success) {
        setPrintStatus({ success: true, message: 'Lệnh in hóa đơn K80 đã được gửi thành công!' });
      } else {
        setPrintStatus({ success: false, message: `Lỗi in: ${res?.error || 'Không kết nối được máy in'}` });
      }
    } catch (err) {
      setPrintStatus({ success: false, message: `Lỗi: ${err.message}` });
    }
  };

  // Lọc hóa đơn theo tìm kiếm
  const filteredInvoices = useMemo(() => {
    const term = searchTerm.trim().toLowerCase();
    if (!term) return invoices;
    return invoices.filter((inv) => {
      const idMatch = String(inv.id).includes(term) || `hd${String(inv.id).padStart(6, '0')}`.toLowerCase().includes(term);
      const custName = (inv.customerName || inv.customer?.name || '').toLowerCase();
      const custPhone = (inv.customer?.phone || '').toLowerCase();
      const note = (inv.note || '').toLowerCase();
      return idMatch || custName.includes(term) || custPhone.includes(term) || note.includes(term);
    });
  }, [invoices, searchTerm]);

  const columns = [
    {
      key: 'id',
      title: 'Mã HĐ',
      width: '100px',
      render: (val) => (
        <strong style={{ fontFamily: 'var(--font-mono)', color: 'var(--primary-700)' }}>
          #{val}
        </strong>
      )
    },
    {
      key: 'createdAt',
      title: 'Thời gian',
      width: '150px',
      render: (val) => (val ? formatDateTime(val) : '—')
    },
    {
      key: 'customer',
      title: 'Khách hàng',
      render: (_, inv) => {
        const name = inv.customerName || inv.customer?.name;
        const phone = inv.customer?.phone;
        if (!name && !phone) return <span style={{ color: 'var(--text-muted)' }}>Khách lẻ</span>;
        return (
          <span>
            {name || 'Khách lẻ'} {phone ? <small style={{ color: 'var(--text-muted)' }}>({phone})</small> : null}
          </span>
        );
      }
    },
    {
      key: 'itemCount',
      title: 'Số món',
      align: 'center',
      width: '80px',
      render: (val, inv) => {
        const count = val ?? inv.details?.length ?? '—';
        return <span className="tabular-nums font-medium">{count}</span>;
      }
    },
    {
      key: 'totalAmount',
      title: 'Tổng tiền',
      align: 'right',
      width: '120px',
      render: (val) => (
        <strong className="tabular-nums" style={{ color: 'var(--primary-700)' }}>
          {formatCurrency(val)}
        </strong>
      )
    },
    {
      key: 'paymentMethod',
      title: 'Thanh toán',
      align: 'center',
      width: '120px',
      render: (val) => (
        <Badge variant={val === 'CASH' ? 'success' : val === 'VIETQR' ? 'primary' : 'secondary'}>
          {PAYMENT_LABELS[val] || val}
        </Badge>
      )
    },
    {
      key: 'status',
      title: 'Trạng thái',
      align: 'center',
      width: '120px',
      render: (val, inv) =>
        inv.kind === 'REFUND_OTHER_SHIFT' ? (
          <Badge variant="warning" title="Hóa đơn của ca khác, tiền hoàn trả chi từ két ca này">
            Hoàn trả (ca khác)
          </Badge>
        ) : (
          <Badge variant={val === 'REFUNDED' ? 'danger' : 'success'}>
            {val === 'REFUNDED' ? 'Đã hoàn trả' : 'Đã thanh toán'}
          </Badge>
        )
    },
    {
      key: 'actions',
      title: 'Thao tác',
      align: 'center',
      width: '110px',
      render: (_, inv) => (
        <Button
          size="sm"
          variant="secondary"
          onClick={(e) => {
            e.stopPropagation();
            handleSelectInvoice(inv);
          }}
          title="Xem chi tiết hóa đơn & in lại bill"
        >
          Xem & In
        </Button>
      )
    }
  ];

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={
        selectedInvoice
          ? `Chi tiết hóa đơn #HD${String(selectedInvoice.id).padStart(6, '0')}`
          : 'Lịch sử hóa đơn bán hàng'
      }
      maxWidth={selectedInvoice ? '720px' : '900px'}
      footer={
        selectedInvoice ? (
          <div style={{ display: 'flex', justifyContent: 'space-between', width: '100%', alignItems: 'center' }}>
            <Button variant="secondary" onClick={() => setSelectedInvoice(null)}>
              ← Quay lại danh sách
            </Button>
            <div style={{ display: 'flex', gap: '8px' }}>
              <Button
                variant="primary"
                onClick={handlePrintReceipt}
                disabled={printStatus?.loading || !canPrint}
                title={canPrint ? 'In lại hóa đơn ra máy in nhiệt K80' : 'Hóa đơn không có mặt hàng để in'}
              >
                {printStatus?.loading ? 'Đang in...' : 'In lại hóa đơn (K80)'}
              </Button>
              <Button variant="secondary" onClick={onClose}>
                Đóng
              </Button>
            </div>
          </div>
        ) : (
          <div style={{ display: 'flex', justifyContent: 'space-between', width: '100%', alignItems: 'center' }}>
            <span style={{ fontSize: '13px', color: 'var(--text-muted)' }}>
              Hiển thị {filteredInvoices.length} hóa đơn
            </span>
            <Button variant="secondary" onClick={onClose}>
              Đóng (Esc)
            </Button>
          </div>
        )
      }
    >
      {selectedInvoice ? (
        /* DETAIL VIEW */
        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          {printStatus && (
            <div
              style={{
                padding: '8px 12px',
                borderRadius: 'var(--radius-sm)',
                fontSize: '13px',
                backgroundColor: printStatus.success
                  ? 'var(--success-50, #ecfdf5)'
                  : printStatus.loading
                  ? 'var(--brand-50, #eff6ff)'
                  : 'var(--danger-50, #fef2f2)',
                color: printStatus.success
                  ? 'var(--success-700, #047857)'
                  : printStatus.loading
                  ? 'var(--primary-700, #1d4ed8)'
                  : 'var(--danger-700, #b91c1c)',
                border: '1px solid currentColor'
              }}
            >
              {printStatus.message}
            </div>
          )}

          {/* Invoice Summary Header */}
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
              gap: '12px',
              backgroundColor: 'var(--bg-muted, #f8fafc)',
              padding: '12px 16px',
              borderRadius: 'var(--radius-md)',
              fontSize: '13px'
            }}
          >
            <div>
              <span style={{ color: 'var(--text-muted)' }}>Mã hóa đơn:</span>
              <div style={{ fontWeight: 600, fontFamily: 'var(--font-mono)' }}>
                #HD{String(selectedInvoice.id).padStart(6, '0')}
              </div>
            </div>
            <div>
              <span style={{ color: 'var(--text-muted)' }}>Thời gian tạo:</span>
              <div style={{ fontWeight: 500 }}>
                {selectedInvoice.createdAt ? formatDateTime(selectedInvoice.createdAt) : '—'}
              </div>
            </div>
            <div>
              <span style={{ color: 'var(--text-muted)' }}>Khách hàng:</span>
              <div style={{ fontWeight: 500 }}>
                {selectedInvoice.customer?.name || selectedInvoice.customerName || 'Khách lẻ'}
                {selectedInvoice.customer?.phone ? ` (${selectedInvoice.customer.phone})` : ''}
              </div>
            </div>
            <div>
              <span style={{ color: 'var(--text-muted)' }}>Phương thức:</span>
              <div>
                <Badge variant={selectedInvoice.paymentMethod === 'CASH' ? 'success' : 'primary'}>
                  {PAYMENT_LABELS[selectedInvoice.paymentMethod] || selectedInvoice.paymentMethod}
                </Badge>
              </div>
            </div>
          </div>

          {/* Items Table */}
          <div style={{ border: '1px solid var(--border-subtle)', borderRadius: 'var(--radius-md)', overflow: 'hidden' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '13px' }}>
              <thead>
                <tr style={{ backgroundColor: 'var(--bg-muted, #f8fafc)', borderBottom: '1px solid var(--border-subtle)' }}>
                  <th style={{ padding: '8px 12px', textAlign: 'left' }}>Sản phẩm</th>
                  <th style={{ padding: '8px 12px', textAlign: 'center', width: '70px' }}>SL</th>
                  <th style={{ padding: '8px 12px', textAlign: 'right', width: '110px' }}>Đơn giá</th>
                  <th style={{ padding: '8px 12px', textAlign: 'right', width: '120px' }}>Thành tiền</th>
                </tr>
              </thead>
              <tbody>
                {(selectedInvoice.details || []).length > 0 ? (
                  selectedInvoice.details.map((detail, idx) => (
                    <tr key={idx} style={{ borderBottom: '1px solid var(--border-subtle)' }}>
                      <td style={{ padding: '8px 12px' }}>
                        <div style={{ fontWeight: 500 }}>{detail.product?.name || 'Sản phẩm'}</div>
                        {detail.product?.barcode && (
                          <small style={{ color: 'var(--text-muted)', fontFamily: 'var(--font-mono)' }}>
                            {detail.product.barcode}
                          </small>
                        )}
                      </td>
                      <td style={{ padding: '8px 12px', textAlign: 'center' }} className="tabular-nums font-semibold">
                        {detail.quantity}
                      </td>
                      <td style={{ padding: '8px 12px', textAlign: 'right' }} className="tabular-nums">
                        {formatCurrency(detail.unitPrice)}
                      </td>
                      <td style={{ padding: '8px 12px', textAlign: 'right', fontWeight: 600 }} className="tabular-nums">
                        {formatCurrency((detail.quantity || 1) * (detail.unitPrice || 0))}
                      </td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td colSpan={4} style={{ padding: '16px', textAlign: 'center', color: 'var(--text-muted)' }}>
                      Không có chi tiết mặt hàng
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          {/* Payment calculation breakdown */}
          <div
            style={{
              display: 'flex',
              flexDirection: 'column',
              gap: '6px',
              alignSelf: 'flex-end',
              width: '280px',
              fontSize: '13px'
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <span style={{ color: 'var(--text-muted)' }}>Tiền hàng:</span>
              <span className="tabular-nums font-medium">
                {formatCurrency(selectedInvoice.subtotal || selectedInvoice.totalAmount)}
              </span>
            </div>
            {Number(selectedInvoice.discount) > 0 && (
              <div style={{ display: 'flex', justifyContent: 'space-between', color: 'var(--danger-600)' }}>
                <span>Giảm giá:</span>
                <span className="tabular-nums">- {formatCurrency(selectedInvoice.discount)}</span>
              </div>
            )}
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                paddingTop: '6px',
                borderTop: '1px solid var(--border-subtle)',
                fontSize: '15px'
              }}
            >
              <strong>Tổng thanh toán:</strong>
              <strong style={{ color: 'var(--primary-700)' }} className="tabular-nums">
                {formatCurrency(selectedInvoice.totalAmount)}
              </strong>
            </div>
            {selectedInvoice.paymentMethod === 'CASH' && (
              <>
                <div style={{ display: 'flex', justifyContent: 'space-between', color: 'var(--text-muted)' }}>
                  <span>Khách đưa:</span>
                  <span className="tabular-nums">{formatCurrency(selectedInvoice.receivedAmount)}</span>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', color: 'var(--text-muted)' }}>
                  <span>Tiền thối lại:</span>
                  <span className="tabular-nums">{formatCurrency(selectedInvoice.changeAmount)}</span>
                </div>
              </>
            )}
          </div>
        </div>
      ) : (
        /* LIST VIEW */
        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          {/* Controls bar: Tabs, Search, Refresh */}
          <div
            style={{
              display: 'flex',
              flexWrap: 'wrap',
              justifyContent: 'space-between',
              alignItems: 'center',
              gap: '10px'
            }}
          >
            <div style={{ display: 'flex', gap: '6px' }}>
              {shiftSession && canViewAll && (
                <button
                  type="button"
                  className={`btn-filter ${activeTab === 'shift' ? 'active' : ''}`}
                  onClick={() => setActiveTab('shift')}
                  style={{
                    padding: '6px 12px',
                    borderRadius: 'var(--radius-sm)',
                    border: '1px solid var(--border-subtle)',
                    backgroundColor: activeTab === 'shift' ? 'var(--primary-600)' : 'transparent',
                    color: activeTab === 'shift' ? '#ffffff' : 'var(--text-main)',
                    fontSize: '13px',
                    fontWeight: 500,
                    cursor: 'pointer'
                  }}
                >
                  Đơn trong ca này ({shiftSession.workShiftName || 'Ca hiện tại'})
                </button>
              )}
              {!canViewAll && (
                <span style={{ fontSize: '13px', fontWeight: 500 }}>
                  {shiftSession
                    ? `Đơn trong ca này (${shiftSession.workShiftName || 'Ca hiện tại'})`
                    : 'Chưa mở ca: mở ca để xem hóa đơn trong ca'}
                </span>
              )}
              {canViewAll && (
              <button
                type="button"
                className={`btn-filter ${activeTab === 'all' ? 'active' : ''}`}
                onClick={() => setActiveTab('all')}
                style={{
                  padding: '6px 12px',
                  borderRadius: 'var(--radius-sm)',
                  border: '1px solid var(--border-subtle)',
                  backgroundColor: activeTab === 'all' ? 'var(--primary-600)' : 'transparent',
                  color: activeTab === 'all' ? '#ffffff' : 'var(--text-main)',
                  fontSize: '13px',
                  fontWeight: 500,
                  cursor: 'pointer'
                }}
              >
                50 đơn gần nhất của chi nhánh
              </button>
              )}
            </div>

            <div style={{ display: 'flex', gap: '8px', alignItems: 'center', flex: '1', maxWidth: '320px', marginLeft: 'auto' }}>
              <Input
                placeholder="Tìm mã HĐ, tên, SĐT khách..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                style={{ fontSize: '13px' }}
              />
              <Button size="sm" variant="secondary" onClick={() => setReloadKey((k) => k + 1)} title="Tải lại danh sách">
                Làm mới
              </Button>
            </div>
          </div>

          {loadingDetail && (
            <div style={{ textAlign: 'center', padding: '4px' }}>
              <Loading text="Đang tải chi tiết hóa đơn..." inline />
            </div>
          )}

          {error && (
            <div
              style={{
                padding: '8px 12px',
                borderRadius: 'var(--radius-sm)',
                backgroundColor: 'var(--danger-50, #fef2f2)',
                color: 'var(--danger-700, #b91c1c)',
                fontSize: '13px'
              }}
            >
              {error}
            </div>
          )}

          {/* Table */}
          <Table
            columns={columns}
            data={filteredInvoices}
            loading={loading}
            loadingText="Đang tải lịch sử hóa đơn..."
            emptyMessage={
              searchTerm
                ? 'Không tìm thấy hóa đơn nào khớp với từ khóa tìm kiếm.'
                : showingShift
                ? shiftSession
                  ? 'Ca hiện tại chưa có hóa đơn bán hàng nào.'
                  : 'Chưa mở ca nên chưa có hóa đơn trong ca.'
                : 'Chưa có hóa đơn nào được ghi nhận.'
            }
            onRowClick={handleSelectInvoice}
            maxHeight="420px"
          />
        </div>
      )}
    </Modal>
  );
}
