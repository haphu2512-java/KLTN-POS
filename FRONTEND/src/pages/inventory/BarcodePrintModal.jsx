import React, { useState, useMemo } from 'react';
import { Modal, Button, BarcodeView } from '../../components/common';
import { formatCurrency } from '../../utils/formatters';
import { exportToCsv } from '../../utils/exportCsv';
import { playBeep } from '../../utils/audio';
import './BarcodePrintModal.css';

export default function BarcodePrintModal({
  isOpen,
  onClose,
  products = [],
  initialSelectedProduct = null,
  storeName = 'BÁCH HÓA POS',
  onMockScan = null
}) {
  const [filterCategory, setFilterCategory] = useState('ALL');
  const [searchTerm, setSearchTerm] = useState('');
  const [paperFormat, setPaperFormat] = useState('format-thermal'); // 'format-thermal' | 'format-sheet' | 'format-large'
  const [copiesPerItem, setCopiesPerItem] = useState(1);
  const [showPrice, setShowPrice] = useState(true);
  const [showStoreName, setShowStoreName] = useState(true);
  const [selectedOnly, setSelectedOnly] = useState(Boolean(initialSelectedProduct));

  // Extract unique categories
  const categories = useMemo(() => {
    const set = new Set();
    products.forEach((p) => {
      if (p.category) set.add(p.category);
    });
    return Array.from(set).sort();
  }, [products]);

  // Filter products
  const displayProducts = useMemo(() => {
    if (selectedOnly && initialSelectedProduct) {
      return [initialSelectedProduct];
    }

    return products.filter((p) => {
      const matchCat = filterCategory === 'ALL' || p.category === filterCategory;
      const term = searchTerm.toLowerCase().trim();
      const matchSearch =
        !term ||
        p.name.toLowerCase().includes(term) ||
        (p.barcode && p.barcode.toLowerCase().includes(term));
      return matchCat && matchSearch;
    });
  }, [products, selectedOnly, initialSelectedProduct, filterCategory, searchTerm]);

  // Generate list of stickers (repeating copiesPerItem times)
  // Limit to max 300 stickers in DOM preview to avoid lag, but allow printing batch
  const stickers = useMemo(() => {
    const list = [];
    const count = Math.max(1, Math.min(50, Number(copiesPerItem) || 1));
    for (const p of displayProducts) {
      for (let i = 0; i < count; i++) {
        list.push({ ...p, stickerKey: `${p.id}-${i}` });
        if (list.length >= 500) break; // Safeguard browser rendering
      }
      if (list.length >= 500) break;
    }
    return list;
  }, [displayProducts, copiesPerItem]);

  const handlePrint = () => {
    window.print();
  };

  const handleExportCsv = () => {
    const headers = ['Mã vạch (Barcode)', 'Tên sản phẩm', 'Danh mục', 'Đơn vị tính', 'Giá bán (VND)'];
    const rows = displayProducts.map((p) => [
      p.barcode,
      p.name,
      p.category || '',
      p.unit || 'Cái',
      p.salePrice || 0
    ]);
    exportToCsv(`DanhSach_MaVach_${new Date().toISOString().slice(0, 10)}`, headers, rows);
  };

  const handleSimulateScan = (product) => {
    playBeep();
    if (onMockScan) {
      onMockScan(product.barcode);
    } else {
      // Dispatch mock event so any active barcode listener catches it
      window.dispatchEvent(
        new CustomEvent('pos:barcode_scanned', { detail: { barcode: product.barcode, product } })
      );
    }
    alert(`⚡ Đã mô phỏng quét mã vạch: ${product.barcode} (${product.name})`);
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="🏷️ IN TEM MÃ VẠCH SẢN PHẨM (BARCODE PRINTING & SHEET)"
      width="960px"
    >
      <div className="barcode-modal-content">
        {/* Controls Toolbar */}
        <div className="barcode-toolbar">
          <div className="barcode-toolbar-group">
            <input
              type="text"
              placeholder="🔍 Tìm theo tên hoặc mã vạch..."
              className="barcode-input"
              style={{ width: '220px' }}
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
            />

            <select
              className="barcode-select"
              value={filterCategory}
              onChange={(e) => setFilterCategory(e.target.value)}
              disabled={selectedOnly}
            >
              <option value="ALL">Tất cả danh mục ({products.length} SP)</option>
              {categories.map((c) => (
                <option key={c} value={c}>
                  {c}
                </option>
              ))}
            </select>

            <select
              className="barcode-select"
              value={paperFormat}
              onChange={(e) => setPaperFormat(e.target.value)}
            >
              <option value="format-thermal">Tem nhiệt (35x22mm - Cuộn 2 tem)</option>
              <option value="format-sheet">Khổ A4 (Lưới 65 tem/tờ)</option>
              <option value="format-large">Tem lớn (50x30mm - Dán thùng)</option>
            </select>

            <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
              <span style={{ fontSize: '12px', color: '#94A3B8' }}>Số tem/mã:</span>
              <input
                type="number"
                min="1"
                max="50"
                value={copiesPerItem}
                onChange={(e) => setCopiesPerItem(Math.max(1, parseInt(e.target.value) || 1))}
                className="barcode-input"
                style={{ width: '60px', textAlign: 'center' }}
              />
            </div>
          </div>

          <div className="barcode-toolbar-group">
            <label className="barcode-checkbox-label">
              <input
                type="checkbox"
                checked={showPrice}
                onChange={(e) => setShowPrice(e.target.checked)}
              />
              Hiện giá
            </label>

            <label className="barcode-checkbox-label">
              <input
                type="checkbox"
                checked={showStoreName}
                onChange={(e) => setShowStoreName(e.target.checked)}
              />
              Hiện tên shop
            </label>

            {initialSelectedProduct && (
              <label className="barcode-checkbox-label">
                <input
                  type="checkbox"
                  checked={selectedOnly}
                  onChange={(e) => setSelectedOnly(e.target.checked)}
                />
                Chỉ SP đang chọn
              </label>
            )}
          </div>
        </div>

        {/* Stats & Notice */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: '13px', color: '#64748B' }}>
          <span>
            Đang hiển thị <strong>{displayProducts.length}</strong> sản phẩm ({stickers.length} tem in)
            {displayProducts.length > 500 && ' (giới hạn 500 tem đầu tiên trong bản xem trước)'}
          </span>
          <span style={{ fontSize: '11px', color: '#0284C7' }}>
            💡 Hỗ trợ máy in tem nhiệt Xprinter, Gprinter, Bixolon hoặc in trực tiếp ra giấy A4
          </span>
        </div>

        {/* Preview / Printable Area */}
        <div className="barcode-preview-container barcode-print-printable-area">
          <div className={`barcode-grid ${paperFormat}`}>
            {stickers.map((item) => (
              <div key={item.stickerKey} className="barcode-sticker">
                <button
                  type="button"
                  className="sticker-quick-scan-btn"
                  title="Mô phỏng máy quét HID quét mã này"
                  onClick={() => handleSimulateScan(item)}
                >
                  ⚡ Quét thử
                </button>

                {showStoreName && <div className="sticker-store">{storeName}</div>}

                <div className="sticker-name" title={item.name}>
                  {item.name}
                </div>

                <div className="sticker-barcode-box">
                  <BarcodeView
                    value={item.barcode}
                    width={paperFormat === 'format-sheet' ? 1.3 : 1.6}
                    height={paperFormat === 'format-sheet' ? 32 : 38}
                    fontSize={11}
                    margin={2}
                  />
                </div>

                {showPrice && (
                  <div className="sticker-price">{formatCurrency(item.salePrice)}</div>
                )}
              </div>
            ))}
          </div>
        </div>

        {/* Modal Actions */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '8px' }}>
          <Button variant="secondary" onClick={handleExportCsv} title="Xuất file CSV danh sách mã vạch">
            📥 Xuất CSV Barcode
          </Button>

          <div style={{ display: 'flex', gap: '10px' }}>
            <Button variant="ghost" onClick={onClose}>
              Đóng
            </Button>
            <Button variant="primary" onClick={handlePrint} title="Kích hoạt lệnh in máy in tem hoặc PDF">
              🖨️ In tem mã vạch (Print)
            </Button>
          </div>
        </div>
      </div>
    </Modal>
  );
}
