import { describe, it, expect } from 'vitest';
import { escapeHtml, receiptFromInvoice, generateReceiptHtml, returnSlipFrom, generateReturnSlipHtml } from '../receiptPrinter';

const invoice = {
  id: 42,
  createdAt: '2026-11-05T19:45:00',
  branch: { name: 'Chi nhánh Quận 1', address: '1 Lê Lợi' },
  customer: null,
  details: [{ id: 1, product: { name: 'Sữa <b>tươi</b>' }, quantity: 2, unitPrice: 18000, totalPrice: 36000 }],
  subtotal: 36000,
  discount: 0,
  totalAmount: 36000,
  receivedAmount: 50000,
  changeAmount: 14000,
  paymentMethod: 'CASH',
};

describe('in hóa đơn', () => {
  it('escape ký tự HTML để tên sản phẩm không chèn được mã', () => {
    expect(escapeHtml('<script>"x"&\'y\'</script>')).toBe('&lt;script&gt;&quot;x&quot;&amp;&#39;y&#39;&lt;/script&gt;');
  });

  it('dữ liệu in lấy từ hóa đơn server: mã HD 6 số, khách lẻ, tiền thối', () => {
    const r = receiptFromInvoice(invoice, 'Thu ngân A');
    expect(r.code).toBe('HD000042');
    expect(r.customerName).toBe('Khách lẻ');
    expect(r.change).toBe(14000);
    expect(r.items).toEqual([{ name: 'Sữa <b>tươi</b>', quantity: 2, unitPrice: 18000 }]);
  });

  it('HTML hóa đơn có mã, tên đã escape, không có thẻ lạ', () => {
    const html = generateReceiptHtml(receiptFromInvoice(invoice, 'Thu ngân A'));
    expect(html).toContain('HD000042');
    expect(html).toContain('Sữa &lt;b&gt;tươi&lt;/b&gt;');
    expect(html).not.toContain('<b>tươi</b>');
  });

  it('in lại bill có dấu BẢN IN LẠI', () => {
    const html = generateReceiptHtml(receiptFromInvoice(invoice, 'A', { at: '2026-11-05T20:00:00', by: 'Quản lý B' }));
    expect(html.toUpperCase()).toContain('BẢN IN LẠI');
  });

  it('phiếu trả hàng ghi hóa đơn gốc và số tiền hoàn', () => {
    const ret = {
      id: 7,
      refundAmount: 17250,
      reason: 'Hỏng',
      createdAt: '2026-11-05T20:05:00',
      details: [{ invoiceDetailId: 1, productName: 'Sữa tươi', quantity: 1, refundAmount: 17250 }],
    };
    const html = generateReturnSlipHtml(returnSlipFrom(invoice, ret, 'Thu ngân A'));
    expect(html).toContain('PHIẾU HOÀN TRẢ');
    expect(html).toContain('PT000007');
    expect(html).toContain('HD000042');
  });
});
