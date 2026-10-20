import { useState, useEffect, useMemo } from 'react';
import { Button, Table, Badge, Modal } from '../../components/common';
import { formatCurrency, formatNumber, formatDateTime } from '../../utils/formatters';
import { reportService } from '../../services/reportService';
import { saleService } from '../../services/saleService';
import { exportToCsv } from '../../utils/exportCsv';
import './ReportPage.css';

export default function ReportPage({
  currentUser,
  branchId = 1,
  isOnline = true,
  onNavigateToPos,
  onNavigateToInventory,
  onNavigateToForecast,
  onNavigateToPartners,
  onNavigateToStaff,
  onLogout
}) {
  const [selectedBranchId, setSelectedBranchId] = useState(branchId || 1);
  const [days, setDays] = useState(7);
  const [loading, setLoading] = useState(false);
  const [viewMode, setViewMode] = useState('charts'); // 'charts' | 'invoices'
  const [invoices, setInvoices] = useState([]);
  const [refundTarget, setRefundTarget] = useState(null);
  const [refundReason, setRefundReason] = useState('');
  const [showRefundModal, setShowRefundModal] = useState(false);

  const [summaryData, setSummaryData] = useState({
    totalRevenue: 0,
    totalCost: 0,
    grossProfit: 0,
    profitMarginPercent: 0,
    totalOrders: 0,
    averageOrderValue: 0,
    dailyTrend: []
  });

  const [topProducts, setTopProducts] = useState([]);

  useEffect(() => {
    let isMounted = true;
    async function fetchReports() {
      setLoading(true);
      try {
        const [revRes, topRes] = await Promise.allSettled([
          reportService.getRevenueReport(selectedBranchId, days),
          reportService.getTopProducts(selectedBranchId, days, 10)
        ]);

        const revData = revRes.status === 'fulfilled' ? revRes.value : null;
        const topData = topRes.status === 'fulfilled' ? topRes.value : [];

        if (isMounted) {
          if (revData && revData.totalRevenue !== undefined) {
            setSummaryData(revData);
          }
          if (Array.isArray(topData)) {
            setTopProducts(topData);
          }
        }
      } catch (err) {
        console.error('Lỗi khi tải báo cáo từ cơ sở dữ liệu:', err);
      } finally {
        if (isMounted) setLoading(false);
      }
    }

    fetchReports();
    return () => {
      isMounted = false;
    };
  }, [selectedBranchId, days, isOnline]);

  const fetchInvoices = async () => {
    try {
      const data = await saleService.getInvoices(selectedBranchId);
      setInvoices(data || []);
    } catch (err) {
      console.error('Lỗi khi tải danh sách hóa đơn từ cơ sở dữ liệu:', err);
    }
  };

  useEffect(() => {
    if (viewMode !== 'invoices') return;
    let isMounted = true;
    saleService
      .getInvoices(selectedBranchId)
      .then((data) => {
        if (isMounted) setInvoices(data || []);
      })
      .catch((err) => console.error('Lỗi khi tải danh sách hóa đơn từ cơ sở dữ liệu:', err));
    return () => {
      isMounted = false;
    };
  }, [viewMode, selectedBranchId, isOnline]);

  const handleProcessRefund = async (e) => {
    e.preventDefault();
    if (!refundTarget) return;
    try {
      await saleService.refundInvoice(refundTarget.id, refundReason);
      alert(`Đã hoàn trả hóa đơn #${refundTarget.id} thành công! Số lượng tồn kho đã được hoàn lại.`);
      setShowRefundModal(false);
      setRefundReason('');
      setRefundTarget(null);
      fetchInvoices();
    } catch (err) {
      alert(err.response?.data?.message || 'Có lỗi khi hoàn trả hóa đơn');
    }
  };

  const handleExportCsv = () => {
    if (viewMode === 'invoices') {
      const headers = ['Mã hóa đơn', 'Thời gian', 'Thu ngân', 'Khách hàng', 'Tổng tiền (VND)', 'Phương thức', 'Trạng thái', 'Ghi chú'];
      const rows = invoices.map((i) => [
        `HD-${i.id}`,
        formatDateTime(i.createdAt),
        `Nhân viên ${i.employeeId || 1}`,
        i.customer ? i.customer.name : 'Khách lẻ',
        i.totalAmount,
        i.paymentMethod,
        i.status === 'REFUNDED' ? 'Đã hoàn trả' : 'Đã thanh toán',
        i.note || ''
      ]);
      exportToCsv(`DanhSachHoaDon_CN${selectedBranchId}`, headers, rows);
    } else {
      const headers = ['Mã sản phẩm', 'Mã vạch', 'Tên sản phẩm', 'Số lượng bán', 'Doanh thu (VND)', 'Lợi nhuận ước tính (VND)'];
      const rows = topProducts.map((p) => [
        p.productId,
        p.barcode,
        p.productName,
        p.quantitySold,
        p.totalRevenue,
        p.estimatedProfit
      ]);
      exportToCsv(`BaoCaoSanPhamBanChay_CN${selectedBranchId}`, headers, rows);
    }
  };

  // Chart data calculations
  const maxRevenue = useMemo(() => {
    const vals = summaryData.dailyTrend.map((d) => Number(d.revenue || 0));
    return Math.max(...vals, 1000000);
  }, [summaryData]);

  const topColumns = [
    {
      key: 'rank',
      title: '#',
      width: '40px',
      align: 'center',
      render: (_, __, idx) => (
        <span style={{ fontWeight: 700, color: idx < 3 ? '#10B981' : '#94A3B8' }}>
          {idx + 1}
        </span>
      )
    },
    {
      key: 'productName',
      title: 'Tên hàng hóa & Mã vạch',
      render: (val, row) => (
        <div>
          <div className="font-medium" style={{ fontSize: '12px' }}>{val}</div>
          <span className="text-muted" style={{ fontSize: '11px', fontFamily: 'monospace' }}>
            {row.barcode}
          </span>
        </div>
      )
    },
    {
      key: 'quantitySold',
      title: 'Đã bán',
      width: '80px',
      align: 'center',
      render: (val) => <span className="font-semibold tabular-nums">{val}</span>
    },
    {
      key: 'totalRevenue',
      title: 'Doanh thu',
      width: '110px',
      align: 'right',
      render: (val) => (
        <span className="font-bold tabular-nums" style={{ color: '#10B981' }}>
          {formatCurrency(val)}
        </span>
      )
    },
    {
      key: 'estimatedProfit',
      title: 'Lợi nhuận',
      width: '110px',
      align: 'right',
      render: (val) => (
        <span className="tabular-nums font-semibold" style={{ color: '#38BDF8' }}>
          {formatCurrency(val)}
        </span>
      )
    }
  ];

  const invoiceColumns = [
    {
      key: 'id',
      title: 'Mã HĐ',
      width: '90px',
      render: (i) => <strong style={{ color: '#38BDF8', fontFamily: 'monospace' }}>#{i.id}</strong>
    },
    {
      key: 'createdAt',
      title: 'Thời gian',
      width: '150px',
      render: (i) => (i.createdAt ? formatDateTime(i.createdAt) : '—')
    },
    {
      key: 'customer',
      title: 'Khách hàng',
      render: (i) => (i.customer ? `${i.customer.name} (${i.customer.phone})` : 'Khách lẻ')
    },
    {
      key: 'totalAmount',
      title: 'Tổng tiền',
      align: 'right',
      render: (i) => <strong style={{ color: '#10B981' }}>{formatCurrency(i.totalAmount)}</strong>
    },
    {
      key: 'paymentMethod',
      title: 'Hình thức',
      align: 'center',
      render: (i) => <Badge variant="secondary">{i.paymentMethod}</Badge>
    },
    {
      key: 'status',
      title: 'Trạng thái',
      align: 'center',
      render: (i) => (
        <Badge variant={i.status === 'REFUNDED' ? 'danger' : 'success'}>
          {i.status === 'REFUNDED' ? 'Đã hoàn trả' : 'Đã thanh toán'}
        </Badge>
      )
    },
    {
      key: 'note',
      title: 'Ghi chú',
      render: (i) => <span style={{ fontSize: '12px', color: '#94A3B8' }}>{i.note || '—'}</span>
    },
    {
      key: 'actions',
      title: 'Thao tác',
      align: 'center',
      render: (i) => (
        <div style={{ display: 'flex', gap: '6px', justifyContent: 'center' }}>
          {i.status !== 'REFUNDED' ? (
            <Button
              size="sm"
              variant="danger"
              onClick={() => {
                setRefundTarget(i);
                setShowRefundModal(true);
              }}
              title="Thực hiện hoàn trả hàng và hoàn tiền cho khách"
            >
              🔄 Trả hàng
            </Button>
          ) : (
            <span style={{ fontSize: '11px', color: '#EF4444' }}>Đã hoàn kho</span>
          )}
        </div>
      )
    }
  ];

  return (
    <div className="report-container">
      {/* Header */}
      <header className="report-header">
        <div className="report-header-left">
          <span style={{ fontSize: '20px' }}>📊</span>
          <span style={{ fontWeight: 700, fontSize: '15px', letterSpacing: '0.02em' }}>
            BÁO CÁO DOANH THU & HIỆU QUẢ KINH DOANH
          </span>
          <Badge variant="brand" withDot={false}>Chi nhánh {selectedBranchId}</Badge>
        </div>

        <div className="report-header-right">
          <Button
            variant="secondary"
            size="md"
            onClick={onNavigateToPos}
            title="Quay lại màn hình bán hàng"
          >
            🏪 Bán hàng (POS)
          </Button>
          <Button
            variant="secondary"
            size="md"
            onClick={onNavigateToInventory}
            title="Mở màn hình Quản lý Kho"
          >
            📦 Quản lý Kho
          </Button>
          {onNavigateToForecast && (
            <Button
              variant="secondary"
              size="md"
              onClick={onNavigateToForecast}
              title="Mở màn hình Dự báo AI"
            >
              📈 Dự báo AI
            </Button>
          )}
          <Button
            variant="secondary"
            size="md"
            onClick={onNavigateToPartners}
            title="Quản lý khách hàng thân thiết & nhà cung cấp"
          >
            👥 Khách & NCC
          </Button>
          {currentUser?.role === 'ADMIN' && onNavigateToStaff && (
            <Button
              variant="secondary"
              size="md"
              onClick={onNavigateToStaff}
              title="Quản lý tài khoản nhân viên"
            >
              👤 Nhân viên
            </Button>
          )}

          {onLogout && (
            <Button
              variant="danger"
              size="md"
              onClick={onLogout}
              title="Đăng xuất khỏi ca làm việc"
            >
              Đăng xuất
            </Button>
          )}
        </div>
      </header>

      {/* Main Body */}
      <main className="report-body">
        {/* Filter Bar */}
        <div className="report-filter-bar">
          <div className="report-filter-group">
            <span className="report-filter-label">Chi nhánh:</span>
            <select
              className="report-select"
              value={selectedBranchId}
              onChange={(e) => setSelectedBranchId(Number(e.target.value))}
            >
              <option value={1}>Chi nhánh 1 (Quận 1)</option>
              <option value={2}>Chi nhánh 2 (Bình Thạnh)</option>
            </select>
          </div>

          <div className="report-filter-group">
            <span className="report-filter-label">Thời gian:</span>
            <div className="report-btn-group">
              {[
                { label: '7 ngày qua', val: 7 },
                { label: '14 ngày qua', val: 14 },
                { label: '30 ngày qua', val: 30 },
                { label: '90 ngày qua', val: 90 }
              ].map((btn) => (
                <button
                  key={btn.val}
                  type="button"
                  className={`report-period-btn ${days === btn.val ? 'active' : ''}`}
                  onClick={() => setDays(btn.val)}
                >
                  {btn.label}
                </button>
              ))}
            </div>

            <Button
              variant="secondary"
              size="sm"
              onClick={handleExportCsv}
              title="Xuất dữ liệu hiển thị ra file CSV/Excel"
            >
              📥 Xuất CSV
            </Button>

            <Button
              variant="secondary"
              size="sm"
              onClick={() => window.print()}
              title="In báo cáo này ra giấy hoặc PDF"
            >
              🖨 In Báo Cáo
            </Button>
          </div>
        </div>

        {/* View Mode Sub-tabs */}
        <div style={{ display: 'flex', gap: '8px', marginBottom: '16px' }}>
          <button
            type="button"
            className={`report-period-btn ${viewMode === 'charts' ? 'active' : ''}`}
            onClick={() => setViewMode('charts')}
          >
            📊 Biểu Đồ & Top Sản Phẩm Bán Chạy
          </button>
          <button
            type="button"
            className={`report-period-btn ${viewMode === 'invoices' ? 'active' : ''}`}
            onClick={() => setViewMode('invoices')}
          >
            🧾 Lịch Sử Hóa Đơn & Trả Hàng ({invoices.length})
          </button>
        </div>

        {viewMode === 'invoices' ? (
          /* Invoices List Table */
          <div className="report-card">
            <div className="report-card-title">
              <span>Lịch Sử Giao Dịch & Hóa Đơn Bán Hàng</span>
              <span style={{ fontSize: '12px', color: '#64748B' }}>Chi nhánh {selectedBranchId}</span>
            </div>
            <Table
              columns={invoiceColumns}
              data={invoices}
              loading={loading}
              rowKey="id"
              emptyMessage="Chưa có hóa đơn nào tại chi nhánh này"
            />
          </div>
        ) : (
          /* Charts & Metrics Overview */
          <>
            {/* KPI Cards Grid */}
            <div className="report-kpi-grid">
              <div className="report-kpi-card">
                <div className="report-kpi-title">
                  <span>Tổng Doanh Thu</span>
                  <span>💰</span>
                </div>
                <div className="report-kpi-value" style={{ color: '#10B981' }}>
                  {formatCurrency(summaryData.totalRevenue)}
                </div>
                <div className="report-kpi-sub">Giá trị bán ra thực tế sau chiết khấu</div>
              </div>

              <div className="report-kpi-card">
                <div className="report-kpi-title">
                  <span>Tiền Vốn Hàng Bán</span>
                  <span>📦</span>
                </div>
                <div className="report-kpi-value" style={{ color: '#94A3B8' }}>
                  {formatCurrency(summaryData.totalCost)}
                </div>
                <div className="report-kpi-sub">Theo giá nhập kho bình quân</div>
              </div>

              <div className="report-kpi-card">
                <div className="report-kpi-title">
                  <span>Lợi Nhuận Gộp</span>
                  <span>📈</span>
                </div>
                <div className="report-kpi-value" style={{ color: '#38BDF8' }}>
                  {formatCurrency(summaryData.grossProfit)}
                </div>
                <div className="report-kpi-sub">
                  Tỷ suất lợi nhuận: <strong>{summaryData.profitMarginPercent}%</strong>
                </div>
              </div>

              <div className="report-kpi-card">
                <div className="report-kpi-title">
                  <span>Tổng Đơn & AOV</span>
                  <span>🧾</span>
                </div>
                <div className="report-kpi-value" style={{ color: '#F59E0B' }}>
                  {formatNumber(summaryData.totalOrders)} đơn
                </div>
                <div className="report-kpi-sub">
                  Giá trị TB/đơn: <strong>{formatCurrency(summaryData.averageOrderValue)}</strong>
                </div>
              </div>
            </div>

            {/* Chart & Top Products Grid */}
            <div className="report-grid-2col">
              {/* Revenue Trend SVG Chart */}
              <div className="report-card">
                <div className="report-card-title">
                  <span>Xu Hướng Doanh Thu & Lợi Nhuận Hằng Ngày</span>
                  <div className="report-chart-legend">
                    <div className="report-legend-item">
                      <div className="report-legend-dot" style={{ backgroundColor: '#10B981' }}></div>
                      <span>Doanh thu</span>
                    </div>
                    <div className="report-legend-item">
                      <div className="report-legend-dot" style={{ backgroundColor: '#38BDF8' }}></div>
                      <span>Lợi nhuận</span>
                    </div>
                  </div>
                </div>

                <div className="report-chart-wrapper">
                  <svg className="report-chart-svg" viewBox="0 0 600 200" preserveAspectRatio="none">
                    <line x1="0" y1="40" x2="600" y2="40" stroke="#334155" strokeDasharray="3 3" />
                    <line x1="0" y1="100" x2="600" y2="100" stroke="#334155" strokeDasharray="3 3" />
                    <line x1="0" y1="160" x2="600" y2="160" stroke="#334155" strokeDasharray="3 3" />

                    {summaryData.dailyTrend.map((d, idx) => {
                      const totalBars = summaryData.dailyTrend.length;
                      const slotWidth = 600 / totalBars;
                      const xCenter = slotWidth * idx + slotWidth / 2;

                      const revHeight = Math.max(4, ((Number(d.revenue) || 0) / maxRevenue) * 140);
                      const profHeight = Math.max(2, ((Number(d.profit) || 0) / maxRevenue) * 140);

                      const revY = 160 - revHeight;
                      const profY = 160 - profHeight;

                      return (
                        <g key={idx}>
                          <rect
                            x={xCenter - 14}
                            y={revY}
                            width="12"
                            height={revHeight}
                            fill="#10B981"
                            rx="2"
                          >
                            <title>{`${d.date}: Doanh thu ${formatCurrency(d.revenue)} (${d.orderCount} đơn)`}</title>
                          </rect>

                          <rect
                            x={xCenter + 2}
                            y={profY}
                            width="12"
                            height={profHeight}
                            fill="#38BDF8"
                            rx="2"
                          >
                            <title>{`${d.date}: Lợi nhuận ${formatCurrency(d.profit)}`}</title>
                          </rect>

                          <text
                            x={xCenter}
                            y="180"
                            fill="#94A3B8"
                            fontSize="10"
                            textAnchor="middle"
                          >
                            {d.date}
                          </text>
                        </g>
                      );
                    })}
                  </svg>
                </div>
              </div>

              {/* Top Selling Goods Table */}
              <div className="report-card">
                <div className="report-card-title">
                  <span>Top Sản Phẩm Bán Chạy Nhất</span>
                  <span style={{ fontSize: '11px', color: '#64748B' }}>Theo số lượng bán</span>
                </div>

                <Table
                  columns={topColumns}
                  data={topProducts}
                  rowKey="productId"
                  emptyMessage="Chưa có dữ liệu giao dịch"
                />
              </div>
            </div>
          </>
        )}
      </main>

      {/* Modal Refund Invoice */}
      <Modal
        isOpen={showRefundModal}
        onClose={() => {
          setShowRefundModal(false);
          setRefundTarget(null);
        }}
        title={`Xác nhận trả hàng cho hóa đơn #${refundTarget?.id}`}
        width="440px"
      >
        <form onSubmit={handleProcessRefund}>
          <p style={{ color: '#F8FAFC', fontSize: '14px', marginBottom: '12px' }}>
            Bạn đang thao tác hoàn trả hóa đơn giá trị:{' '}
            <strong style={{ color: '#10B981' }}>{formatCurrency(refundTarget?.totalAmount || 0)}</strong>.
          </p>
          <div style={{ backgroundColor: '#0F172A', padding: '12px', borderRadius: '6px', marginBottom: '14px', border: '1px solid #334155' }}>
            <p style={{ fontSize: '12px', color: '#F59E0B', margin: 0 }}>
              ⚠️ Toàn bộ số lượng sản phẩm trong đơn sẽ được <strong>hoàn trả tự động vào kho hàng nguyên tử</strong>. Điểm tích lũy thành viên đã tặng cũng sẽ được thu hồi.
            </p>
          </div>

          <div style={{ marginBottom: '16px' }}>
            <label style={{ display: 'block', fontSize: '12px', color: '#94A3B8', marginBottom: '6px' }}>
              Lý do trả hàng / ghi chú hoàn tiền:
            </label>
            <input
              type="text"
              placeholder="vd: Hàng bị lỗi bao bì / Khách đổi ý..."
              style={{
                width: '100%',
                backgroundColor: '#0F172A',
                border: '1px solid #475569',
                borderRadius: '6px',
                padding: '8px 12px',
                color: '#F8FAFC',
                boxSizing: 'border-box'
              }}
              value={refundReason}
              onChange={(e) => setRefundReason(e.target.value)}
              required
            />
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px' }}>
            <Button
              type="button"
              variant="ghost"
              onClick={() => {
                setShowRefundModal(false);
                setRefundTarget(null);
              }}
            >
              Hủy
            </Button>
            <Button type="submit" variant="danger">
              Xác nhận Hoàn tiền & Hoàn kho
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
