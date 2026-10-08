import React, { useState, useMemo, useEffect } from 'react';
import { Button, Input, Table, Badge } from '../../components/common';
import StockInModal from './StockInModal';
import StockTransferModal from './StockTransferModal';
import StockInDetailModal from './StockInDetailModal';
import {
  INITIAL_PRODUCTS,
  INITIAL_SUPPLIERS,
  INITIAL_BRANCHES,
  INITIAL_STOCK_IN_HISTORY
} from '../../data/mockProducts';
import { formatCurrency, formatNumber } from '../../utils/formatters';
import { productService } from '../../services/productService';
import { inventoryService } from '../../services/inventoryService';
import { purchaseService } from '../../services/purchaseService';
import { websocketService } from '../../services/websocketService';
import { exportToCsv } from '../../utils/exportCsv';
import './InventoryPage.css';

export default function InventoryPage({
  currentUser,
  branchId = 1,
  isOnline = true,
  isWsConnected = false,
  onNavigateToPos,
  onNavigateToForecast,
  onNavigateToReport,
  onNavigateToPartners,
  onNavigateToStaff,
  onLogout
}) {
  // State
  const [products, setProducts] = useState(INITIAL_PRODUCTS);
  const [suppliers, setSuppliers] = useState(INITIAL_SUPPLIERS);
  const [branches] = useState(INITIAL_BRANCHES);
  const [stockInHistory, setStockInHistory] = useState(INITIAL_STOCK_IN_HISTORY);

  const [currentBranchId, setCurrentBranchId] = useState(branchId || 1);
  const [activeTab, setActiveTab] = useState('inventory'); // 'inventory' | 'history'

  // Sync data from Backend
  useEffect(() => {
    let isMounted = true;
    async function loadData() {
      try {
        const [prodList, invList, supList, slipList] = await Promise.all([
          productService.getProducts(),
          inventoryService.getBranchInventory(currentBranchId),
          purchaseService.getSuppliers(),
          purchaseService.getStockIns(currentBranchId)
        ]);

        if (isMounted) {
          if (Array.isArray(prodList) && prodList.length > 0) {
            const stockMap = new Map();
            (invList || []).forEach(inv => stockMap.set(inv.productId || inv.product?.id, inv.quantity));
            const merged = prodList.map(p => ({
              ...p,
              stock: stockMap.has(p.id) ? stockMap.get(p.id) : (p.stock || 50),
              costPrice: p.costPrice || 0,
              salePrice: p.sellingPrice || p.price || 0,
              minThreshold: p.minThreshold || 10
            }));
            setProducts(merged);
          }

          if (Array.isArray(supList) && supList.length > 0) {
            setSuppliers(supList);
          }

          if (Array.isArray(slipList) && slipList.length > 0) {
            const mappedHistory = slipList.map(s => ({
              id: s.code || `NK-${s.id}`,
              supplierId: s.supplier?.id || s.supplierId,
              supplierName: s.supplier?.name || 'Nhà cung cấp',
              branchId: s.branch?.id || currentBranchId,
              branchName: s.branch?.name || `Chi nhánh ${currentBranchId}`,
              createdDate: s.createdDate ? new Date(s.createdDate).toLocaleString('vi-VN') : new Date().toLocaleString('vi-VN'),
              employeeName: s.employee?.fullName || currentUser?.fullName || 'Quản trị viên',
              totalItems: s.items?.reduce((sum, it) => sum + it.quantity, 0) || 0,
              totalCost: s.totalAmount || s.items?.reduce((sum, it) => sum + it.quantity * it.unitCost, 0) || 0,
              items: s.items || [],
              note: s.note || ''
            }));
            setStockInHistory(mappedHistory);
          }
        }
      } catch (err) {
        console.warn('Backend unavailable for inventory, using fallback mock data:', err.message);
      }
    }

    if (isOnline) {
      loadData();
    }

    // Subscribe to STOMP WebSocket inventory updates
    const unsubWs = websocketService.on('inventory-update', (event) => {
      if (event && event.productId) {
        setProducts(prev => prev.map(p => p.id === event.productId ? { ...p, stock: event.newQuantity ?? p.stock } : p));
      }
    });

    return () => {
      isMounted = false;
      unsubWs();
    };
  }, [currentBranchId, isOnline]);

  // Filter states
  const [selectedCategory, setSelectedCategory] = useState('ALL');
  const [stockStatusFilter, setStockStatusFilter] = useState('ALL'); // 'ALL' | 'IN_STOCK' | 'LOW_STOCK' | 'OUT_OF_STOCK'
  const [searchTerm, setSearchTerm] = useState('');

  // Modals state
  const [isStockInOpen, setIsStockInOpen] = useState(false);
  const [isTransferOpen, setIsTransferOpen] = useState(false);
  const [selectedSlip, setSelectedSlip] = useState(null);

  // Categories list
  const categories = useMemo(() => {
    const list = Array.from(new Set(products.map((p) => p.category)));
    return ['ALL', ...list];
  }, [products]);

  // Metrics calculation
  const totalSkus = products.length;
  const lowStockCount = products.filter(
    (p) => p.stock <= p.minThreshold && p.stock > 0
  ).length;
  const outOfStockCount = products.filter((p) => p.stock <= 0).length;
  const totalInventoryValuation = products.reduce(
    (sum, p) => sum + p.stock * p.costPrice,
    0
  );

  // Filtered Products
  const filteredProducts = useMemo(() => {
    return products.filter((p) => {
      // Category filter
      if (selectedCategory !== 'ALL' && p.category !== selectedCategory) {
        return false;
      }
      // Status filter
      if (stockStatusFilter === 'IN_STOCK' && (p.stock <= p.minThreshold || p.stock <= 0)) {
        return false;
      }
      if (stockStatusFilter === 'LOW_STOCK' && (p.stock > p.minThreshold || p.stock <= 0)) {
        return false;
      }
      if (stockStatusFilter === 'OUT_OF_STOCK' && p.stock > 0) {
        return false;
      }
      // Search term
      if (searchTerm.trim()) {
        const term = searchTerm.toLowerCase().trim();
        return (
          p.name.toLowerCase().includes(term) ||
          p.barcode.includes(term) ||
          p.category.toLowerCase().includes(term)
        );
      }
      return true;
    });
  }, [products, selectedCategory, stockStatusFilter, searchTerm]);

  // Handle Save New Stock-In
  const handleSaveStockIn = async (newStockIn) => {
    // 1. Call Backend API if online
    if (isOnline) {
      try {
        const payload = {
          branchId: Number(currentBranchId) || 1,
          supplierId: Number(newStockIn.supplierId) || 1,
          employeeId: currentUser?.userId || 1,
          note: newStockIn.note || '',
          items: newStockIn.items.map(it => ({
            productId: it.productId,
            quantity: it.quantity,
            unitCost: it.unitCost
          }))
        };
        const created = await purchaseService.createStockIn(payload);
        if (created && created.id) {
          newStockIn.id = created.code || `NK-${created.id}`;
        }
      } catch (err) {
        console.warn('Backend createStockIn failed, saved locally:', err.message);
      }
    }

    // 2. Increase stock for each product locally
    setProducts((prev) =>
      prev.map((prod) => {
        const matchingItem = newStockIn.items.find(
          (item) => item.productId === prod.id
        );
        if (matchingItem) {
          return {
            ...prod,
            stock: prod.stock + matchingItem.quantity,
            costPrice: matchingItem.unitCost // Update latest cost price
          };
        }
        return prod;
      })
    );

    // 3. Append to history
    setStockInHistory([newStockIn, ...stockInHistory]);
    alert(`✔ Đã hoàn tất nhập kho phiếu: ${newStockIn.id}! Tồn kho đã được cộng tự động.`);
  };

  // Handle Stock Transfer
  const handleTransfer = ({ productId, quantity, toBranchId, note }) => {
    setProducts((prev) =>
      prev.map((prod) => {
        if (prod.id === productId) {
          return { ...prod, stock: Math.max(0, prod.stock - quantity) };
        }
        return prod;
      })
    );
    const targetBranch = branches.find((b) => b.id === toBranchId);
    alert(
      `Đã chuyển thành công ${quantity} sản phẩm sang ${targetBranch?.name}!`
    );
  };

  // Quick action: Stock-in single product
  const handleQuickStockIn = (product) => {
    const qty = prompt(`Nhập thêm số lượng cho "${product.name}":`, '10');
    if (!qty) return;
    const num = Number(qty);
    if (isNaN(num) || num <= 0) {
      alert('Số lượng không hợp lệ!');
      return;
    }

    const quickSlip = {
      id: `NK-${Date.now().toString().slice(-6)}`,
      supplierId: 1,
      supplierName: suppliers[0]?.name || 'Nhà cung cấp',
      branchId: currentBranchId,
      branchName: branches.find((b) => b.id === currentBranchId)?.name || 'Chi nhánh',
      createdDate: new Date().toLocaleString('vi-VN'),
      employeeName: 'Nguyễn Văn A',
      totalItems: num,
      totalCost: num * product.costPrice,
      items: [
        {
          productId: product.id,
          productName: product.name,
          barcode: product.barcode,
          unit: product.unit,
          quantity: num,
          unitCost: product.costPrice
        }
      ],
      note: 'Nhập nhanh trực tiếp từ bảng tồn kho'
    };

    handleSaveStockIn(quickSlip);
  };

  const handleExportInventory = () => {
    const headers = ['Mã vạch', 'Tên sản phẩm', 'Danh mục', 'ĐVT', 'Tồn kho', 'Ngưỡng an toàn', 'Giá vốn (VND)', 'Giá bán (VND)', 'Tổng giá trị tồn (VND)'];
    const rows = filteredProducts.map((p) => [
      p.barcode,
      p.name,
      p.category,
      p.unit,
      p.stock,
      p.minThreshold,
      p.costPrice,
      p.salePrice,
      (Number(p.stock) || 0) * (Number(p.costPrice) || 0)
    ]);
    exportToCsv(`BaoCaoTonKho_ChiNhanh_${currentBranchId}`, headers, rows);
  };

  // Table columns for Inventory
  const productColumns = [
    {
      key: 'barcode',
      title: 'Mã vạch (Barcode)',
      width: '140px',
      render: (val) => (
        <span style={{ fontFamily: 'var(--font-mono)', fontSize: '12px' }}>
          {val}
        </span>
      )
    },
    {
      key: 'name',
      title: 'Tên hàng hóa & Danh mục',
      render: (val, row) => (
        <div>
          <div className="font-medium">{val}</div>
          <span className="text-muted" style={{ fontSize: '11px' }}>
            {row.category} • ĐVT: <strong>{row.unit}</strong>
          </span>
        </div>
      )
    },
    {
      key: 'stock',
      title: 'Tồn kho thực tế',
      width: '130px',
      align: 'center',
      render: (val, row) => (
        <span
          className="font-bold tabular-nums"
          style={{
            fontSize: '14px',
            color: val <= 0 ? 'var(--danger-600)' : val <= row.minThreshold ? 'var(--warning-600)' : 'var(--text-main)'
          }}
        >
          {formatNumber(val)} {row.unit}
        </span>
      )
    },
    {
      key: 'minThreshold',
      title: 'Ngưỡng an toàn',
      width: '120px',
      align: 'center',
      render: (val, row) => (
        <span className="text-muted tabular-nums">
          {val} {row.unit}
        </span>
      )
    },
    {
      key: 'costPrice',
      title: 'Giá vốn',
      width: '110px',
      align: 'right',
      render: (val) => <span className="tabular-nums">{formatCurrency(val)}</span>
    },
    {
      key: 'salePrice',
      title: 'Giá bán',
      width: '110px',
      align: 'right',
      render: (val) => <span className="tabular-nums font-semibold text-success">{formatCurrency(val)}</span>
    },
    {
      key: 'status',
      title: 'Trạng thái',
      width: '130px',
      align: 'center',
      render: (_, row) => {
        if (row.stock <= 0) return <Badge variant="danger">Hết hàng</Badge>;
        if (row.stock <= row.minThreshold)
          return <Badge variant="warning">Cảnh báo sắp hết</Badge>;
        return <Badge variant="success">Đủ hàng</Badge>;
      }
    },
    {
      key: 'actions',
      title: 'Thao tác',
      width: '140px',
      align: 'center',
      render: (_, row) => (
        <div style={{ display: 'flex', gap: '6px', justifyContent: 'center' }}>
          <Button
            size="sm"
            variant="secondary"
            onClick={() => handleQuickStockIn(row)}
            title="Nhập thêm hàng hóa này"
          >
            + Nhập hàng
          </Button>
        </div>
      )
    }
  ];

  // Table columns for Stock-in History
  const historyColumns = [
    {
      key: 'id',
      title: 'Mã phiếu',
      width: '130px',
      render: (val) => (
        <span style={{ fontFamily: 'var(--font-mono)', fontWeight: 600 }}>
          {val}
        </span>
      )
    },
    {
      key: 'supplierName',
      title: 'Nhà cung cấp',
      render: (val) => <span className="font-medium">{val}</span>
    },
    {
      key: 'createdDate',
      title: 'Thời gian nhập',
      width: '150px',
      align: 'center'
    },
    {
      key: 'employeeName',
      title: 'Người lập phiếu',
      width: '130px'
    },
    {
      key: 'totalItems',
      title: 'Số lượng món',
      width: '110px',
      align: 'center',
      render: (val) => <span className="font-semibold tabular-nums">{val}</span>
    },
    {
      key: 'totalCost',
      title: 'Tổng giá trị nhập',
      width: '140px',
      align: 'right',
      render: (val) => (
        <span className="font-bold tabular-nums text-success">
          {formatCurrency(val)}
        </span>
      )
    },
    {
      key: 'action',
      title: '',
      width: '100px',
      align: 'center',
      render: (_, row) => (
        <Button
          size="sm"
          variant="ghost"
          onClick={() => setSelectedSlip(row)}
        >
          Xem chi tiết
        </Button>
      )
    }
  ];

  return (
    <div className="inv-container">
      {/* Header */}
      <header className="inv-header">
        <div className="inv-header-left">
          <span style={{ fontSize: '20px' }}>📦</span>
          <span className="inv-logo-title">QUẢN LÝ KHO & NHẬP HÀNG</span>
          <select
            className="inv-select"
            value={currentBranchId}
            onChange={(e) => setCurrentBranchId(Number(e.target.value))}
            style={{ height: '32px', backgroundColor: 'var(--brand-800)', color: '#ffffff', borderColor: 'rgba(255,255,255,0.2)' }}
          >
            {branches.map((b) => (
              <option key={b.id} value={b.id} style={{ color: '#000000' }}>
                {b.name}
              </option>
            ))}
          </select>
        </div>

        <div className="inv-header-right">
          <Button
            variant="success"
            size="md"
            onClick={() => setIsStockInOpen(true)}
            title="Tạo phiếu nhập hàng mới từ nhà cung cấp"
          >
            + Tạo phiếu nhập kho (F3)
          </Button>
          <Button
            variant="secondary"
            size="md"
            onClick={() => setIsTransferOpen(true)}
            title="Điều chuyển hàng hóa giữa các chi nhánh"
          >
            🔄 Điều chuyển kho
          </Button>
          <Button
            variant="secondary"
            size="md"
            onClick={onNavigateToForecast}
            title="Chuyển sang màn hình Dự báo AI & Doanh số"
          >
            📈 Dự báo AI
          </Button>
          <Button
            variant="secondary"
            size="md"
            onClick={onNavigateToReport}
            title="Xem báo cáo doanh thu & sản phẩm bán chạy"
          >
            📊 Báo cáo
          </Button>
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
          <Button
            variant="primary"
            size="md"
            onClick={onNavigateToPos}
            title="Quay lại màn hình bán hàng tại quầy"
          >
            🏪 Màn hình Bán hàng (POS)
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
      <main className="inv-body">
        {/* Metric Summary Cards */}
        <section className="inv-metrics-grid">
          <div
            className={`inv-metric-card ${stockStatusFilter === 'ALL' ? 'active' : ''}`}
            onClick={() => setStockStatusFilter('ALL')}
          >
            <span className="inv-metric-label">Tổng mặt hàng (SKUs)</span>
            <span className="inv-metric-val">{totalSkus}</span>
            <span className="inv-metric-sub">Toàn bộ danh mục kinh doanh</span>
          </div>

          <div
            className={`inv-metric-card ${stockStatusFilter === 'LOW_STOCK' ? 'active' : ''}`}
            onClick={() => setStockStatusFilter('LOW_STOCK')}
          >
            <span className="inv-metric-label" style={{ color: 'var(--warning-700)' }}>
              ⚠️ Cảnh báo sắp hết hàng
            </span>
            <span className="inv-metric-val" style={{ color: 'var(--warning-700)' }}>
              {lowStockCount} mặt hàng
            </span>
            <span className="inv-metric-sub">Tồn kho ≤ Ngưỡng an toàn</span>
          </div>

          <div
            className={`inv-metric-card ${stockStatusFilter === 'OUT_OF_STOCK' ? 'active' : ''}`}
            onClick={() => setStockStatusFilter('OUT_OF_STOCK')}
          >
            <span className="inv-metric-label" style={{ color: 'var(--danger-700)' }}>
              ❌ Đã hết hàng
            </span>
            <span className="inv-metric-val" style={{ color: 'var(--danger-700)' }}>
              {outOfStockCount} mặt hàng
            </span>
            <span className="inv-metric-sub">Cần lên phiếu nhập ngay</span>
          </div>

          <div className="inv-metric-card">
            <span className="inv-metric-label">Giá trị tồn kho (Giá vốn)</span>
            <span className="inv-metric-val tabular-nums text-success" style={{ fontSize: '20px' }}>
              {formatCurrency(totalInventoryValuation)}
            </span>
            <span className="inv-metric-sub">Ước tính theo giá nhập mới nhất</span>
          </div>
        </section>

        {/* Toolbar & Filter Options */}
        <section className="inv-toolbar">
          {/* Tab buttons */}
          <div className="inv-tab-switcher">
            <button
              type="button"
              className={`inv-tab-btn ${activeTab === 'inventory' ? 'active' : ''}`}
              onClick={() => setActiveTab('inventory')}
            >
              Danh sách tồn kho ({filteredProducts.length})
            </button>
            <button
              type="button"
              className={`inv-tab-btn ${activeTab === 'history' ? 'active' : ''}`}
              onClick={() => setActiveTab('history')}
            >
              Lịch sử phiếu nhập kho ({stockInHistory.length})
            </button>
          </div>

          {activeTab === 'inventory' && (
            <div className="inv-toolbar-row">
              <div className="inv-filter-group">
                <select
                  className="inv-select"
                  value={selectedCategory}
                  onChange={(e) => setSelectedCategory(e.target.value)}
                >
                  <option value="ALL">Tất cả danh mục</option>
                  {categories
                    .filter((c) => c !== 'ALL')
                    .map((c) => (
                      <option key={c} value={c}>
                        {c}
                      </option>
                    ))}
                </select>

                <select
                  className="inv-select"
                  value={stockStatusFilter}
                  onChange={(e) => setStockStatusFilter(e.target.value)}
                >
                  <option value="ALL">Tất cả trạng thái tồn</option>
                  <option value="IN_STOCK">Đủ hàng an toàn</option>
                  <option value="LOW_STOCK">Cảnh báo sắp hết (≤ min)</option>
                  <option value="OUT_OF_STOCK">Đã hết hàng (0)</option>
                </select>

                {(selectedCategory !== 'ALL' || stockStatusFilter !== 'ALL' || searchTerm) && (
                  <Button
                    size="sm"
                    variant="ghost"
                    onClick={() => {
                      setSelectedCategory('ALL');
                      setStockStatusFilter('ALL');
                      setSearchTerm('');
                    }}
                  >
                    ✕ Xóa bộ lọc
                  </Button>
                )}
              </div>

              <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                <div style={{ width: '300px' }}>
                  <Input
                    placeholder="Tìm theo tên hàng hoặc mã vạch..."
                    prefix="🔍"
                    value={searchTerm}
                    onChange={(e) => setSearchTerm(e.target.value)}
                  />
                </div>
                <Button
                  size="md"
                  variant="secondary"
                  onClick={handleExportInventory}
                  title="Xuất bảng tồn kho ra file Excel/CSV"
                >
                  📥 Xuất CSV
                </Button>
              </div>
            </div>
          )}
        </section>

        {/* Active Content Tab */}
        {activeTab === 'inventory' ? (
          <Table
            columns={productColumns}
            data={filteredProducts}
            emptyText="Không có sản phẩm nào phù hợp với bộ lọc"
          />
        ) : (
          <Table
            columns={historyColumns}
            data={stockInHistory}
            emptyText="Chưa có phiếu nhập kho nào"
          />
        )}
      </main>

      {/* Modals */}
      <StockInModal
        isOpen={isStockInOpen}
        onClose={() => setIsStockInOpen(false)}
        products={products}
        suppliers={suppliers}
        branches={branches}
        currentBranchId={currentBranchId}
        onSaveStockIn={handleSaveStockIn}
      />

      <StockTransferModal
        isOpen={isTransferOpen}
        onClose={() => setIsTransferOpen(false)}
        products={products}
        branches={branches}
        currentBranchId={currentBranchId}
        onTransfer={handleTransfer}
      />

      <StockInDetailModal
        isOpen={!!selectedSlip}
        onClose={() => setSelectedSlip(null)}
        slip={selectedSlip}
      />
    </div>
  );
}
