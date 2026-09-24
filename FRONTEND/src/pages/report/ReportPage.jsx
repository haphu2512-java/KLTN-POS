import React, { useState, useEffect, useMemo } from 'react';
import { Button, Table, Badge } from '../../components/common';
import { formatCurrency, formatNumber } from '../../utils/formatters';
import { reportService } from '../../services/reportService';
import './ReportPage.css';

const DEFAULT_DAYS_DATA = [
  { date: '18/09', revenue: 1450000, cost: 980000, profit: 470000, orderCount: 24 },
  { date: '19/09', revenue: 1820000, cost: 1220000, profit: 600000, orderCount: 31 },
  { date: '20/09', revenue: 1690000, cost: 1140000, profit: 550000, orderCount: 28 },
  { date: '21/09', revenue: 2150000, cost: 1450000, profit: 700000, orderCount: 36 },
  { date: '22/09', revenue: 2380000, cost: 1600000, profit: 780000, orderCount: 42 },
  { date: '23/09', revenue: 1950000, cost: 1310000, profit: 640000, orderCount: 33 },
  { date: '24/09', revenue: 2540000, cost: 1720000, profit: 820000, orderCount: 45 }
];

const DEFAULT_TOP_PRODUCTS = [
  { productId: 1, barcode: '8934567890123', productName: 'Sữa tươi tiệt trùng Vinamilk 100% Không Đường 1L', quantitySold: 94, totalRevenue: 3384000, estimatedProfit: 846000 },
  { productId: 3, barcode: '8936009876543', productName: 'Nước ngọt Coca-Cola Sleek 320ml', quantitySold: 185, totalRevenue: 2035000, estimatedProfit: 555000 },
  { productId: 2, barcode: '8935001234567', productName: 'Mì ăn liền Hảo Hảo Tôm Chua Cay 75g', quantitySold: 210, totalRevenue: 1008000, estimatedProfit: 252000 },
  { productId: 4, barcode: '8934673123456', productName: 'Dầu ăn Simply Đậu Nành Tinh Luyện 1L', quantitySold: 28, totalRevenue: 1540000, estimatedProfit: 308000 },
  { productId: 5, barcode: '8934822001122', productName: 'Nước khoáng Lavie 500ml', quantitySold: 140, totalRevenue: 840000, estimatedProfit: 280000 }
];

export default function ReportPage({
  currentUser,
  branchId = 1,
  isOnline = true,
  onNavigateToPos,
  onNavigateToInventory,
  onNavigateToForecast,
  onLogout
}) {
  const [selectedBranchId, setSelectedBranchId] = useState(branchId || 1);
  const [days, setDays] = useState(7);
  const [loading, setLoading] = useState(false);

  const [summaryData, setSummaryData] = useState({
    totalRevenue: 13980000,
    totalCost: 9420000,
    grossProfit: 4560000,
    profitMarginPercent: 32.6,
    totalOrders: 239,
    averageOrderValue: 58493,
    dailyTrend: DEFAULT_DAYS_DATA
  });

  const [topProducts, setTopProducts] = useState(DEFAULT_TOP_PRODUCTS);

  useEffect(() => {
    let isMounted = true;
    async function fetchReports() {
      if (!isOnline) return;
      setLoading(true);
      try {
        const [revData, topData] = await Promise.all([
          reportService.getRevenueReport(selectedBranchId, days),
          reportService.getTopProducts(selectedBranchId, days, 10)
        ]);

        if (isMounted) {
          if (revData && revData.totalRevenue) {
            setSummaryData(revData);
          }
          if (Array.isArray(topData) && topData.length > 0) {
            setTopProducts(topData);
          }
        }
      } catch (err) {
        console.warn('Report API fallback to localized metrics:', err.message);
      } finally {
        if (isMounted) setLoading(false);
      }
    }

    fetchReports();
    return () => {
      isMounted = false;
    };
  }, [selectedBranchId, days, isOnline]);

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
          <Button
            variant="secondary"
            size="md"
            onClick={onNavigateToForecast}
            title="Mở màn hình Dự báo AI"
          >
            📈 Dự báo AI
          </Button>

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
              onClick={() => window.print()}
              title="In báo cáo này ra giấy hoặc PDF"
            >
              🖨 In Báo Cáo
            </Button>
          </div>
        </div>

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
                {/* Horizontal grid lines */}
                <line x1="0" y1="40" x2="600" y2="40" stroke="#334155" strokeDasharray="3 3" />
                <line x1="0" y1="100" x2="600" y2="100" stroke="#334155" strokeDasharray="3 3" />
                <line x1="0" y1="160" x2="600" y2="160" stroke="#334155" strokeDasharray="3 3" />

                {/* Bars for Daily Revenue & Profit */}
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
                      {/* Revenue Bar */}
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

                      {/* Profit Bar */}
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

                      {/* Date label */}
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
      </main>
    </div>
  );
}
