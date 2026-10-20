import { formatCurrency, formatDateTime } from './formatters';

const PAYMENT_LABELS = { CASH: 'Tiền mặt', VIETQR: 'Chuyển khoản', CARD: 'Thẻ' };

/** Escape dữ liệu (tên sản phẩm, khách hàng...) trước khi chèn vào HTML hóa đơn */
export function escapeHtml(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

/**
 * Dữ liệu in lấy từ hóa đơn server trả về (mã, giá, tiền thối là số đã được ghi nhận),
 * không lấy từ giỏ hàng trên màn hình.
 */
export function receiptFromInvoice(invoice, cashierName) {
  return {
    code: `HD${String(invoice.id).padStart(6, '0')}`,
    createdAt: invoice.createdAt,
    branchName: invoice.branch?.name || '',
    branchAddress: invoice.branch?.address || '',
    cashierName,
    customerName: invoice.customer?.name || 'Khách lẻ',
    items: (invoice.details || []).map((d) => ({
      name: d.product?.name || '',
      quantity: d.quantity,
      unitPrice: d.unitPrice
    })),
    subtotal: invoice.subtotal,
    discount: invoice.discount,
    total: invoice.totalAmount,
    received: invoice.receivedAmount,
    change: invoice.changeAmount,
    paymentMethod: invoice.paymentMethod
  };
}

export function generateReceiptHtml(receipt) {
  const itemsHtml = receipt.items.map(item => `
    <tr>
      <td style="padding: 4px 0; font-size: 12px;">${escapeHtml(item.name)}</td>
      <td style="padding: 4px 0; text-align: center; font-size: 12px;">${escapeHtml(item.quantity)}</td>
      <td style="padding: 4px 0; text-align: right; font-size: 12px;">${formatCurrency(item.unitPrice)}</td>
      <td style="padding: 4px 0; text-align: right; font-size: 12px; font-weight: bold;">${formatCurrency(Number(item.unitPrice) * Number(item.quantity))}</td>
    </tr>
  `).join('');

  const discount = Number(receipt.discount) || 0;
  const paymentLabel = PAYMENT_LABELS[receipt.paymentMethod] || receipt.paymentMethod;

  return `
    <!DOCTYPE html>
    <html>
    <head>
      <meta charset="utf-8" />
      <title>Hóa Đơn ${escapeHtml(receipt.code)}</title>
      <style>
        @page { size: 80mm auto; margin: 0; }
        body {
          font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
          width: 76mm;
          margin: 0 auto;
          padding: 8px 4px;
          color: #000;
          font-size: 12px;
          line-height: 1.3;
        }
        .text-center { text-align: center; }
        .text-right { text-align: right; }
        .bold { font-weight: bold; }
        .divider { border-top: 1px dashed #000; margin: 6px 0; }
        .double-divider { border-top: 2px solid #000; margin: 6px 0; }
        table { width: 100%; border-collapse: collapse; margin: 6px 0; }
        th { font-size: 11px; border-bottom: 1px solid #000; padding: 3px 0; text-align: left; }
        .footer-note { font-size: 11px; text-align: center; margin-top: 10px; }
      </style>
    </head>
    <body>
      <div class="text-center">
        <h2 style="margin: 0; font-size: 16px;">HỆ THỐNG POS BÁN LẺ</h2>
        <div style="font-size: 11px;">${escapeHtml(receipt.branchName)}</div>
        ${receipt.branchAddress ? `<div style="font-size: 10px;">${escapeHtml(receipt.branchAddress)}</div>` : ''}
        <div style="font-size: 10px;">Hotline: 1900 1234 • Giờ mở cửa: 07:00 - 22:00</div>
      </div>

      <div class="divider"></div>

      <div>
        <div>Mã HĐ: <span class="bold">${escapeHtml(receipt.code)}</span></div>
        <div>Ngày giờ: ${formatDateTime(receipt.createdAt || new Date())}</div>
        <div>Thu ngân: ${escapeHtml(receipt.cashierName)}</div>
        <div>Khách hàng: ${escapeHtml(receipt.customerName)}</div>
      </div>

      <div class="divider"></div>

      <table>
        <thead>
          <tr>
            <th style="width: 44%;">Mặt hàng</th>
            <th style="width: 14%; text-align: center;">SL</th>
            <th style="width: 20%; text-align: right;">Đơn giá</th>
            <th style="width: 22%; text-align: right;">T.Tiền</th>
          </tr>
        </thead>
        <tbody>
          ${itemsHtml}
        </tbody>
      </table>

      <div class="double-divider"></div>

      <table style="margin: 0;">
        <tr>
          <td>Tổng tiền hàng:</td>
          <td class="text-right">${formatCurrency(receipt.subtotal)}</td>
        </tr>
        ${discount > 0 ? `
        <tr>
          <td>Giảm giá / Chiết khấu:</td>
          <td class="text-right">-${formatCurrency(discount)}</td>
        </tr>
        ` : ''}
        <tr style="font-size: 14px;" class="bold">
          <td>THANH TOÁN:</td>
          <td class="text-right">${formatCurrency(receipt.total)}</td>
        </tr>
        <tr>
          <td>Tiền khách đưa (${escapeHtml(paymentLabel)}):</td>
          <td class="text-right">${formatCurrency(receipt.received)}</td>
        </tr>
        <tr>
          <td>Tiền thừa trả khách:</td>
          <td class="text-right bold">${formatCurrency(receipt.change)}</td>
        </tr>
      </table>

      <div class="divider"></div>

      <div class="footer-note">
        <div class="bold">CẢM ƠN QUÝ KHÁCH & HẸN GẶP LẠI!</div>
        <div style="font-size: 10px; color: #444; margin-top: 2px;">(Hóa đơn có giá trị đổi trả trong vòng 3 ngày)</div>
      </div>
    </body>
    </html>
  `;
}

function printInHiddenIframe(html) {
  let iframe = document.getElementById('pos-print-iframe');
  if (!iframe) {
    iframe = document.createElement('iframe');
    iframe.id = 'pos-print-iframe';
    iframe.style.position = 'fixed';
    iframe.style.right = '0';
    iframe.style.bottom = '0';
    iframe.style.width = '0';
    iframe.style.height = '0';
    iframe.style.border = '0';
    document.body.appendChild(iframe);
  }

  const doc = iframe.contentWindow.document;
  doc.open();
  doc.write(html);
  doc.close();

  setTimeout(() => {
    iframe.contentWindow.focus();
    iframe.contentWindow.print();
  }, 250);
}

/**
 * In hóa đơn K80. Trong Electron, main process mở một cửa sổ ẩn chỉ chứa HTML hóa đơn rồi in
 * (in thẳng cửa sổ chính sẽ ra ảnh màn hình POS). Trên trình duyệt thì in qua iframe ẩn.
 * @returns {Promise<{success: boolean, error?: string}>}
 */
export async function printInvoiceReceipt(receipt) {
  const html = generateReceiptHtml(receipt);

  if (window.electronAPI && typeof window.electronAPI.printReceipt === 'function') {
    try {
      return await window.electronAPI.printReceipt({ html, silent: true });
    } catch (err) {
      return { success: false, error: err.message };
    }
  }

  printInHiddenIframe(html);
  return { success: true };
}
