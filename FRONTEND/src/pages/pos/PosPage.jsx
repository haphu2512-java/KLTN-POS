import { useState, useMemo, useCallback, useEffect, useRef } from 'react';
import PosHeader from './PosHeader';
import PosCart from './PosCart';
import PosPayment from './PosPayment';
import ProductSearchModal from './ProductSearchModal';
import CustomerModal from './CustomerModal';
import VietQRModal from './VietQRModal';
import ScannerPairingModal from './ScannerPairingModal';
import ShiftModal from './ShiftModal';
import CashMovementModal from './CashMovementModal';
import RecentInvoicesModal from './RecentInvoicesModal';
import ManagerApprovalModal from './ManagerApprovalModal';
import { Modal, Button } from '../../components/common';
import { useBarcodeScanner } from '../../hooks/useBarcodeScanner';
import { useHotkeys } from '../../hooks/useHotkeys';
import { playBeepSuccess, playBeepWarning } from '../../utils/audio';
import { formatCurrency } from '../../utils/formatters';
import { printInvoiceReceipt, receiptFromInvoice } from '../../utils/receiptPrinter';
import { productService } from '../../services/productService';
import { inventoryService } from '../../services/inventoryService';
import { customerService } from '../../services/customerService';
import { saleService } from '../../services/saleService';
import { websocketService } from '../../services/websocketService';
import { scannerPairingService } from '../../services/scannerPairingService';
import { shiftService } from '../../services/shiftService';
import { approvalService } from '../../services/approvalService';
import { newRequestId } from '../../utils/requestId';
import './PosPage.css';


function emptyOrder() {
  return {
    items: [],
    customer: null,
    discount: 0,
    discountReason: '',
    // Mã duyệt sau khi quản lý nhập PIN: { token, approverName, maxDiscount }
    discountApproval: null,
    paymentMethod: 'CASH',
    receivedAmount: 0,
    note: '',
    requestId: newRequestId()
  };
}

export default function PosPage({
  currentUser,
  branchId = 1,
  branchName,
  isOnline = true,
  isWsConnected = false
}) {
  const branchLabel = branchName || `Chi nhánh ${branchId || 1}`;
  // Products & Customers from Database
  const [products, setProducts] = useState([]);
  const [customers, setCustomers] = useState([]);
  const [loadingProducts, setLoadingProducts] = useState(true);

  // Sync products, inventory, and customers from backend database
  useEffect(() => {
    let isMounted = true;
    async function loadBackendData() {
      setLoadingProducts(true);
      try {
        const [prodRes, invRes, custRes] = await Promise.allSettled([
          productService.getProducts(),
          inventoryService.getBranchInventory(branchId),
          customerService.getCustomers()
        ]);
        const rawProds = prodRes.status === 'fulfilled' ? prodRes.value : [];
        const prodList = Array.isArray(rawProds) ? rawProds : (rawProds?.data || []);

        const rawInvs = invRes.status === 'fulfilled' ? invRes.value : [];
        const invList = Array.isArray(rawInvs) ? rawInvs : (rawInvs?.data || []);

        const rawCusts = custRes.status === 'fulfilled' ? custRes.value : [];
        const custList = Array.isArray(rawCusts) ? rawCusts : (rawCusts?.data || []);

        if (prodRes.status === 'rejected') {
          console.error('Lỗi tải sản phẩm từ backend:', prodRes.reason);
        }

        if (isMounted && Array.isArray(prodList) && prodList.length > 0) {
          // Merge stock from inventory into products
          const stockMap = new Map();
          (invList || []).forEach(inv => stockMap.set(inv.productId || inv.product?.id, inv.quantity));
          const merged = prodList.map(p => ({
            ...p,
            stock: stockMap.has(p.id) ? stockMap.get(p.id) : (p.stock || 0),
            price: p.salePrice ?? p.sellingPrice ?? p.price ?? 0,
            costPrice: p.costPrice || 0,
            minThreshold: p.minThreshold || 5
          }));
          setProducts(merged);
        }

        if (isMounted && Array.isArray(custList) && custList.length > 0) {
          setCustomers(custList);
        }
      } catch (err) {
        console.error('Error loading data from database:', err);
      } finally {
        if (isMounted) setLoadingProducts(false);
      }
    }

    loadBackendData();

    // Subscribe to STOMP WebSocket inventory updates
    const unsubWs = websocketService.on('inventory-update', (event) => {
      // Chỉ cập nhật tồn của chi nhánh đang xem; máy chủ gửi kèm tồn mới (newQuantity)
      if (event?.productId && event.newQuantity != null && Number(event.branchId) === Number(branchId)) {
        setProducts(prev => prev.map(p => p.id === event.productId ? { ...p, stock: event.newQuantity } : p));
      }
    });

    return () => {
      isMounted = false;
      unsubWs();
    };
  }, [branchId, isOnline]);

  // Multi-order Tabs State
  const [tabs, setTabs] = useState([
    { id: 'order-1', label: 'Đơn #01', closable: false }
  ]);
  const [activeTabId, setActiveTabId] = useState('order-1');

  // Orders State Dict: initial order starts empty ready for scanning/adding
  const [orders, setOrders] = useState(() => ({ 'order-1': emptyOrder() }));
  const [isProcessing, setIsProcessing] = useState(false);
  // Ref chặn bấm hai lần trước khi state kịp cập nhật (F4 + click, double click)
  const processingRef = useRef(false);

  // Modals state
  const [isSearchOpen, setIsSearchOpen] = useState(false);
  const [isCustomerOpen, setIsCustomerOpen] = useState(false);
  const [isVietQROpen, setIsVietQROpen] = useState(false);
  const [isHelpOpen, setIsHelpOpen] = useState(false);
  const [isPairingOpen, setIsPairingOpen] = useState(false);
  const [isShiftOpen, setIsShiftOpen] = useState(false);
  const [isCashOpen, setIsCashOpen] = useState(false);
  const [isRecentInvoicesOpen, setIsRecentInvoicesOpen] = useState(false);
  // Ca đang trực của tài khoản: { session, shiftRequired, suggested..., previousShift }
  const [shiftInfo, setShiftInfo] = useState(null);
  // Lý do mở hộp ca: 'sale' khi thu ngân bán hàng lúc chưa mở ca, null khi tự bấm nút Mở ca / Đóng ca
  const [shiftPrompt, setShiftPrompt] = useState(null);
  // Chỉ nhắc mở ca một lần khi thêm hàng vào giỏ, các lần sau đã có dải thông báo trên màn hình
  const shiftPromptedRef = useRef(false);
  // Quầy (máy POS) này; điện thoại ghép cặp với quầy để bắn mã vào giỏ
  const [terminal, setTerminal] = useState(null);
  const [pairedCount, setPairedCount] = useState(0);
  // Các mã quét offline trên điện thoại vừa đồng bộ lên, chờ thu ngân xác nhận
  const [remoteBatch, setRemoteBatch] = useState(null);
  const [barcodeInput, setBarcodeInput] = useState('');

  // Current active order data
  const currentOrder = useMemo(() => {
    return (
      orders[activeTabId] || {
        items: [],
        customer: null,
        discount: 0,
        paymentMethod: 'CASH',
        receivedAmount: 0,
        note: ''
      }
    );
  }, [orders, activeTabId]);

  // Calculations
  const subtotal = useMemo(() => {
    return currentOrder.items.reduce(
      (sum, item) => sum + item.price * item.quantity,
      0
    );
  }, [currentOrder.items]);

  const finalAmount = useMemo(() => {
    return Math.max(0, subtotal - (currentOrder.discount || 0));
  }, [subtotal, currentOrder.discount]);

  // Hạn mức giảm giá của thu ngân (ADMIN/MANAGER không giới hạn)
  const isCashier = currentUser?.role === 'CASHIER';
  const [cashierMaxDiscountPercent, setCashierMaxDiscountPercent] = useState(null);
  const discountLimit = isCashier && cashierMaxDiscountPercent != null
    ? Math.floor((subtotal * cashierMaxDiscountPercent) / 100)
    : null;
  const isOverDiscountLimit = (order) => discountLimit != null && (order.discount || 0) > discountLimit;
  const isDiscountApproved = (order) => Number(order.discountApproval?.maxDiscount || 0) >= (order.discount || 0);
  const discountOverLimit = isOverDiscountLimit(currentOrder);
  const discountApproved = isDiscountApproved(currentOrder);
  const [isApprovalOpen, setIsApprovalOpen] = useState(false);

  const changeAmount = useMemo(() => {
    if (currentOrder.paymentMethod !== 'CASH') return 0;
    return (currentOrder.receivedAmount || 0) - finalAmount;
  }, [currentOrder.paymentMethod, currentOrder.receivedAmount, finalAmount]);

  // Order state updater
  const updateCurrentOrder = useCallback(
    (updater) => {
      setOrders((prev) => ({
        ...prev,
        [activeTabId]: {
          ...prev[activeTabId],
          ...(typeof updater === 'function' ? updater(prev[activeTabId]) : updater)
        }
      }));
    },
    [activeTabId]
  );

  // Chưa mở ca thì vẫn xem, tra cứu, chuẩn bị đơn được; chỉ thanh toán mới cần ca (backend cũng chặn)
  const needsShift = !!(isOnline && shiftInfo?.shiftRequired && !shiftInfo.session);

  // Đóng ca xong rồi bán tiếp thì nhắc lại một lần nữa
  useEffect(() => {
    if (!needsShift) shiftPromptedRef.current = false;
  }, [needsShift]);

  const promptOpenShift = useCallback(() => {
    setShiftPrompt('sale');
    setIsShiftOpen(true);
  }, []);

  // Add Product to Cart by Barcode or selection
  const handleAddProduct = useCallback(
    (product) => {
      if (!product) return;
      if (needsShift && !shiftPromptedRef.current) {
        shiftPromptedRef.current = true;
        promptOpenShift();
      }
      if (product.stock <= 0) {
        playBeepWarning();
        alert(`Sản phẩm "${product.name}" đã hết hàng trong kho!`);
        return;
      }

      updateCurrentOrder((prev) => {
        const existingIndex = prev.items.findIndex(
          (item) => item.id === product.id
        );
        let updatedItems;

        if (existingIndex > -1) {
          const currentQty = prev.items[existingIndex].quantity;
          if (currentQty >= product.stock) {
            playBeepWarning();
            alert(
              `Kho chỉ còn ${product.stock} ${product.unit}. Không thể thêm vượt quá tồn kho!`
            );
            return prev;
          }
          updatedItems = [...prev.items];
          updatedItems[existingIndex] = {
            ...updatedItems[existingIndex],
            quantity: currentQty + 1
          };
        } else {
          updatedItems = [
            ...prev.items,
            {
              id: product.id,
              barcode: product.barcode,
              name: product.name,
              imageUrl: product.imageUrl,
              unit: product.unit,
              price: product.salePrice,
              quantity: 1,
              stock: product.stock
            }
          ];
        }

        const newSubtotal = updatedItems.reduce(
          (sum, item) => sum + item.price * item.quantity,
          0
        );
        const newFinal = Math.max(0, newSubtotal - (prev.discount || 0));

        playBeepSuccess();
        return {
          ...prev,
          items: updatedItems,
          receivedAmount:
            prev.paymentMethod === 'CASH' && prev.receivedAmount < newFinal
              ? newFinal
              : prev.receivedAmount
        };
      });
    },
    [updateCurrentOrder, needsShift, promptOpenShift]
  );

  // Barcode scanned from hardware scanner
  const handleBarcodeScanned = useCallback(
    (scannedCode) => {
      const found = products.find(
        (p) => p.barcode.toLowerCase() === scannedCode.toLowerCase().trim()
      );
      if (found) {
        handleAddProduct(found);
      } else {
        playBeepWarning();
        alert(`Không tìm thấy sản phẩm có mã vạch: ${scannedCode}`);
      }
    },
    [products, handleAddProduct]
  );

  // Hook Barcode scanner
  useBarcodeScanner(handleBarcodeScanned, {
    enabled: !isSearchOpen && !isCustomerOpen && !isVietQROpen && !isHelpOpen && !isPairingOpen && !isShiftOpen && !isCashOpen && !isRecentInvoicesOpen && !isApprovalOpen && !remoteBatch
  });

  // Listen to simulated scan events (e.g. from BarcodePrintModal or mock tester)
  useEffect(() => {
    const handleCustomScan = (e) => {
      if (e.detail?.barcode) {
        handleBarcodeScanned(e.detail.barcode);
      }
    };
    window.addEventListener('pos:barcode_scanned', handleCustomScan);
    return () => window.removeEventListener('pos:barcode_scanned', handleCustomScan);
  }, [handleBarcodeScanned]);

  useEffect(() => {
    if (!isOnline || !currentUser?.userId || !isCashier) return;
    let cancelled = false;
    approvalService
      .getPolicy()
      .then((p) => {
        if (!cancelled) setCashierMaxDiscountPercent(Number(p?.cashierMaxDiscountPercent));
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [isOnline, currentUser?.userId, isCashier]);

  // Tải ca đang trực. Không tự bật hộp mở ca: thu ngân vẫn đi các màn hình khác hay đăng xuất được
  const userId = currentUser?.userId; // chế độ demo ngoại tuyến không có userId nên không gọi API ca
  const loadShift = useCallback(async () => {
    if (!isOnline || !userId) return null;
    try {
      const info = await shiftService.getCurrent();
      setShiftInfo(info);
      return info;
    } catch (err) {
      console.warn('Không tải được ca làm việc:', err.message);
      return null;
    }
  }, [isOnline, userId]);

  useEffect(() => {
    if (!isOnline || !userId) return;
    let cancelled = false;
    shiftService
      .getCurrent()
      .then((info) => {
        if (cancelled) return;
        setShiftInfo(info);
      })
      .catch((err) => console.warn('Không tải được ca làm việc:', err.message));
    return () => {
      cancelled = true;
    };
  }, [isOnline, userId]);

  const openShiftModal = async () => {
    // Lấy số liệu mới nhất của ca trước khi chốt tiền
    await loadShift();
    setShiftPrompt(null);
    setIsShiftOpen(true);
  };

  // Đăng ký máy này là một quầy (một lần, id lưu trên máy) và nghe mã quét của điện thoại đã ghép
  useEffect(() => {
    let cancelled = false;
    scannerPairingService
      .ensureTerminal(`Quầy ${branchLabel}`, branchId)
      .then((t) => {
        if (cancelled) return;
        setTerminal(t);
        websocketService.setTerminal(t.id);
        return scannerPairingService.listPairings(t.id).then((list) => !cancelled && setPairedCount(list.length));
      })
      .catch((err) => console.warn('Không đăng ký được quầy POS:', err.message));
    return () => {
      cancelled = true;
      websocketService.setTerminal(null);
    };
  }, [branchId, branchLabel, isOnline]);

  useEffect(() => {
    if (!terminal) return;
    return websocketService.on('scanner-pairing', () => {
      scannerPairingService.listPairings(terminal.id).then((list) => setPairedCount(list.length)).catch(() => {});
    });
  }, [terminal]);

  // Mã quét từ điện thoại đã ghép cặp với quầy này (server chỉ gửi tới đúng quầy)
  useEffect(() => {
    const unsub = websocketService.on('barcode-scanned-remote', (payload) => {
      if (payload?.action !== 'ADD_TO_CART') return;
      if (payload.type === 'BATCH') {
        if (Array.isArray(payload.scans) && payload.scans.length > 0) {
          setRemoteBatch(payload.scans);
        }
        return;
      }
      if (payload.barcode) {
        handleBarcodeScanned(payload.barcode);
      }
    });
    return () => unsub();
  }, [handleBarcodeScanned]);

  const handleConfirmRemoteBatch = () => {
    (remoteBatch || []).forEach((scan) => {
      const found = products.find(
        (p) => p.barcode.toLowerCase() === String(scan.barcode).toLowerCase().trim()
      );
      if (found) handleAddProduct(found);
    });
    setRemoteBatch(null);
  };

  // Handle Search Input submit (typing + enter)
  const handleSubmitBarcodeInput = () => {
    if (!barcodeInput.trim()) return;
    const term = barcodeInput.trim().toLowerCase();
    const found = products.find(
      (p) => p.barcode.toLowerCase() === term || p.name.toLowerCase().includes(term)
    );
    if (found) {
      handleAddProduct(found);
      setBarcodeInput('');
    } else {
      setIsSearchOpen(true);
    }
  };

  // Quantity changes
  const handleUpdateQuantity = (id, delta) => {
    updateCurrentOrder((prev) => {
      const updated = prev.items
        .map((item) => {
          if (item.id === id) {
            const nextQty = item.quantity + delta;
            if (nextQty > item.stock) {
              playBeepWarning();
              alert(`Số lượng vượt quá tồn kho hiện tại (${item.stock})!`);
              return item;
            }
            return nextQty > 0 ? { ...item, quantity: nextQty } : null;
          }
          return item;
        })
        .filter(Boolean);

      const newSubtotal = updated.reduce(
        (sum, item) => sum + item.price * item.quantity,
        0
      );
      const newFinal = Math.max(0, newSubtotal - (prev.discount || 0));

      return {
        ...prev,
        items: updated,
        receivedAmount:
          prev.paymentMethod === 'CASH' ? newFinal : prev.receivedAmount
      };
    });
  };

  // Remove Item
  const handleRemoveItem = (id) => {
    updateCurrentOrder((prev) => ({
      ...prev,
      items: prev.items.filter((item) => item.id !== id)
    }));
  };

  // Cancel Order (F9)
  const handleCancelOrder = () => {
    if (isProcessing || currentOrder.items.length === 0) return;
    if (window.confirm('Bạn có chắc chắn muốn hủy đơn hàng hiện tại?')) {
      updateCurrentOrder({
        items: [],
        discount: 0,
        discountReason: '',
        discountApproval: null,
        receivedAmount: 0,
        note: '',
        requestId: newRequestId()
      });
    }
  };

  // Process Payment (F4)
  // order: truyền đơn đã có mã duyệt ngay sau khi quản lý nhập PIN (state chưa kịp cập nhật)
  const handleProcessPayment = (order = currentOrder) => {
    if (isProcessing) return;
    if (needsShift) {
      playBeepWarning();
      promptOpenShift();
      return;
    }
    if (finalAmount <= 0) {
      alert('Đơn hàng chưa có sản phẩm để thanh toán!');
      return;
    }

    // Thu ngân giảm vượt hạn mức: cần lý do và PIN quản lý trước khi thu tiền
    if (isOverDiscountLimit(order)) {
      if (!order.discountReason?.trim()) {
        playBeepWarning();
        alert('Giảm giá vượt hạn mức, vui lòng nhập lý do giảm giá.');
        return;
      }
      if (!isDiscountApproved(order)) {
        setIsApprovalOpen(true);
        return;
      }
    }

    if (currentOrder.paymentMethod === 'VIETQR') {
      setIsVietQROpen(true);
      return;
    }

    if (currentOrder.paymentMethod === 'CASH' && changeAmount < 0) {
      playBeepWarning();
      alert('Số tiền khách đưa chưa đủ để hoàn tất đơn hàng!');
      return;
    }

    executeFinishPayment(order);
  };

  // Finalize payment & receipt
  const executeFinishPayment = async (orderOverride) => {
    if (processingRef.current) return;
    processingRef.current = true;
    const order = orderOverride || currentOrder;
    const orderTabId = activeTabId;
    const cashierName = currentUser?.fullName || currentUser?.username || 'Thu ngân';
    let receipt;

    setIsProcessing(true);
    try {
      if (isOnline) {
        // Chỉ coi là đã bán khi máy chủ đã ghi nhận hóa đơn
        let invoice;
        try {
          invoice = await saleService.createInvoice({
            // Máy chủ bán tại chi nhánh của phiên đăng nhập; gửi kèm để phát hiện đơn của phiên cũ
            branchId: Number(branchId) || undefined,
            customerId: order.customer?.id || null,
            discount: order.discount || 0,
            discountReason: order.discount > 0 ? order.discountReason || '' : '',
            discountApprovalToken: order.discountApproval?.token || undefined,
            paymentMethod: order.paymentMethod,
            receivedAmount: order.receivedAmount || finalAmount,
            note: order.note || '',
            clientRequestId: order.requestId,
            items: order.items.map(it => ({
              productId: it.id,
              quantity: it.quantity,
              unitPrice: it.price
            }))
          });
        } catch (err) {
          playBeepWarning();
          if (err.response?.data?.code === 'DISCOUNT_APPROVAL_REQUIRED') {
            // Mã duyệt hết hạn hoặc chưa có: bỏ mã cũ, mời quản lý nhập PIN lại
            setOrders((prev) => ({ ...prev, [orderTabId]: { ...prev[orderTabId], discountApproval: null } }));
            setIsApprovalOpen(true);
          } else if (err.response) {
            // Server từ chối (sai giá, giảm giá không hợp lệ, hết hàng, không có quyền...)
            alert(`Không thể thanh toán: ${err.response.data?.message || 'Máy chủ từ chối hóa đơn'}`);
          } else {
            // Mất kết nối / hết thời gian chờ: chưa biết server đã lưu hay chưa.
            // Giữ nguyên giỏ và mã đơn; bấm thanh toán lại sẽ không tạo hóa đơn trùng.
            alert(
              'Không nhận được phản hồi từ máy chủ, hóa đơn CHƯA được xác nhận.\n\n' +
                'Đơn hàng vẫn được giữ nguyên. Kiểm tra kết nối mạng rồi bấm Thanh toán lại ' +
                '(hệ thống sẽ không ghi trùng hóa đơn). Chưa giao hàng hoặc in hóa đơn cho khách.'
            );
          }
          return;
        }
        receipt = receiptFromInvoice(invoice, cashierName);
      } else {
        // Chế độ demo ngoại tuyến (chưa đăng nhập): không có máy chủ, hóa đơn chỉ để trình diễn
        receipt = {
          code: `DEMO-${Date.now().toString().slice(-6)}`,
          branchName: `${branchLabel} (demo, không lưu)`,
          cashierName,
          customerName: order.customer ? order.customer.name : 'Khách lẻ',
          items: order.items.map(it => ({ name: it.name, quantity: it.quantity, unitPrice: it.price })),
          subtotal,
          discount: order.discount || 0,
          total: finalAmount,
          received: order.paymentMethod === 'CASH' ? order.receivedAmount || finalAmount : finalAmount,
          change: Math.max(0, changeAmount),
          paymentMethod: order.paymentMethod
        };
      }

      playBeepSuccess();

      // Trừ tồn kho trên màn hình theo số lượng đã bán
      setProducts(prev => {
        const soldMap = new Map();
        order.items.forEach(it => soldMap.set(it.id, it.quantity));
        return prev.map(p => {
          if (soldMap.has(p.id)) {
            return { ...p, stock: Math.max(0, p.stock - soldMap.get(p.id)) };
          }
          return p;
        });
      });

      // Làm trống đơn vừa thanh toán (kèm mã chống trùng mới)
      setOrders(prev => ({
        ...prev,
        [orderTabId]: {
          ...prev[orderTabId],
          items: [],
          customer: null,
          discount: 0,
          discountReason: '',
          discountApproval: null,
          receivedAmount: 0,
          note: '',
          requestId: newRequestId()
        }
      }));
      setIsVietQROpen(false);

      // In hóa đơn K80 từ dữ liệu server đã ghi nhận
      const printResult = await printInvoiceReceipt(receipt);

      alert(
        `Thanh toán thành công [${receipt.code}]!\n\n` +
          `• Khách hàng: ${receipt.customerName}\n` +
          `• Tổng thanh toán: ${formatCurrency(receipt.total)}\n` +
          `• Phương thức: ${receipt.paymentMethod}\n` +
          `• Tiền thừa trả khách: ${formatCurrency(receipt.change)}\n\n` +
          (printResult?.success
            ? 'Hóa đơn K80 đã được gửi đến máy in.'
            : `Hóa đơn đã lưu nhưng KHÔNG in được: ${printResult?.error || 'lỗi máy in'}.`)
      );
    } finally {
      processingRef.current = false;
      setIsProcessing(false);
    }
  };

  // Tab Handlers
  const handleAddTab = () => {
    const newIndex = tabs.length + 1;
    const newId = `order-${Date.now()}`;
    const newTab = {
      id: newId,
      label: `Đơn #${String(newIndex).padStart(2, '0')}`,
      closable: true
    };
    setTabs([...tabs, newTab]);
    setOrders((prev) => ({
      ...prev,
      [newId]: emptyOrder()
    }));
    setActiveTabId(newId);
  };

  const handleCloseTab = (id) => {
    if (tabs.length <= 1) return;
    const nextTabs = tabs.filter((t) => t.id !== id);
    setTabs(nextTabs);
    setOrders((prev) => {
      const copy = { ...prev };
      delete copy[id];
      return copy;
    });
    if (activeTabId === id) {
      setActiveTabId(nextTabs[nextTabs.length - 1].id);
    }
  };

  // Register Global Hotkeys
  useHotkeys({
    F1: () => setIsHelpOpen(true),
    F2: () => setIsSearchOpen(true),
    F3: () => setIsCustomerOpen(true),
    F4: () => handleProcessPayment(),
    F7: () => isOnline && setIsRecentInvoicesOpen(true),
    F8: () => handleAddTab(),
    F9: () => handleCancelOrder(),
    Escape: () => {
      setIsSearchOpen(false);
      setIsCustomerOpen(false);
      setIsVietQROpen(false);
      setIsHelpOpen(false);
      setIsPairingOpen(false);
      setIsRecentInvoicesOpen(false);
      setRemoteBatch(null);
    }
  });

  return (
    <div className="pos-container">
      {/* Header */}
      <PosHeader
        branchName={branchLabel}
        cashierName={currentUser?.fullName || currentUser?.username || 'Thu ngân'}
        role={currentUser?.role || 'CASHIER'}
        shiftName={
          shiftInfo?.session
            ? `${shiftInfo.session.workShiftName} · ${shiftInfo.session.employeeName}`
            : 'Chưa mở ca'
        }
        shiftOpen={!!shiftInfo?.session}
        onOpenShift={isOnline && userId ? openShiftModal : undefined}
        onOpenCashMovement={isOnline && userId ? () => loadShift().then(() => setIsCashOpen(true)) : undefined}
        isOnline={isOnline}
        isWsConnected={isWsConnected}
        onOpenRecentInvoices={isOnline ? () => setIsRecentInvoicesOpen(true) : undefined}
        terminalName={terminal?.name}
        pairedCount={pairedCount}
        onOpenPairing={() => setIsPairingOpen(true)}
        onOpenHelp={() => setIsHelpOpen(true)}
      />

      {needsShift && (
        <div className="pos-shift-notice" role="status">
          <span>Bạn chưa mở ca. Vẫn có thể tra cứu và chuẩn bị đơn, nhưng cần mở ca để thanh toán.</span>
          <Button variant="primary" size="sm" onClick={openShiftModal}>
            Mở ca
          </Button>
        </div>
      )}

      {/* Main 2-Column POS Workspace */}
      <div className="pos-body">
        {/* Left Column: Cart & Tabs */}
        <PosCart
          tabs={tabs}
          activeTabId={activeTabId}
          onSelectTab={setActiveTabId}
          onCloseTab={handleCloseTab}
          onAddTab={handleAddTab}
          cartItems={currentOrder.items}
          onUpdateQuantity={handleUpdateQuantity}
          onRemoveItem={handleRemoveItem}
          onOpenSearch={() => setIsSearchOpen(true)}
          barcodeInput={barcodeInput}
          onChangeBarcodeInput={setBarcodeInput}
          onSubmitBarcode={handleSubmitBarcodeInput}
          orderNote={currentOrder.note}
          onChangeOrderNote={(val) => updateCurrentOrder({ note: val })}
        />

        {/* Right Column: Fixed Payment Panel */}
        <PosPayment
          customer={currentOrder.customer}
          onOpenCustomerModal={() => setIsCustomerOpen(true)}
          subtotal={subtotal}
          discount={currentOrder.discount}
          onChangeDiscount={(val) => updateCurrentOrder({ discount: val })}
          discountReason={currentOrder.discountReason || ''}
          onChangeDiscountReason={(val) => updateCurrentOrder({ discountReason: val })}
          discountLimit={discountLimit}
          discountApproverName={discountOverLimit && discountApproved ? currentOrder.discountApproval?.approverName : null}
          finalAmount={finalAmount}
          paymentMethod={currentOrder.paymentMethod}
          onChangePaymentMethod={(method) =>
            updateCurrentOrder({ paymentMethod: method })
          }
          receivedAmount={currentOrder.receivedAmount}
          onChangeReceivedAmount={(val) =>
            updateCurrentOrder({ receivedAmount: val })
          }
          changeAmount={changeAmount}
          onCancelOrder={handleCancelOrder}
          onProcessPayment={() => handleProcessPayment()}
          isProcessing={isProcessing}
        />
      </div>

      {/* Bottom Hotkey Bar */}
      <footer className="pos-footer">
        <div className="pos-hotkey-list">
          <div className="pos-hotkey-item"><kbd>F1</kbd> Trợ giúp</div>
          <div className="pos-hotkey-item"><kbd>F2</kbd> Tra cứu hàng</div>
          <div className="pos-hotkey-item"><kbd>F3</kbd> Chọn khách</div>
          <div className="pos-hotkey-item"><kbd>F4</kbd> Thanh toán</div>
          <div className="pos-hotkey-item"><kbd>F8</kbd> Thêm đơn mới</div>
          <div className="pos-hotkey-item"><kbd>F9</kbd> Hủy đơn</div>
          <div className="pos-hotkey-item"><kbd>Esc</kbd> Đóng</div>
        </div>
        <div>
          <span>Máy in: <strong>K80 Thermal</strong></span>
        </div>
      </footer>

      {/* Modals */}
      <ProductSearchModal
        isOpen={isSearchOpen}
        onClose={() => setIsSearchOpen(false)}
        products={products}
        loading={loadingProducts}
        onSelectProduct={handleAddProduct}
      />

      <CustomerModal
        isOpen={isCustomerOpen}
        onClose={() => setIsCustomerOpen(false)}
        customers={customers}
        onSelectCustomer={(cust) => updateCurrentOrder({ customer: cust })}
        onAddNewCustomer={(newCust) => setCustomers([...customers, newCust])}
      />

      {isShiftOpen && (
        <ShiftModal
          isOpen
          onClose={() => setIsShiftOpen(false)}
          current={shiftInfo}
          reason={shiftPrompt}
          onChanged={loadShift}
          role={currentUser?.role}
        />
      )}

      {isCashOpen && shiftInfo?.session && (
        <CashMovementModal
          isOpen
          onClose={() => setIsCashOpen(false)}
          session={shiftInfo.session}
          drawerLimit={shiftInfo.drawerLimit}
          onChanged={loadShift}
        />
      )}

      {isPairingOpen && (
        <ScannerPairingModal
          isOpen
          onClose={() => setIsPairingOpen(false)}
          terminal={terminal}
          onTerminalChange={setTerminal}
        />
      )}

      <VietQRModal
        isOpen={isVietQROpen}
        onClose={() => setIsVietQROpen(false)}
        amount={finalAmount}
        orderId={activeTabId.replace('order-', 'DH')}
        onConfirmSuccess={() => executeFinishPayment()}
      />

      {/* Help Modal */}
      <Modal
        isOpen={!!remoteBatch}
        onClose={() => setRemoteBatch(null)}
        title="Nhận hàng quét từ điện thoại"
        maxWidth="500px"
        footer={
          <>
            <Button variant="secondary" onClick={() => setRemoteBatch(null)}>
              Bỏ qua
            </Button>
            <Button variant="primary" onClick={handleConfirmRemoteBatch}>
              Thêm vào giỏ
            </Button>
          </>
        }
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', fontSize: '13px' }}>
          <div>
            Điện thoại vừa đồng bộ {remoteBatch?.length || 0} mã đã quét lúc mất mạng. Thêm vào đơn hàng
            hiện tại?
          </div>
          <div style={{ maxHeight: '260px', overflowY: 'auto' }}>
            {(remoteBatch || []).map((scan) => (
              <div
                key={scan.clientScanId || scan.barcode}
                style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0', borderBottom: '1px solid var(--border-subtle)' }}
              >
                <span>{scan.product?.name || `Mã ${scan.barcode} (không có trong danh mục, sẽ bỏ qua)`}</span>
                <span>{scan.product ? formatCurrency(scan.product.salePrice) : ''}</span>
              </div>
            ))}
          </div>
        </div>
      </Modal>

      <ManagerApprovalModal
        isOpen={isApprovalOpen}
        onClose={() => setIsApprovalOpen(false)}
        amount={currentOrder.discount || 0}
        maxWithoutApproval={discountLimit}
        reason={currentOrder.discountReason}
        onApproved={(grant) => {
          updateCurrentOrder({ discountApproval: grant });
          setIsApprovalOpen(false);
          handleProcessPayment({ ...currentOrder, discountApproval: grant });
        }}
      />

      <RecentInvoicesModal
        isOpen={isRecentInvoicesOpen}
        onClose={() => setIsRecentInvoicesOpen(false)}
        shiftSession={shiftInfo?.session}
        branchId={branchId}
        cashierName={currentUser?.fullName || currentUser?.username || 'Thu ngân'}
        canViewAll={currentUser?.role === 'ADMIN' || currentUser?.role === 'MANAGER'}
      />

      <Modal
        isOpen={isHelpOpen}
        onClose={() => setIsHelpOpen(false)}
        title="Danh mục phím tắt bán hàng (Hotkeys)"
        maxWidth="500px"
        footer={
          <Button variant="secondary" onClick={() => setIsHelpOpen(false)}>
            Đóng (Esc)
          </Button>
        }
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', fontSize: '13px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0', borderBottom: '1px solid var(--border-subtle)' }}>
            <span>Mở tra cứu sản phẩm</span>
            <kbd>F2</kbd>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0', borderBottom: '1px solid var(--border-subtle)' }}>
            <span>Chọn / Tìm kiếm khách hàng</span>
            <kbd>F3</kbd>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0', borderBottom: '1px solid var(--border-subtle)' }}>
            <span>Thực hiện thanh toán & In hóa đơn</span>
            <kbd>F4</kbd>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0', borderBottom: '1px solid var(--border-subtle)' }}>
            <span>Xem lịch sử hóa đơn gần đây / In lại bill</span>
            <kbd>F7</kbd>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0', borderBottom: '1px solid var(--border-subtle)' }}>
            <span>Tạo thêm tab đơn hàng mới</span>
            <kbd>F8</kbd>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0', borderBottom: '1px solid var(--border-subtle)' }}>
            <span>Hủy đơn hàng hiện tại</span>
            <kbd>F9</kbd>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0' }}>
            <span>Đóng cửa sổ / Thoát thao tác</span>
            <kbd>Esc</kbd>
          </div>
        </div>
      </Modal>
    </div>
  );
}
