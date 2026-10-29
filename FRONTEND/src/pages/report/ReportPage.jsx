import { useState, useEffect, useMemo } from 'react';
import { Button, Table, Badge, Modal, Loading } from '../../components/common';
import { formatCurrency, formatNumber, formatDateTime } from '../../utils/formatters';
import { reportService } from '../../services/reportService';
import { saleService } from '../../services/saleService';
import { exportToCsv } from '../../utils/exportCsv';
import useBranches, { branchNameOf } from '../../hooks/useBranches';
import './ReportPage.css';

export default function ReportPage({
  currentUser,
  branchId,
  branchName,
  isOnline = true
}) {
  // Quản trị xem được mọi chi nhánh ('' = tất cả); quản lý chỉ xem chi nhánh đang đăng nhập
  const isAdmin = currentUser?.role === 'ADMIN';
  const branches = useBranches();
  const [selectedBranchId, setSelectedBranchId] = useState(isAdmin ? '' : branchId);
  const scopeLabel = selectedBranchId
    ? branchNameOf(branches, selectedBranchId) || branchName
    : 'Tất cả chi nhánh';
  const [days, setDays] = useState(7);
  const [loading, setLoading] = useState(false);
  const [viewMode, setViewMode] = useState('charts'); // 'charts' | 'invoices' (lịch sử ca đã chuyển sang tab Ca làm)
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
        i.employeeName || '',
        i.customer ? i.customer.name : 'Khách lẻ',
        i.totalAmount,
        i.paymentMethod,
        i.status === 'REFUNDED' ? 'Đã hoàn trả' : 'Đã thanh toán',
        i.note || ''
      ]);
      exportToCsv(`DanhSachHoaDon_CN${selectedBranchId || 'TatCa'}`, headers, rows);
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
      exportToCsv(`BaoCaoSanPhamBanChay_CN${selectedBranchId || 'TatCa'}`, headers, rows);
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
        <span style={{ fontWeight: 700, color: idx < 3 ? 'var(--primary-600)' : 'var(--text-muted)' }}>
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
        <span className="font-bold tabular-nums" style={{ color: 'var(--primary-600)' }}>
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
        <span className="tabular-nums font-semibold" style={{ color: 'var(--text-main)' }}>
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
      render: (val, i) => <strong style={{ color: 'var(--text-main)', fontFamily: 'monospace' }}>#{val || i?.id}</strong>
    },
    {
      key: 'createdAt',
      title: 'Thời gian',
      width: '150px',
      render: (val, i) => {
        const dt = val || i?.createdAt;
        return dt ? formatDateTime(dt) : '—';
      }
    },
    {
      key: 'customer',
      title: 'Khách hàng',
      render: (val, i) => {
        const cust = val || i?.customer;
        return cust ? `${cust.name} (${cust.phone})` : 'Khách lẻ';
      }
    },
    {
      key: 'employeeName',
      title: 'Nhân viên',
      render: (val, i) => (
        <div>
          <div>{val || '—'}</div>
          {i?.status === 'REFUNDED' && i?.refundedByName && (
            <small style={{ color: 'var(--text-muted)' }}>Hoàn trả: {i.refundedByName}</small>
          )}
        </div>
      )
    },
    {
      key: 'totalAmount',
      title: 'Tổng tiền',
      align: 'right',
      render: (val, i) => {
        const total = val ?? i?.totalAmount ?? 0;
        return <strong style={{ color: 'var(--primary-600)' }}>{formatCurrency(total)}</strong>;
      }
    },
    {
      key: 'paymentMethod',
      title: 'Hình thức',
      align: 'center',
      render: (val, i) => <Badge variant="secondary">{val || i?.paymentMethod}</Badge>
    },
    {
      key: 'status',
      title: 'Trạng thái',
      align: 'center',
      render: (val, i) => {
        const st = val || i?.status;
        return (
          <Badge variant={st === 'REFUNDED' ? 'danger' : 'success'}>
            {st === 'REFUNDED' ? 'Đã hoàn trả' : 'Đã thanh toán'}
          </Badge>
        );
      }
    },
    {
      key: 'note',
      title: 'Ghi chú',
      render: (val, i) => <span style={{ fontSize: '12px', color: 'var(--text-muted)' }}>{val || i?.note || '—'}</span>
    },
    {
      key: 'actions',
      title: 'Thao tác',
      align: 'center',
      render: (_, i) => {
        const st = i?.status;
        return (
          <div style={{ display: 'flex', gap: '6px', justifyContent: 'center' }}>
            {st !== 'REFUNDED' ? (
              <Button
                size="sm"
                variant="danger"
                onClick={() => {
                  setRefundTarget(i);
                  setShowRefundModal(true);
                }}
                title="Thực hiện hoàn trả hàng và hoàn tiền cho khách"
              >
                Trả hàng
              </Button>
            ) : (
              <span style={{ fontSize: '11px', color: 'var(--danger-600)' }}>Đã hoàn kho</span>
            )}
          </div>
        );
      }
    }
  ];

  return (
    <div className="report-container">
      {/* Header */}
      <header className="page-header">
        <h1 className="page-title">Báo cáo doanh thu</h1>
        <span className="page-subtitle">{scopeLabel}</span>
      </header>

      {/* Main Body */}
      <main className="report-body">
        {/* Filter Bar */}
        <div className="report-filter-bar">
          <div className="report-filter-group">
            <span className="report-filter-label">Chi nhánh:</span>
            {isAdmin ? (
              <select
                className="report-select"
                value={selectedBranchId}
                onChange={(e) => setSelectedBranchId(e.target.value ? Number(e.target.value) : '')}
              >
                <option value="">Tất cả chi nhánh</option>
                {branches.map((b) => (
                  <option key={b.id} value={b.id}>{b.name}</option>
                ))}
              </select>
            ) : (
              <strong title="Đăng nhập lại để đổi chi nhánh">{branchName}</strong>
            )}
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
              Xuất CSV
            </Button>

            <Button
              variant="secondary"
              size="sm"
              onClick={() => window.print()}
              title="In báo cáo này ra giấy hoặc PDF"
            >
              In Báo Cáo
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
            Biểu Đồ & Top Sản Phẩm Bán Chạy
          </button>
          <button
            type="button"
            className={`report-period-btn ${viewMode === 'invoices' ? 'active' : ''}`}
            onClick={() => setViewMode('invoices')}
          >
            Lịch Sử Hóa Đơn & Trả Hàng ({invoices.length})
          </button>
        </div>

        {viewMode === 'invoices' ? (
          /* Invoices List Table */
          <div className="report-card">
            <div className="report-card-title">
              <span>Lịch Sử Giao Dịch & Hóa Đơn Bán Hàng</span>
              <span style={{ fontSize: '12px', color: 'var(--text-muted)' }}>{scopeLabel}</span>
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
                </div>
                <div className="report-kpi-value" style={{ color: 'var(--primary-600)' }}>
                  {loading ? <Loading size="sm" inline text="" /> : formatCurrency(summaryData.totalRevenue)}
                </div>
                <div className="report-kpi-sub">Giá trị bán ra thực tế sau chiết khấu</div>
              </div>

              <div className="report-kpi-card">
                <div className="report-kpi-title">
                  <span>Tiền Vốn Hàng Bán</span>
                </div>
                <div className="report-kpi-value" style={{ color: 'var(--text-muted)' }}>
                  {loading ? <Loading size="sm" inline text="" /> : formatCurrency(summaryData.totalCost)}
                </div>
                <div className="report-kpi-sub">Theo giá nhập kho bình quân</div>
              </div>

              <div className="report-kpi-card">
                <div className="report-kpi-title">
                  <span>Lợi Nhuận Gộp</span>
                </div>
                <div className="report-kpi-value" style={{ color: 'var(--text-main)' }}>
                  {loading ? <Loading size="sm" inline text="" /> : formatCurrency(summaryData.grossProfit)}
                </div>
                <div className="report-kpi-sub">
                  Tỷ suất lợi nhuận: <strong>{loading ? '—' : `${summaryData.profitMarginPercent}%`}</strong>
                </div>
              </div>

              <div className="report-kpi-card">
                <div className="report-kpi-title">
                  <span>Tổng Đơn & AOV</span>
                </div>
                <div className="report-kpi-value" style={{ color: 'var(--warning-600)' }}>
                  {loading ? <Loading size="sm" inline text="" /> : `${formatNumber(summaryData.totalOrders)} đơn`}
                </div>
                <div className="report-kpi-sub">
                  Giá trị TB/đơn: <strong>{loading ? '—' : formatCurrency(summaryData.averageOrderValue)}</strong>
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
                      <div className="report-legend-dot" style={{ backgroundColor: 'var(--primary-600)' }}></div>
                      <span>Doanh thu</span>
                    </div>
                    <div className="report-legend-item">
                      <div className="report-legend-dot" style={{ backgroundColor: 'var(--warning-600)' }}></div>
                      <span>Lợi nhuận</span>
                    </div>
                  </div>
                </div>

                <div className="report-chart-wrapper">
                  {loading ? (
                    <div style={{ height: 200, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                      <Loading text="Đang tải biểu đồ xu hướng..." size="md" />
                    </div>
                  ) : (
                    <svg className="report-chart-svg" viewBox="0 0 600 200" preserveAspectRatio="none">
                    <line x1="0" y1="40" x2="600" y2="40" stroke="var(--border-subtle)" strokeDasharray="3 3" />
                    <line x1="0" y1="100" x2="600" y2="100" stroke="var(--border-subtle)" strokeDasharray="3 3" />
                    <line x1="0" y1="160" x2="600" y2="160" stroke="var(--border-subtle)" strokeDasharray="3 3" />

                    {summaryData.dailyTrend.map((d, idx) => {
                      const totalBars = summaryData.dailyTrend.length;
                      const slotWidth = 600 / totalBars;
                      const xCenter = slotWidth * idx + slotWidth / 2;
                      // Thu hẹp cột và giãn nhãn ngày khi khoảng thời gian dài (30/90 ngày) để không chồng lên nhau
                      const barWidth = Math.min(12, slotWidth * 0.4);
                      const labelStep = Math.ceil(totalBars / 10);
                      const showLabel = idx % labelStep === 0 || idx === totalBars - 1;

                      const revHeight = Math.max(4, ((Number(d.revenue) || 0) / maxRevenue) * 140);
                      const profHeight = Math.max(2, ((Number(d.profit) || 0) / maxRevenue) * 140);

                      const revY = 160 - revHeight;
                      const profY = 160 - profHeight;

                      return (
                        <g key={idx}>
                          <rect
                            x={xCenter - barWidth - 1}
                            y={revY}
                            width={barWidth}
                            height={revHeight}
                            fill="var(--primary-600)"
                            rx="2"
                          >
                            <title>{`${d.date}: Doanh thu ${formatCurrency(d.revenue)} (${d.orderCount} đơn)`}</title>
                          </rect>

                          <rect
                            x={xCenter + 1}
                            y={profY}
                            width={barWidth}
                            height={profHeight}
                            fill="var(--warning-600)"
                            rx="2"
                          >
                            <title>{`${d.date}: Lợi nhuận ${formatCurrency(d.profit)}`}</title>
                          </rect>

                          {showLabel && (
                            <text
                              x={xCenter}
                              y="180"
                              fill="var(--text-muted)"
                              fontSize="10"
                              textAnchor="middle"
                            >
                              {d.date}
                            </text>
                          )}
                        </g>
                      );
                    })}
                  </svg>
                  )}
                </div>
              </div>

              {/* Top Selling Goods Table */}
              <div className="report-card">
                <div className="report-card-title">
                  <span>Top Sản Phẩm Bán Chạy Nhất</span>
                  <span style={{ fontSize: '11px', color: 'var(--text-muted)' }}>Theo số lượng bán</span>
                </div>

                <Table
                  columns={topColumns}
                  data={topProducts}
                  loading={loading}
                  loadingText="Đang tải danh sách bán chạy..."
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
          <p style={{ color: 'var(--text-main)', fontSize: '14px', marginBottom: '12px' }}>
            Bạn đang thao tác hoàn trả hóa đơn giá trị:{' '}
            <strong style={{ color: 'var(--primary-600)' }}>{formatCurrency(refundTarget?.totalAmount || 0)}</strong>.
          </p>
          <div style={{ backgroundColor: 'var(--bg-canvas)', padding: '12px', borderRadius: '6px', marginBottom: '14px', border: '1px solid var(--border-subtle)' }}>
            <p style={{ fontSize: '12px', color: 'var(--warning-600)', margin: 0 }}>
              Toàn bộ số lượng sản phẩm trong đơn sẽ được <strong>hoàn trả tự động vào kho hàng nguyên tử</strong>. Điểm tích lũy thành viên đã tặng cũng sẽ được thu hồi.
            </p>
          </div>

          <div style={{ marginBottom: '16px' }}>
            <label style={{ display: 'block', fontSize: '12px', color: 'var(--text-muted)', marginBottom: '6px' }}>
              Lý do trả hàng / ghi chú hoàn tiền:
            </label>
            <input
              type="text"
              placeholder="vd: Hàng bị lỗi bao bì / Khách đổi ý..."
              style={{
                width: '100%',
                backgroundColor: 'var(--bg-canvas)',
                border: '1px solid var(--border-strong)',
                borderRadius: '6px',
                padding: '8px 12px',
                color: 'var(--text-main)',
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
