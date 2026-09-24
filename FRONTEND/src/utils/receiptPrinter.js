import { formatCurrency, formatDateTime } from './formatters';

export function generateReceiptHtml(order, invoiceCode = `HD${Date.now().toString().slice(-6)}`, branchName = 'Chi nhánh Quận 1', cashierName = 'Nguyễn Văn A') {
  const itemsHtml = order.items.map(item => `
    <tr>
      <td style="padding: 4px 0; font-size: 12px;">${item.name}</td>
      <td style="padding: 4px 0; text-align: center; font-size: 12px;">${item.quantity}</td>
      <td style="padding: 4px 0; text-align: right; font-size: 12px;">${formatCurrency(item.price)}</td>
      <td style="padding: 4px 0; text-align: right; font-size: 12px; font-weight: bold;">${formatCurrency(item.price * item.quantity)}</td>
    </tr>
  `).join('');

  const subtotal = order.items.reduce((sum, it) => sum + it.price * it.quantity, 0);
  const discount = order.discount || 0;
  const total = Math.max(0, subtotal - discount);
  const received = order.receivedAmount || total;
  const change = Math.max(0, received - total);

  return `
    <!DOCTYPE html>
    <html>
    <head>
      <meta charset="utf-8" />
      <title>Hóa Đơn ${invoiceCode}</title>
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
        <div style="font-size: 11px;">${branchName}</div>
        <div style="font-size: 10px;">Hotline: 1900 1234 • Giờ mở cửa: 07:00 - 22:00</div>
      </div>

      <div class="divider"></div>

      <div>
        <div>Mã HĐ: <span class="bold">${invoiceCode}</span></div>
        <div>Ngày giờ: ${formatDateTime()}</div>
        <div>Thu ngân: ${cashierName}</div>
        <div>Khách hàng: ${order.customer ? order.customer.name : 'Khách lẻ'}</div>
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
          <td class="text-right">${formatCurrency(subtotal)}</td>
        </tr>
        ${discount > 0 ? `
        <tr>
          <td>Giảm giá / Chiết khấu:</td>
          <td class="text-right">-${formatCurrency(discount)}</td>
        </tr>
        ` : ''}
        <tr style="font-size: 14px;" class="bold">
          <td>THANH TOÁN:</td>
          <td class="text-right">${formatCurrency(total)}</td>
        </tr>
        <tr>
          <td>Tiền khách đưa (${order.paymentMethod}):</td>
          <td class="text-right">${formatCurrency(received)}</td>
        </tr>
        <tr>
          <td>Tiền thừa trả khách:</td>
          <td class="text-right bold">${formatCurrency(change)}</td>
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

export async function printInvoiceReceipt(order, invoiceCode, branchName, cashierName) {
  const html = generateReceiptHtml(order, invoiceCode, branchName, cashierName);

  // If running in Electron Desktop App
  if (window.electronAPI && typeof window.electronAPI.printReceipt === 'function') {
    // Write to hidden iframe in current window and trigger silent print
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

    setTimeout(async () => {
      try {
        await window.electronAPI.printReceipt({ silent: true });
      } catch (err) {
        console.warn('Silent print failed, falling back to window.print:', err);
        iframe.contentWindow.print();
      }
    }, 250);
    return;
  }

  // Web Browser fallback: use hidden iframe and window.print()
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
