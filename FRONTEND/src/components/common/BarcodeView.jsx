import { useEffect, useRef } from 'react';
import JsBarcode from 'jsbarcode';

/**
 * BarcodeView Component
 * Renders high-quality crisp vector SVG barcodes using JsBarcode.
 * Supports Code128, EAN-13, UPC, etc.
 */
export default function BarcodeView({
  value,
  format = 'CODE128',
  width = 1.6,
  height = 42,
  displayValue = true,
  fontSize = 12,
  margin = 4,
  background = 'transparent',
  lineColor = '#000000',
  className = '',
  style = {}
}) {
  const svgRef = useRef(null);

  useEffect(() => {
    if (!svgRef.current || !value) return;

    try {
      // Determine best format or fallback cleanly to CODE128
      let actualFormat = format;
      const cleanVal = String(value).trim();

      // If format was requested as EAN13 but value length is not 13 or contains letters, fallback to CODE128
      if (format === 'EAN13' && (!/^\d{13}$/.test(cleanVal))) {
        actualFormat = 'CODE128';
      }

      JsBarcode(svgRef.current, cleanVal, {
        format: actualFormat,
        width,
        height,
        displayValue,
        fontSize,
        font: 'monospace',
        textAlign: 'center',
        textPosition: 'bottom',
        textMargin: 2,
        background,
        lineColor,
        margin
      });
    } catch (err) {
      console.warn(`Lỗi khi tạo mã vạch cho giá trị "${value}" với định dạng ${format}:`, err);
      // Fallback try with CODE128
      try {
        JsBarcode(svgRef.current, String(value).trim(), {
          format: 'CODE128',
          width,
          height,
          displayValue,
          fontSize,
          margin
        });
      } catch (fallbackErr) {
        console.error('Không thể render mã vạch:', fallbackErr);
      }
    }
  }, [value, format, width, height, displayValue, fontSize, margin, background, lineColor]);

  if (!value) {
    return <span className="text-muted" style={{ fontSize: '11px' }}>Chưa có mã vạch</span>;
  }

  return (
    <div
      className={`barcode-view-wrapper ${className}`}
      style={{
        display: 'inline-flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        ...style
      }}
    >
      <svg ref={svgRef} style={{ maxWidth: '100%', height: 'auto', display: 'block' }} />
    </div>
  );
}
