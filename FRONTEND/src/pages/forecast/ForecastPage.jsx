import { useState, useMemo, useEffect } from 'react';
import { Button, Table, Badge, Loading } from '../../components/common';
import SalesTrendChart from '../../components/forecast/SalesTrendChart';
import {
  predictMovingAverage,
  predictExponentialSmoothing,
  calculateReplenishment
} from '../../utils/forecasting';
import { formatCurrency } from '../../utils/formatters';
import { forecastService } from '../../services/forecastService';
import { productService } from '../../services/productService';
import { reportService } from '../../services/reportService';
import './ForecastPage.css';

export default function ForecastPage({
  branchId = 1,
  isOnline = true,
  onCreateStockInDraft
}) {
  const [loading, setLoading] = useState(true);
  const [products, setProducts] = useState([]);
  const [algorithm, setAlgorithm] = useState('EXP_SMOOTHING'); // 'EXP_SMOOTHING' | 'MOVING_AVG'
  const [forecastDays, setForecastDays] = useState(7); // 7 | 14 | 30
  const [selectedProductId, setSelectedProductId] = useState(null);
  const [backendForecasts, setBackendForecasts] = useState([]);
  const [productForecast, setProductForecast] = useState(null);
  const [revenueSummary, setRevenueSummary] = useState({
    totalRevenue: 0,
    totalOrders: 0,
    averageOrderValue: 0
  });

  // Fetch forecast data and live report metrics from backend database
  useEffect(() => {
    let isMounted = true;
    async function loadData() {
      setLoading(true);
      try {
        const [prodRes, fcRes, revRes] = await Promise.allSettled([
          productService.getProducts(),
          forecastService.getDemandForecast(branchId, forecastDays, algorithm),
          reportService.getRevenueReport(branchId, 7)
        ]);

        const prodList = prodRes.status === 'fulfilled' ? prodRes.value : [];
        const fcList = fcRes.status === 'fulfilled' ? fcRes.value : [];
        const revData = revRes.status === 'fulfilled' ? revRes.value : null;

        if (isMounted) {
          if (Array.isArray(prodList) && prodList.length > 0) {
            setProducts(prodList.map(p => ({
              ...p,
              stock: p.stock ?? 0,
              minThreshold: p.minThreshold ?? 5,
              costPrice: p.costPrice ?? 0,
              price: p.salePrice ?? p.sellingPrice ?? p.price ?? 0
            })));
            if (!selectedProductId && prodList[0]) {
              setSelectedProductId(prodList[0].id);
            }
          }
          if (Array.isArray(fcList) && fcList.length > 0) {
            setBackendForecasts(fcList);
            if (!selectedProductId && fcList[0]) {
              setSelectedProductId(fcList[0].productId);
            }
          }
          if (revData && revData.totalRevenue !== undefined) {
            setRevenueSummary(revData);
          }
        }
      } catch (err) {
        console.error('Error fetching database forecast data:', err);
      } finally {
        if (isMounted) setLoading(false);
      }
    }

    loadData();

    return () => {
      isMounted = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [branchId, forecastDays, algorithm, isOnline]);

  // Dự báo chi tiết của sản phẩm đang chọn (có chuỗi lịch sử bán, SS, ROP, MAE/RMSE)
  useEffect(() => {
    if (!selectedProductId) return undefined;
    let isMounted = true;
    forecastService
      .getProductForecast(selectedProductId, branchId, forecastDays, algorithm)
      .then((fc) => { if (isMounted) setProductForecast(fc); })
      .catch(() => { if (isMounted) setProductForecast(null); });
    return () => {
      isMounted = false;
    };
  }, [selectedProductId, branchId, forecastDays, algorithm, isOnline]);

  // Selected product details
  const selectedProduct = useMemo(() => {
    return products.find((p) => p.id === Number(selectedProductId)) || products[0] || null;
  }, [products, selectedProductId]);

  // Kết quả dự báo của sản phẩm đang chọn: ưu tiên bản chi tiết, sau đó tới danh sách chung
  const selectedForecast = useMemo(() => {
    if (productForecast && productForecast.productId === Number(selectedProductId)) {
      return productForecast;
    }
    return backendForecasts.find(f => f.productId === Number(selectedProductId)) || null;
  }, [productForecast, backendForecasts, selectedProductId]);

  // Actual sales for selected product from DB
  const actualSales = useMemo(() => {
    const history = selectedForecast?.historicalDailySales;
    if (Array.isArray(history) && history.length > 0) {
      return history;
    }
    return new Array(30).fill(0);
  }, [selectedForecast]);

  // Nhãn ngày cho phần lịch sử (ngày cuối là hôm qua, server bỏ ngày hôm nay chưa bán xong)
  const historyLabels = useMemo(() => {
    const labels = [];
    const today = new Date();
    for (let i = actualSales.length - 1; i >= 0; i--) {
      const d = new Date(today);
      d.setDate(today.getDate() - i - 1);
      const dd = String(d.getDate()).padStart(2, '0');
      const mm = String(d.getMonth() + 1).padStart(2, '0');
      labels.push(`${dd}/${mm}`);
    }
    return labels;
  }, [actualSales.length]);

  // Compute Daily Forecast rate based on chosen algorithm
  const dailyForecast = useMemo(() => {
    if (selectedForecast && selectedForecast.dailyDemandForecast !== undefined) {
      return selectedForecast.dailyDemandForecast;
    }
    if (algorithm === 'MOVING_AVG') {
      return predictMovingAverage(actualSales, 7);
    }
    return predictExponentialSmoothing(actualSales, 0.35);
  }, [selectedForecast, actualSales, algorithm]);

  // Generate future daily values & labels for the chart
  const { forecastValues, forecastLabels } = useMemo(() => {
    const values = [];
    const labels = [];
    const today = new Date();

    // Dự báo bắt đầu từ hôm nay, nối tiếp ngày cuối của lịch sử
    for (let i = 0; i < forecastDays; i++) {
      const nextDate = new Date(today);
      nextDate.setDate(today.getDate() + i);
      const dd = String(nextDate.getDate()).padStart(2, '0');
      const mm = String(nextDate.getMonth() + 1).padStart(2, '0');
      labels.push(`${dd}/${mm}`);

      // Exponential Smoothing / Moving Average cho ra một mức nhu cầu/ngày nên đường dự báo là đường ngang
      values.push(Number(Number(dailyForecast).toFixed(1)));
    }

    return { forecastValues: values, forecastLabels: labels };
  }, [dailyForecast, forecastDays]);

  // MAE/RMSE do backend tính trên 7 ngày cuối mà mô hình chưa thấy (AI_for_POS.md mục 6)
  const qualityLabel = {
    GOOD: 'Tốt (sai số ≤ 30%)',
    FAIR: 'Trung bình (sai số ≤ 60%)',
    LOW: 'Thấp (sai số > 60%)',
    INSUFFICIENT_DATA: 'Chưa đủ dữ liệu để đánh giá'
  };

  // Compute replenishment table from live DB backendForecasts
  const replenishmentList = useMemo(() => {
    if (backendForecasts && backendForecasts.length > 0) {
      return backendForecasts.map((fc) => ({
        id: fc.productId,
        name: fc.productName,
        category: fc.category,
        unit: fc.unit,
        currentStock: fc.currentStock,
        costPrice: fc.costPrice,
        dailyRate: fc.dailyDemandForecast,
        totalDemand: fc.totalPredictedDemand,
        suggestedQty: fc.suggestedRestockQty,
        urgency: fc.urgency,
        safetyStock: fc.safetyStock,
        reorderPoint: fc.reorderPoint,
        reason: fc.reason,
        supplierName: 'Nhà cung cấp đối tác'
      }));
    }
    return products.map((prod) => {
      // Ngoại tuyến: không có lịch sử bán nên không đề xuất nhập, chỉ cảnh báo hàng đã hết
      const restockInfo = calculateReplenishment(prod.stock || 0, [], forecastDays, algorithm);
      return {
        id: prod.id,
        name: prod.name,
        category: prod.category,
        unit: prod.unit,
        currentStock: prod.stock || 0,
        costPrice: prod.costPrice || 0,
        dailyRate: restockInfo.dailyRate,
        totalDemand: restockInfo.totalDemand,
        suggestedQty: restockInfo.suggestedQty,
        urgency: restockInfo.urgency,
        safetyStock: restockInfo.safetyStock,
        reorderPoint: restockInfo.reorderPoint,
        supplierName: 'Nhà cung cấp đối tác'
      };
    });
  }, [backendForecasts, products, forecastDays, algorithm]);

  // Mở phiếu nhập điền sẵn ở trang Kho. Dự báo chỉ đề xuất, quản lý xem lại và quyết định lưu phiếu.
  const openStockInDraft = (rows) => {
    if (!onCreateStockInDraft) {
      alert('Không mở được phiếu nhập từ trang này.');
      return;
    }
    onCreateStockInDraft(rows.map((r) => ({ productId: r.id, quantity: r.suggestedQty > 0 ? r.suggestedQty : 1 })));
  };

  const handleRestockClick = (item) => openStockInDraft([item]);

  const restockRows = replenishmentList.filter((r) => r.suggestedQty > 0);
  const handleBulkRestockClick = () => {
    if (restockRows.length === 0) {
      alert('Hiện chưa có mặt hàng nào cần nhập thêm.');
      return;
    }
    openStockInDraft(restockRows);
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
      key: 'reorderPoint',
      title: 'Điểm đặt hàng lại',
      width: '140px',
      align: 'center',
      render: (val, row) => (
        <div className="tabular-nums">
          <span className="font-medium">{val ?? '-'} {row.unit}</span>
          <div className="text-muted" style={{ fontSize: '11px' }}>
            An toàn: {row.safetyStock ?? '-'}
          </div>
        </div>
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
          title={row.reason || ''}
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
        if (val === 'HIGH') return <Badge variant="danger">Khẩn cấp</Badge>;
        if (val === 'MEDIUM') return <Badge variant="warning">Cao</Badge>;
        return <Badge variant="success">Bình thường</Badge>;
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
      <header className="page-header">
        <h1 className="page-title">Dự báo nhu cầu</h1>
        <span className="page-subtitle">Gợi ý nhập hàng dựa trên doanh số 30 ngày gần nhất</span>
      </header>

      {/* Main Workspace */}
      <main className="forecast-body">
        {/* Metric Summary Cards */}
        <section className="forecast-metrics-grid">
          <div className="forecast-metric-card">
            <span className="forecast-metric-label">Doanh thu 7 ngày qua (Thực tế)</span>
            <span className="forecast-metric-val tabular-nums text-success" style={{ fontSize: '22px' }}>
              {loading ? <Loading size="sm" inline text="" /> : formatCurrency(revenueSummary.totalRevenue || 0)}
            </span>
            <span className="forecast-metric-sub">Dữ liệu từ CSDL MySQL</span>
          </div>

          <div className="forecast-metric-card">
            <span className="forecast-metric-label">Số đơn hàng đã bán</span>
            <span className="forecast-metric-val tabular-nums">
              {loading ? <Loading size="sm" inline text="" /> : `${revenueSummary.totalOrders || 0} hóa đơn`}
            </span>
            <span className="forecast-metric-sub">
              {loading ? 'Đang tính toán...' : `Giá trị đơn TB: ${formatCurrency(revenueSummary.averageOrderValue || 0)}`}
            </span>
          </div>

          <div className="forecast-metric-card">
            <span className="forecast-metric-label">Độ sai lệch dự báo (MAE)</span>
            <span className="forecast-metric-val tabular-nums text-primary">
              {loading ? <Loading size="sm" inline text="" /> : (selectedForecast?.mae != null ? `± ${selectedForecast.mae} ${selectedProduct?.unit || ''}` : '—')}
            </span>
            <span className="forecast-metric-sub">
              {selectedForecast?.rmse != null ? `RMSE ${selectedForecast.rmse} • ` : ''}
              Độ tin cậy: {qualityLabel[selectedForecast?.quality] || qualityLabel.INSUFFICIENT_DATA}
            </span>
          </div>

          <div className="forecast-metric-card">
            <span className="forecast-metric-label">Mặt hàng cần nhập gấp</span>
            <span className="forecast-metric-val text-danger">
              {loading ? <Loading size="sm" inline text="" /> : `${replenishmentList.filter((r) => r.urgency === 'HIGH').length} mặt hàng`}
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

          {loading || !selectedProduct ? (
            <div style={{ padding: '60px 20px', background: 'var(--bg-surface)', border: '1px solid var(--border-subtle)', borderRadius: 'var(--radius-sm)' }}>
              <Loading text="Đang tải dữ liệu và phân tích chuỗi thời gian..." size="md" />
            </div>
          ) : (
            <>
              <SalesTrendChart
                actualData={actualSales}
                forecastData={forecastValues}
                actualLabels={historyLabels}
                forecastLabels={forecastLabels}
                unit={selectedProduct.unit}
                title={`Diễn biến doanh số ${actualSales.length} ngày qua & Dự báo ${forecastDays} ngày tới: ${selectedProduct.name}`}
              />
              {selectedForecast?.reason && (
                <p className="text-muted" style={{ fontSize: '12px', margin: '8px 0 0' }}>
                  {selectedForecast.reason}
                </p>
              )}
            </>
          )}
        </section>

        {/* Automated Restock Recommendation Table */}
        <section className="forecast-table-box">
          <div className="forecast-table-header">
            <div>
              <h3 className="forecast-table-title">
                Bảng Gợi Ý Nhập Hàng Tự Động (AI Restock Recommendations)
              </h3>
              <p className="text-muted" style={{ fontSize: '12px', margin: 0 }}>
                Công thức: Đề xuất = ⌈ROP + Nhu cầu/ngày × {forecastDays} ngày − Tồn kho⌉, với ROP = Nhu cầu/ngày × Lead time + Tồn kho an toàn
                (Z × σ × √Lead time). Di chuột lên số đề xuất để xem lý do.
              </p>
            </div>
            <Button
              variant="success"
              size="sm"
              onClick={handleBulkRestockClick}
              title={`Tổng vốn ước tính: ${formatCurrency(restockRows.reduce((sum, r) => sum + r.suggestedQty * (r.costPrice || 0), 0))}`}
            >
              + Tạo phiếu nhập tổng hợp ({restockRows.length} mặt hàng)
            </Button>
          </div>

          <Table
            columns={restockColumns}
            data={replenishmentList}
            loading={loading}
            loadingText="Đang tính toán gợi ý nhập hàng AI..."
            emptyText="Không có gợi ý nhập hàng"
          />
        </section>
      </main>
    </div>
  );
}
