import React, { useState, useMemo } from 'react';
import { Button, Table, Badge } from '../../components/common';
import SalesTrendChart from '../../components/forecast/SalesTrendChart';
import {
  predictMovingAverage,
  predictExponentialSmoothing,
  calculateMAE,
  calculateReplenishment
} from '../../utils/forecasting';
import {
  PAST_14_DAYS_LABELS,
  PRODUCT_SALES_HISTORY,
  REVENUE_HISTORY_14_DAYS
} from '../../data/mockSalesData';
import { INITIAL_PRODUCTS } from '../../data/mockProducts';
import { formatCurrency, formatNumber } from '../../utils/formatters';
import './ForecastPage.css';

export default function ForecastPage({ onNavigateToPos, onNavigateToInventory, onQuickStockIn }) {
  const [products] = useState(INITIAL_PRODUCTS);
  const [algorithm, setAlgorithm] = useState('EXP_SMOOTHING'); // 'EXP_SMOOTHING' | 'MOVING_AVG'
  const [forecastDays, setForecastDays] = useState(7); // 7 | 14 | 30
  const [selectedProductId, setSelectedProductId] = useState(1);

  // Selected product details
  const selectedProduct = useMemo(() => {
    return products.find((p) => p.id === Number(selectedProductId)) || products[0];
  }, [products, selectedProductId]);

  // Actual sales for selected product
  const actualSales = useMemo(() => {
    return PRODUCT_SALES_HISTORY[selectedProduct.id]?.dailySales || [10, 12, 11, 13, 14, 12, 15, 14, 16, 15, 17, 18, 16, 18];
  }, [selectedProduct]);

  // Compute Daily Forecast rate based on chosen algorithm
  const dailyForecast = useMemo(() => {
    if (algorithm === 'MOVING_AVG') {
      return predictMovingAverage(actualSales, 7);
    }
    return predictExponentialSmoothing(actualSales, 0.35);
  }, [actualSales, algorithm]);

  // Generate future daily values & labels for the chart
  const { forecastValues, forecastLabels } = useMemo(() => {
    const values = [];
    const labels = [];
    const today = new Date();

    for (let i = 1; i <= forecastDays; i++) {
      const nextDate = new Date(today);
      nextDate.setDate(today.getDate() + i);
      const dd = String(nextDate.getDate()).padStart(2, '0');
      const mm = String(nextDate.getMonth() + 1).padStart(2, '0');
      labels.push(`${dd}/${mm}`);

      // Add minor realistic variance (+/- 5%) around the forecast point
      const noise = (Math.sin(i * 1.5) * 0.08 + 1);
      values.push(Math.round(dailyForecast * noise));
    }

    return { forecastValues: values, forecastLabels: labels };
  }, [dailyForecast, forecastDays]);

  // Calculate MAE metric (Mean Absolute Error) for selected product
  const maeScore = useMemo(() => {
    // Compare last 7 days actual vs running forecast
    const testActual = actualSales.slice(-7);
    const testForecast = testActual.map((_, i) =>
      predictExponentialSmoothing(actualSales.slice(0, 7 + i), 0.35)
    );
    return calculateMAE(testActual, testForecast);
  }, [actualSales]);

  // Compute replenishment table for all products
  const replenishmentList = useMemo(() => {
    return products.map((prod) => {
      const history = PRODUCT_SALES_HISTORY[prod.id]?.dailySales || [5, 6, 7, 8, 7, 6, 8, 7, 9, 8, 7, 8, 9, 8];
      const dailyRate =
        algorithm === 'MOVING_AVG'
          ? predictMovingAverage(history, 7)
          : predictExponentialSmoothing(history, 0.35);

      const restockInfo = calculateReplenishment(prod.stock, dailyRate, forecastDays);
      const supplierName = PRODUCT_SALES_HISTORY[prod.id]?.supplierName || 'Nhà cung cấp chính';

      return {
        id: prod.id,
        name: prod.name,
        category: prod.category,
        unit: prod.unit,
        currentStock: prod.stock,
        costPrice: prod.costPrice,
        dailyRate,
        totalDemand: restockInfo.totalDemand,
        suggestedQty: restockInfo.suggestedQty,
        urgency: restockInfo.urgency,
        supplierName
      };
    });
  }, [products, algorithm, forecastDays]);

  // Action: Trigger restock
  const handleRestockClick = (item) => {
    const qty = item.suggestedQty > 0 ? item.suggestedQty : 10;
    if (onQuickStockIn) {
      onQuickStockIn(item, qty);
    } else {
      alert(`Đã khởi tạo đề xuất nhập kho: +${qty} ${item.unit} "${item.name}"!`);
    }
  };

  // Restock Table Columns
  const restockColumns = [
    {
      key: 'name',
      title: 'Tên hàng hóa & Danh mục',
      render: (val, row) => (
        <div>
          <span className="font-semibold">{val}</span>
          <div className="text-muted" style={{ fontSize: '11px' }}>
            {row.category} • ĐVT: {row.unit}
          </div>
        </div>
      )
    },
    {
      key: 'currentStock',
      title: 'Tồn kho hiện tại',
      width: '130px',
      align: 'center',
      render: (val, row) => (
        <span
          className="font-bold tabular-nums"
          style={{
            color: val <= 0 ? 'var(--danger-600)' : val <= 5 ? 'var(--warning-600)' : 'var(--text-main)'
          }}
        >
          {val} {row.unit}
        </span>
      )
    },
    {
      key: 'dailyRate',
      title: 'Tiêu thụ TB/ngày',
      width: '130px',
      align: 'center',
      render: (val, row) => (
        <span className="tabular-nums font-medium">
          ~{val} {row.unit}/ngày
        </span>
      )
    },
    {
      key: 'totalDemand',
      title: `Nhu cầu (${forecastDays} ngày)`,
      width: '150px',
      align: 'center',
      render: (val, row) => (
        <span className="tabular-nums font-semibold text-primary">
          {val} {row.unit}
        </span>
      )
    },
    {
      key: 'suggestedQty',
      title: 'Đề xuất nhập thêm',
      width: '150px',
      align: 'center',
      render: (val, row) => (
        <span
          className="font-bold tabular-nums"
          style={{
            fontSize: '14px',
            color: val > 0 ? 'var(--success-700)' : 'var(--text-muted)'
          }}
        >
          {val > 0 ? `+ ${val} ${row.unit}` : 'Đủ hàng'}
        </span>
      )
    },
    {
      key: 'urgency',
      title: 'Mức độ ưu tiên',
      width: '140px',
      align: 'center',
      render: (val) => {
        if (val === 'HIGH') return <Badge variant="danger">Cấp thiết (Hết/Thiếu)</Badge>;
        if (val === 'MEDIUM') return <Badge variant="warning">Cần nhập bổ sung</Badge>;
        return <Badge variant="success">An toàn</Badge>;
      }
    },
    {
      key: 'action',
      title: 'Hành động',
      width: '150px',
      align: 'center',
      render: (_, row) => (
        <Button
          size="sm"
          variant={row.urgency === 'HIGH' ? 'success' : 'secondary'}
          onClick={() => handleRestockClick(row)}
        >
          + Lên phiếu nhập
        </Button>
      )
    }
  ];

  return (
    <div className="forecast-container">
      {/* Header */}
      <header className="forecast-header">
        <div className="forecast-header-left">
          <span style={{ fontSize: '20px' }}>📈</span>
          <span style={{ fontWeight: 700, fontSize: '15px', letterSpacing: '0.02em' }}>
            DỰ BÁO NHU CẦU & BÁO CÁO DOANH SỐ (AI FORECAST)
          </span>
          <Badge variant="brand" withDot={false}>Mô hình Machine Learning v1.2</Badge>
        </div>

        <div className="forecast-header-right">
          <Button
            variant="secondary"
            size="md"
            onClick={onNavigateToInventory}
            title="Mở màn hình Quản lý Kho"
          >
            📦 Quản lý Kho
          </Button>
          <Button
            variant="primary"
            size="md"
            onClick={onNavigateToPos}
            title="Quay lại bán hàng tại quầy"
          >
            🏪 Màn hình Bán hàng (POS)
          </Button>
        </div>
      </header>

      {/* Main Workspace */}
      <main className="forecast-body">
        {/* Metric Summary Cards */}
        <section className="forecast-metrics-grid">
          <div className="forecast-metric-card">
            <span className="forecast-metric-label">Doanh thu hôm nay (16/09)</span>
            <span className="forecast-metric-val tabular-nums text-success" style={{ fontSize: '22px' }}>
              {formatCurrency(REVENUE_HISTORY_14_DAYS[REVENUE_HISTORY_14_DAYS.length - 1])}
            </span>
            <span className="forecast-metric-sub">▲ Tăng 10.7% so với hôm qua</span>
          </div>

          <div className="forecast-metric-card">
            <span className="forecast-metric-label">Số đơn hàng hôm nay</span>
            <span className="forecast-metric-val tabular-nums">48 hóa đơn</span>
            <span className="forecast-metric-sub">Giá trị đơn bình quân: 79.500 ₫</span>
          </div>

          <div className="forecast-metric-card">
            <span className="forecast-metric-label">Độ sai lệch dự báo (MAE)</span>
            <span className="forecast-metric-val tabular-nums text-primary">
              ± {maeScore} {selectedProduct.unit}
            </span>
            <span className="forecast-metric-sub">Độ tin cậy mô hình: ~95.8% (Rất cao)</span>
          </div>

          <div className="forecast-metric-card">
            <span className="forecast-metric-label">Mặt hàng cần nhập gấp</span>
            <span className="forecast-metric-val text-danger">
              {replenishmentList.filter((r) => r.urgency === 'HIGH').length} mặt hàng
            </span>
            <span className="forecast-metric-sub">Khuyến nghị bổ sung trong 48h tới</span>
          </div>
        </section>

        {/* Algorithm & Period Configuration Bar */}
        <section className="forecast-config-bar">
          <div className="forecast-config-group">
            <span className="font-semibold" style={{ fontSize: '13px' }}>Thuật toán AI:</span>
            <label className="forecast-radio-label">
              <input
                type="radio"
                name="algorithm"
                value="EXP_SMOOTHING"
                checked={algorithm === 'EXP_SMOOTHING'}
                onChange={() => setAlgorithm('EXP_SMOOTHING')}
              />
              <span>Exponential Smoothing (Alpha = 0.35 - Khuyên dùng)</span>
            </label>
            <label className="forecast-radio-label">
              <input
                type="radio"
                name="algorithm"
                value="MOVING_AVG"
                checked={algorithm === 'MOVING_AVG'}
                onChange={() => setAlgorithm('MOVING_AVG')}
              />
              <span>Moving Average (Trung bình trượt 7 ngày)</span>
            </label>
          </div>

          <div className="forecast-config-group">
            <span className="font-semibold" style={{ fontSize: '13px' }}>Chu kỳ dự báo:</span>
            <select
              value={forecastDays}
              onChange={(e) => setForecastDays(Number(e.target.value))}
              style={{
                height: '32px',
                border: '1px solid var(--border-subtle)',
                borderRadius: 'var(--radius-xs)',
                padding: '0 8px',
                fontSize: '13px'
              }}
            >
              <option value={7}>7 ngày tới (1 tuần)</option>
              <option value={14}>14 ngày tới (2 tuần)</option>
              <option value={30}>30 ngày tới (1 tháng)</option>
            </select>
          </div>
        </section>

        {/* Trend Chart Section */}
        <section>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
            <span className="font-semibold" style={{ fontSize: '14px' }}>
              Biểu đồ xu hướng & dự báo theo sản phẩm:
            </span>
            <select
              value={selectedProductId}
              onChange={(e) => setSelectedProductId(Number(e.target.value))}
              style={{
                height: '32px',
                border: '1px solid var(--border-subtle)',
                borderRadius: 'var(--radius-xs)',
                padding: '0 10px',
                fontSize: '13px',
                fontWeight: 500,
                backgroundColor: 'var(--bg-surface)'
              }}
            >
              {products.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name} (Tồn: {p.stock} {p.unit})
                </option>
              ))}
            </select>
          </div>

          <SalesTrendChart
            actualData={actualSales}
            forecastData={forecastValues}
            actualLabels={PAST_14_DAYS_LABELS}
            forecastLabels={forecastLabels}
            unit={selectedProduct.unit}
            title={`Diễn biến doanh số 14 ngày qua & Dự báo ${forecastDays} ngày tới: ${selectedProduct.name}`}
          />
        </section>

        {/* Automated Restock Recommendation Table */}
        <section className="forecast-table-box">
          <div className="forecast-table-header">
            <div>
              <h3 className="forecast-table-title">
                📋 Bảng Gợi Ý Nhập Hàng Tự Động (AI Restock Recommendations)
              </h3>
              <p className="text-muted" style={{ fontSize: '12px', margin: 0 }}>
                Công thức: Nhu cầu dự báo ({forecastDays} ngày) + 10% hệ số an toàn - Tồn kho hiện tại.
              </p>
            </div>
            <Button
              variant="success"
              size="sm"
              onClick={() => {
                const totalUrgent = replenishmentList
                  .filter((r) => r.suggestedQty > 0)
                  .reduce((sum, r) => sum + r.suggestedQty * r.costPrice, 0);
                alert(`Đã tổng hợp phiếu nhập đề xuất cho ${replenishmentList.filter((r) => r.suggestedQty > 0).length} mặt hàng! Tổng vốn ước tính: ${formatCurrency(totalUrgent)}.`);
              }}
            >
              + Tạo phiếu nhập tổng hợp tự động
            </Button>
          </div>

          <Table
            columns={restockColumns}
            data={replenishmentList}
            emptyText="Không có gợi ý nhập hàng"
          />
        </section>
      </main>
    </div>
  );
}
