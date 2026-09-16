import React, { useState, useMemo, useCallback } from 'react';
import PosHeader from './PosHeader';
import PosCart from './PosCart';
import PosPayment from './PosPayment';
import ProductSearchModal from './ProductSearchModal';
import CustomerModal from './CustomerModal';
import VietQRModal from './VietQRModal';
import { Modal, Button } from '../../components/common';
import { useBarcodeScanner } from '../../hooks/useBarcodeScanner';
import { useHotkeys } from '../../hooks/useHotkeys';
import { playBeepSuccess, playBeepWarning } from '../../utils/audio';
import { INITIAL_PRODUCTS, INITIAL_CUSTOMERS } from '../../data/mockProducts';
import { formatCurrency } from '../../utils/formatters';
import './PosPage.css';

export default function PosPage({ onNavigateToInventory, onNavigateToForecast }) {
  // Products & Customers
  const [products] = useState(INITIAL_PRODUCTS);
  const [customers, setCustomers] = useState(INITIAL_CUSTOMERS);

  // Multi-order Tabs State
  const [tabs, setTabs] = useState([
    { id: 'order-1', label: 'Đơn #01', closable: false }
  ]);
  const [activeTabId, setActiveTabId] = useState('order-1');

  // Orders State Dict: { [orderId]: { items, customer, discount, paymentMethod, receivedAmount, note } }
  const [orders, setOrders] = useState({
    'order-1': {
      items: [
        {
          id: 1,
          barcode: '8934567890123',
          name: 'Sữa tươi tiệt trùng Vinamilk 100% Không Đường 1L',
          unit: 'Hộp',
          price: 36000,
          quantity: 2,
          stock: 45
        },
        {
          id: 3,
          barcode: '8936009876543',
          name: 'Nước ngọt Coca-Cola Sleek 320ml',
          unit: 'Lon',
          price: 11000,
          quantity: 3,
          stock: 120
        }
      ],
      customer: null,
      discount: 0,
      paymentMethod: 'CASH',
      receivedAmount: 105000,
      note: ''
    }
  });

  // Modals state
  const [isSearchOpen, setIsSearchOpen] = useState(false);
  const [isCustomerOpen, setIsCustomerOpen] = useState(false);
  const [isVietQROpen, setIsVietQROpen] = useState(false);
  const [isHelpOpen, setIsHelpOpen] = useState(false);
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

  // Add Product to Cart by Barcode or selection
  const handleAddProduct = useCallback(
    (product) => {
      if (!product) return;
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
    [updateCurrentOrder]
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
    enabled: !isSearchOpen && !isCustomerOpen && !isVietQROpen && !isHelpOpen
  });

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
    if (currentOrder.items.length === 0) return;
    if (window.confirm('Bạn có chắc chắn muốn hủy đơn hàng hiện tại?')) {
      updateCurrentOrder({
        items: [],
        discount: 0,
        receivedAmount: 0,
        note: ''
      });
    }
  };

  // Process Payment (F4)
  const handleProcessPayment = () => {
    if (finalAmount <= 0) {
      alert('Đơn hàng chưa có sản phẩm để thanh toán!');
      return;
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

    executeFinishPayment();
  };

  // Finalize payment & receipt
  const executeFinishPayment = () => {
    playBeepSuccess();
    alert(
      `🎉 THANH TOÁN THÀNH CÔNG!\n\n` +
        `• Khách hàng: ${currentOrder.customer ? currentOrder.customer.name : 'Khách lẻ'}\n` +
        `• Tổng thanh toán: ${formatCurrency(finalAmount)}\n` +
        `• Phương thức: ${currentOrder.paymentMethod}\n` +
        `• Tiền thừa trả khách: ${formatCurrency(Math.max(0, changeAmount))}\n\n` +
        `Đang gửi lệnh in hóa đơn ra máy in K80...`
    );

    // Reset current order
    updateCurrentOrder({
      items: [],
      discount: 0,
      receivedAmount: 0,
      note: ''
    });
    setIsVietQROpen(false);
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
      [newId]: {
        items: [],
        customer: null,
        discount: 0,
        paymentMethod: 'CASH',
        receivedAmount: 0,
        note: ''
      }
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
    F8: () => handleAddTab(),
    F9: () => handleCancelOrder(),
    Escape: () => {
      setIsSearchOpen(false);
      setIsCustomerOpen(false);
      setIsVietQROpen(false);
      setIsHelpOpen(false);
    }
  });

  return (
    <div className="pos-container">
      {/* Header */}
      <PosHeader
        branchName="Chi nhánh Quận 1"
        cashierName="Nguyễn Văn A"
        shiftName="Ca Sáng"
        isOnline={true}
        onOpenHelp={() => setIsHelpOpen(true)}
        onNavigateToInventory={onNavigateToInventory}
        onNavigateToForecast={onNavigateToForecast}
      />

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
          onProcessPayment={handleProcessPayment}
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
          <span>Máy in: <strong>K80 Thermal (Đã kết nối)</strong></span>
        </div>
      </footer>

      {/* Modals */}
      <ProductSearchModal
        isOpen={isSearchOpen}
        onClose={() => setIsSearchOpen(false)}
        products={products}
        onSelectProduct={handleAddProduct}
      />

      <CustomerModal
        isOpen={isCustomerOpen}
        onClose={() => setIsCustomerOpen(false)}
        customers={customers}
        onSelectCustomer={(cust) => updateCurrentOrder({ customer: cust })}
        onAddNewCustomer={(newCust) => setCustomers([...customers, newCust])}
      />

      <VietQRModal
        isOpen={isVietQROpen}
        onClose={() => setIsVietQROpen(false)}
        amount={finalAmount}
        orderId={activeTabId.replace('order-', 'DH')}
        onConfirmSuccess={executeFinishPayment}
      />

      {/* Help Modal */}
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
