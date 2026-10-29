import { useState, useMemo, useEffect } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { Button, Input, Table, Badge, BarcodeView, Loading } from '../../components/common';
import StockInModal from './StockInModal';
import StockTransferModal from './StockTransferModal';
import StockInDetailModal from './StockInDetailModal';
import BarcodePrintModal from './BarcodePrintModal';
import ProductFormModal from './ProductFormModal';
import { formatCurrency, formatNumber } from '../../utils/formatters';
import { productService } from '../../services/productService';
import { inventoryService } from '../../services/inventoryService';
import { purchaseService } from '../../services/purchaseService';
import { websocketService } from '../../services/websocketService';
import { exportToCsv } from '../../utils/exportCsv';
import { newRequestId } from '../../utils/requestId';
import useBranches from '../../hooks/useBranches';
import { PERMS } from '../../utils/permissions';
import './InventoryPage.css';

export default function InventoryPage({
  currentUser,
  can = () => true,
  branchId = 1,
  branchName,
  isOnline = true
}) {
  // Chi nhánh của phiên đăng nhập: nhập kho và điều chuyển luôn từ chi nhánh này.
  // Quản trị xem được tồn kho chi nhánh khác nhưng muốn nhập/xuất ở đó thì đăng nhập vào chi nhánh đó.
  const isAdmin = currentUser?.role === 'ADMIN';
  // Thu ngân chỉ xem tồn kho; nhập kho, điều chuyển và lịch sử phiếu nhập dành cho kho/quản lý
  const isCashier = currentUser?.role === 'CASHIER';
  // Thu ngân chỉ xem tồn kho; nhập kho, điều chuyển và lịch sử phiếu nhập dành cho kho/quản lý
  const canEdit = can(PERMS.INVENTORY_EDIT);
  const canViewCost = canEdit && !isCashier;
  // State from Database
  const [products, setProducts] = useState([]);
  const [suppliers, setSuppliers] = useState([]);
  const branches = useBranches([{ id: branchId, name: branchName || `Chi nhánh ${branchId}` }]);
  const [stockInHistory, setStockInHistory] = useState([]);
  const [loading, setLoading] = useState(true);

  const [currentBranchId, setCurrentBranchId] = useState(branchId || 1);
  const viewingSessionBranch = Number(currentBranchId) === Number(branchId);
  const otherBranchHint = 'Đang xem chi nhánh khác. Đăng nhập vào chi nhánh đó để nhập kho hoặc điều chuyển.';
  const [activeTab, setActiveTab] = useState('inventory'); // 'inventory' | 'history'

  // Barcode Printing State
  const [isBarcodeModalOpen, setIsBarcodeModalOpen] = useState(false);
  // Thêm/sửa sản phẩm: product = null là thêm mới
  const [productForm, setProductForm] = useState({ open: false, product: null });
  const [reloadKey, setReloadKey] = useState(0);
  const [syncingBarcodes, setSyncingBarcodes] = useState(false);
  const [selectedBarcodeProduct, setSelectedBarcodeProduct] = useState(null);

  // Sync data from Backend
  useEffect(() => {
    let isMounted = true;
    async function loadData() {
      setLoading(true);
      try {
        const [prodRes, invRes, supRes, slipRes] = await Promise.allSettled([
          productService.getProducts(),
          inventoryService.getBranchInventory(currentBranchId),
          canEdit ? purchaseService.getSuppliers() : Promise.resolve([]),
          canEdit ? purchaseService.getStockIns(currentBranchId) : Promise.resolve([])
        ]);

        const prodList = prodRes.status === 'fulfilled' ? prodRes.value : [];
        const invList = invRes.status === 'fulfilled' ? invRes.value : [];
        const supList = supRes.status === 'fulfilled' ? supRes.value : [];
        const slipList = slipRes.status === 'fulfilled' ? slipRes.value : [];

        if (isMounted) {
          if (Array.isArray(prodList) && prodList.length > 0) {
            const stockMap = new Map();
            const thresholdMap = new Map();
            (invList || []).forEach(inv => {
              const pid = inv.productId || inv.product?.id;
              if (pid) {
                stockMap.set(pid, inv.quantity);
                if (inv.minThreshold !== undefined) {
                  thresholdMap.set(pid, inv.minThreshold);
                }
              }
            });
            const merged = prodList.map(p => ({
              ...p,
              stock: stockMap.has(p.id) ? stockMap.get(p.id) : (p.stock || 0),
              costPrice: p.costPrice || 0,
              salePrice: p.salePrice ?? p.sellingPrice ?? p.price ?? 0,
              minThreshold: thresholdMap.has(p.id) ? thresholdMap.get(p.id) : (p.minThreshold || 5)
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
        console.error('Lỗi khi tải dữ liệu tồn kho từ backend:', err);
      } finally {
        if (isMounted) setLoading(false);
      }
    }

    loadData();

    // Subscribe to STOMP WebSocket inventory updates
    const unsubWs = websocketService.on('inventory-update', (event) => {
      // Chỉ cập nhật tồn của chi nhánh đang xem; máy chủ gửi kèm tồn mới (newQuantity)
      if (event?.productId && event.newQuantity != null && Number(event.branchId) === Number(currentBranchId)) {
        setProducts(prev => prev.map(p => p.id === event.productId ? { ...p, stock: event.newQuantity } : p));
      }
    });

    return () => {
      isMounted = false;
      unsubWs();
    };
  }, [currentBranchId, isOnline, currentUser?.fullName, canEdit, reloadKey]);

  const handleProductSaved = (saved, action) => {
    setProductForm({ open: false, product: null });
    setReloadKey((k) => k + 1);
    if (action === 'created' && saved?.barcode) {
      alert(`Đã thêm sản phẩm "${saved.name}" (mã vạch ${saved.barcode}). Nhập kho để có tồn bán.`);
    }
  };

  const handleSyncBarcodes = async () => {
    if (!window.confirm('Gán mã vạch nội bộ cho các sản phẩm đang mang mã cũ không quét được?')) return;
    setSyncingBarcodes(true);
    try {
      const result = await productService.syncBarcodes();
      alert(
        `Đồng bộ mã vạch xong: kiểm tra ${result?.checked ?? 0} sản phẩm, ` +
          `đổi mã ${result?.updated ?? 0}, bỏ qua ${result?.skipped ?? 0}.`
      );
      setReloadKey((k) => k + 1);
    } catch (err) {
      alert(err.response?.data?.message || 'Không đồng bộ được mã vạch.');
    } finally {
      setSyncingBarcodes(false);
    }
  };

  // Filter states
  const [selectedCategory, setSelectedCategory] = useState('ALL');
  const [stockStatusFilter, setStockStatusFilter] = useState('ALL'); // 'ALL' | 'IN_STOCK' | 'LOW_STOCK' | 'OUT_OF_STOCK'
  const [searchTerm, setSearchTerm] = useState('');

  // Modals state
  const [isStockInOpen, setIsStockInOpen] = useState(false);
  const [stockInModalKey, setStockInModalKey] = useState(0);
  const location = useLocation();
  const navigate = useNavigate();

  // Phiếu nhập điền sẵn từ trang Dự báo: mở modal để người dùng kiểm tra, chỉnh rồi mới lưu
  const draftItems = useMemo(() => {
    const draft = location.state?.stockInDraft;
    if (!Array.isArray(draft) || products.length === 0) return [];
    return draft
      .map(({ productId, quantity }) => {
        const p = products.find((prod) => prod.id === Number(productId));
        return p && {
          productId: p.id,
          productName: p.name,
          barcode: p.barcode,
          unit: p.unit,
          quantity: Math.max(1, Number(quantity) || 1),
          unitCost: p.costPrice || 0
        };
      })
      .filter(Boolean);
  }, [location.state, products]);
  const showDraft = draftItems.length > 0;

  const closeStockIn = () => {
    setIsStockInOpen(false);
    // Bỏ phiếu điền sẵn khỏi lịch sử điều hướng để không mở lại khi quay về trang
    if (location.state?.stockInDraft) navigate('/inventory', { replace: true, state: null });
  };

  const openEmptyStockIn = () => {
    setStockInModalKey((k) => k + 1);
    setIsStockInOpen(true);
  };
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

  // Pagination for large inventory datasets (1900+ products)
  // Trang hiện tại gắn với bộ lọc: đổi bộ lọc thì tự quay về trang 1
  const filterKey = `${selectedCategory}|${stockStatusFilter}|${searchTerm}`;
  const [pageState, setPageState] = useState({ filterKey, page: 1 });
  const currentPage = pageState.filterKey === filterKey ? pageState.page : 1;
  const setCurrentPage = (update) =>
    setPageState({
      filterKey,
      page: typeof update === 'function' ? update(currentPage) : update,
    });
  const pageSize = 50;
  const totalPages = Math.ceil(filteredProducts.length / pageSize) || 1;

  const paginatedProducts = useMemo(() => {
    const start = (currentPage - 1) * pageSize;
    return filteredProducts.slice(start, start + pageSize);
  }, [filteredProducts, currentPage, pageSize]);

  // Handle Save New Stock-In
  const handleSaveStockIn = async (newStockIn) => {
    // 1. Call Backend API if online
    if (isOnline) {
      try {
        // Chi nhánh và người nhập do máy chủ lấy từ phiên đăng nhập
        const payload = {
          supplierId: Number(newStockIn.supplierId) || 1,
          note: newStockIn.note || '',
          clientRequestId: newStockIn.requestId || newRequestId(),
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
        // Server từ chối (ví dụ tài khoản thu ngân không có quyền nhập kho): không cộng kho giả trên giao diện
        if (err.response) {
          alert(`Không thể nhập kho: ${err.response.data?.message || 'Máy chủ từ chối phiếu nhập'}`);
          return false;
        }
        // Mất phản hồi: phiếu có thể đã được lưu. Giữ form để bấm lại với cùng mã, máy chủ sẽ không cộng kho lần hai
        alert(`Chưa nhận được phản hồi từ máy chủ (${err.message}). Hãy bấm lại "Hoàn tất & Nhập Kho" để thử lại, phiếu sẽ không bị nhập trùng.`);
        return false;
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
    alert(`Đã hoàn tất nhập kho phiếu: ${newStockIn.id}! Tồn kho đã được cộng tự động.`);
    return true;
  };

  // Điều chuyển từ chi nhánh đang đăng nhập sang chi nhánh khác
  const handleTransfer = async ({ productId, quantity, toBranchId, note, requestId }) => {
    if (isOnline) {
      try {
        await inventoryService.transfer({ productId, quantity, toBranchId, note, clientRequestId: requestId });
      } catch (err) {
        const hint = err.response ? '' : ' Bấm xác nhận lại để thử, hàng sẽ không bị chuyển hai lần.';
        alert(`Không thể điều chuyển: ${err.response?.data?.message || err.message}.${hint}`);
        return false;
      }
    }
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
    return true;
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
      note: 'Nhập nhanh trực tiếp từ bảng tồn kho',
      requestId: newRequestId()
    };

    handleSaveStockIn(quickSlip);
  };

  const handleExportInventory = () => {
    const headers = canViewCost
      ? ['Mã vạch', 'Tên sản phẩm', 'Danh mục', 'ĐVT', 'Tồn kho', 'Ngưỡng an toàn', 'Giá vốn (VND)', 'Giá bán (VND)', 'Tổng giá trị tồn (VND)']
      : ['Mã vạch', 'Tên sản phẩm', 'Danh mục', 'ĐVT', 'Tồn kho', 'Ngưỡng an toàn', 'Giá bán (VND)'];
    const rows = filteredProducts.map((p) =>
      canViewCost
        ? [
            p.barcode,
            p.name,
            p.category,
            p.unit,
            p.stock,
            p.minThreshold,
            p.costPrice,
            p.salePrice,
            (Number(p.stock) || 0) * (Number(p.costPrice) || 0)
          ]
        : [
            p.barcode,
            p.name,
            p.category,
            p.unit,
            p.stock,
            p.minThreshold,
            p.salePrice
          ]
    );
    exportToCsv(`BaoCaoTonKho_ChiNhanh_${currentBranchId}`, headers, rows);
  };

  // Table columns for Inventory
  const productColumns = [
    {
      key: 'barcode',
      title: 'Mã vạch (Barcode)',
      width: '165px',
      render: (val, row) => (
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <div>
            <div style={{ fontFamily: 'var(--font-mono)', fontSize: '11px', fontWeight: 600 }}>
              {val}
            </div>
            <BarcodeView
              value={val}
              width={1.0}
              height={20}
              displayValue={false}
              margin={0}
            />
          </div>
          <button
            type="button"
            style={{
              background: 'var(--warning-50)',
              border: '1px solid var(--warning-border)',
              borderRadius: '4px',
              padding: '2px 6px',
              cursor: 'pointer',
              color: 'var(--text-main)',
              fontSize: '11px',
              fontWeight: 600
            }}
            title="Xem và in tem mã vạch sản phẩm này"
            onClick={(e) => {
              e.stopPropagation();
              setSelectedBarcodeProduct(row);
              setIsBarcodeModalOpen(true);
            }}
          >
            In
          </button>
        </div>
      )
    },
    {
      key: 'name',
      title: 'Tên hàng hóa & Danh mục',
      render: (val, row) => (
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          {row.imageUrl && (
            <img
              src={row.imageUrl}
              alt=""
              style={{
                width: 38,
                height: 38,
                objectFit: 'contain',
                borderRadius: 4,
                backgroundColor: '#fff',
                border: '1px solid var(--text-main)',
                flexShrink: 0
              }}
              onError={(e) => { e.currentTarget.style.display = 'none'; }}
            />
          )}
          <div>
            <div className="font-medium">{val}</div>
            <span className="text-muted" style={{ fontSize: '11px' }}>
              {row.category} • ĐVT: <strong>{row.unit}</strong>
            </span>
          </div>
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
      width: '190px',
      align: 'center',
      render: (_, row) => (
        <div style={{ display: 'flex', gap: '6px', justifyContent: 'center' }}>
          <Button
            size="sm"
            variant="ghost"
            onClick={() => setProductForm({ open: true, product: row })}
            title="Sửa thông tin, giá bán hoặc ngừng kinh doanh"
          >
            Sửa
          </Button>
          <Button
            size="sm"
            variant="secondary"
            onClick={() => handleQuickStockIn(row)}
            disabled={!viewingSessionBranch}
            title={viewingSessionBranch ? 'Nhập thêm hàng hóa này' : otherBranchHint}
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
      <header className="page-header">
        <h1 className="page-title">Kho hàng</h1>
        {isAdmin ? (
          <select
            className="inv-select"
            value={currentBranchId}
            onChange={(e) => setCurrentBranchId(Number(e.target.value))}
            aria-label="Chi nhánh"
          >
            {branches.map((b) => (
              <option key={b.id} value={b.id}>
                {b.name}
                {Number(b.id) === Number(branchId) ? ' (đang đăng nhập)' : ''}
              </option>
            ))}
          </select>
        ) : (
          <span className="page-subtitle">{branchName}</span>
        )}
        <div className="page-header-spacer" />
        {canEdit && (
        <>
        {isAdmin && (
          <Button
            variant="ghost"
            size="md"
            onClick={handleSyncBarcodes}
            disabled={syncingBarcodes}
            title="Gán mã nội bộ cho sản phẩm mang mã vạch cũ không quét được"
          >
            {syncingBarcodes ? 'Đang đồng bộ...' : 'Đồng bộ mã vạch'}
          </Button>
        )}
        <Button
          variant="secondary"
          size="md"
          onClick={() => setProductForm({ open: true, product: null })}
          title="Thêm sản phẩm mới vào danh mục"
        >
          + Sản phẩm
        </Button>
        <Button
          variant="secondary"
          size="md"
          onClick={() => setIsTransferOpen(true)}
          disabled={!viewingSessionBranch}
          title={viewingSessionBranch ? 'Điều chuyển hàng sang chi nhánh khác' : otherBranchHint}
        >
          Điều chuyển kho
        </Button>
        <Button
          variant="primary"
          size="md"
          onClick={openEmptyStockIn}
          disabled={!viewingSessionBranch}
          title={viewingSessionBranch ? 'Tạo phiếu nhập hàng mới từ nhà cung cấp' : otherBranchHint}
        >
          Tạo phiếu nhập (F3)
        </Button>
        </>
        )}
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
            <span className="inv-metric-val">{loading ? <Loading size="sm" inline text="" /> : totalSkus}</span>
            <span className="inv-metric-sub">Toàn bộ danh mục kinh doanh</span>
          </div>

          <div
            className={`inv-metric-card ${stockStatusFilter === 'LOW_STOCK' ? 'active' : ''}`}
            onClick={() => setStockStatusFilter('LOW_STOCK')}
          >
            <span className="inv-metric-label" style={{ color: 'var(--warning-700)' }}>
              Cảnh báo sắp hết hàng
            </span>
            <span className="inv-metric-val" style={{ color: 'var(--warning-700)' }}>
              {loading ? <Loading size="sm" inline text="" /> : `${lowStockCount} mặt hàng`}
            </span>
            <span className="inv-metric-sub">Tồn kho ≤ Ngưỡng an toàn</span>
          </div>

          <div
            className={`inv-metric-card ${stockStatusFilter === 'OUT_OF_STOCK' ? 'active' : ''}`}
            onClick={() => setStockStatusFilter('OUT_OF_STOCK')}
          >
            <span className="inv-metric-label" style={{ color: 'var(--danger-700)' }}>
              Đã hết hàng
            </span>
            <span className="inv-metric-val" style={{ color: 'var(--danger-700)' }}>
              {loading ? <Loading size="sm" inline text="" /> : `${outOfStockCount} mặt hàng`}
            </span>
            <span className="inv-metric-sub">Cần lên phiếu nhập ngay</span>
          </div>

          {canViewCost && (
            <div className="inv-metric-card">
              <span className="inv-metric-label">Giá trị tồn kho (Giá vốn)</span>
              <span className="inv-metric-val tabular-nums text-success" style={{ fontSize: '20px' }}>
                {loading ? <Loading size="sm" inline text="" /> : formatCurrency(totalInventoryValuation)}
              </span>
              <span className="inv-metric-sub">Ước tính theo giá nhập mới nhất</span>
            </div>
          )}
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
            {canEdit && (
            <button
              type="button"
              className={`inv-tab-btn ${activeTab === 'history' ? 'active' : ''}`}
              onClick={() => setActiveTab('history')}
            >
              Lịch sử phiếu nhập kho ({stockInHistory.length})
            </button>
            )}
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
                    Xóa bộ lọc
                  </Button>
                )}
              </div>

              <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                <div style={{ width: '300px' }}>
                  <Input
                    placeholder="Tìm theo tên hàng hoặc mã vạch..."
                    value={searchTerm}
                    onChange={(e) => setSearchTerm(e.target.value)}
                  />
                </div>
                <Button
                  size="md"
                  variant="primary"
                  onClick={() => {
                    setSelectedBarcodeProduct(null);
                    setIsBarcodeModalOpen(true);
                  }}
                  title="Mở trình in tem mã vạch cho tất cả sản phẩm hoặc theo danh mục"
                >
                  In tem mã vạch
                </Button>
                <Button
                  size="md"
                  variant="secondary"
                  onClick={handleExportInventory}
                  title="Xuất bảng tồn kho ra file Excel/CSV"
                >
                  Xuất CSV
                </Button>
              </div>
            </div>
          )}
        </section>

        {/* Active Content Tab */}
        {activeTab === 'inventory' ? (
          <>
            <Table
              columns={productColumns.filter((col) => {
                if (col.key === 'actions' && !canEdit) return false;
                if (col.key === 'costPrice' && !canViewCost) return false;
                return true;
              })}
              data={paginatedProducts}
              loading={loading}
              loadingText="Đang tải danh sách tồn kho..."
              emptyText="Không có sản phẩm nào phù hợp với bộ lọc"
            />
            {filteredProducts.length > pageSize && (
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '16px', padding: '0 8px' }}>
                <span className="text-muted" style={{ fontSize: '13px' }}>
                  Hiển thị {(currentPage - 1) * pageSize + 1} - {Math.min(currentPage * pageSize, filteredProducts.length)} / {filteredProducts.length} mặt hàng
                </span>
                <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                  <Button
                    size="sm"
                    variant="secondary"
                    disabled={currentPage <= 1}
                    onClick={() => setCurrentPage(p => Math.max(1, p - 1))}
                  >
                    ← Trước
                  </Button>
                  <span style={{ fontSize: '13px', fontWeight: 600 }}>
                    Trang {currentPage} / {totalPages}
                  </span>
                  <Button
                    size="sm"
                    variant="secondary"
                    disabled={currentPage >= totalPages}
                    onClick={() => setCurrentPage(p => Math.min(totalPages, p + 1))}
                  >
                    Sau →
                  </Button>
                </div>
              </div>
            )}
          </>
        ) : (
          <Table
            columns={historyColumns}
            data={stockInHistory}
            loading={loading}
            loadingText="Đang tải lịch sử nhập kho..."
            emptyText="Chưa có phiếu nhập kho nào"
          />
        )}
      </main>

      {/* Modals */}
      <StockInModal
        key={showDraft ? `draft-${location.key}` : `empty-${stockInModalKey}`}
        initialItems={showDraft ? draftItems : null}
        isOpen={isStockInOpen || showDraft}
        onClose={closeStockIn}
        products={products}
        suppliers={suppliers}
        branchId={branchId}
        branchName={branchName}
        onSaveStockIn={handleSaveStockIn}
      />

      <StockTransferModal
        isOpen={isTransferOpen}
        onClose={() => setIsTransferOpen(false)}
        products={products}
        branches={branches}
        currentBranchId={branchId}
        currentBranchName={branchName}
        onTransfer={handleTransfer}
      />

      <StockInDetailModal
        isOpen={!!selectedSlip}
        onClose={() => setSelectedSlip(null)}
        slip={selectedSlip}
      />

      <ProductFormModal
        isOpen={productForm.open}
        onClose={() => setProductForm({ open: false, product: null })}
        product={productForm.product}
        categories={categories.filter((c) => c !== 'ALL' && c)}
        onSaved={handleProductSaved}
      />

      <BarcodePrintModal
        isOpen={isBarcodeModalOpen}
        onClose={() => setIsBarcodeModalOpen(false)}
        products={filteredProducts}
        initialSelectedProduct={selectedBarcodeProduct}
        storeName="BÁCH HÓA POS"
      />
    </div>
  );
}
