import { describe, it, expect } from 'vitest';
import { estimateRefund, remainingQty } from '../returnCalc';

// Hóa đơn 96.000 tạm tính, giảm 4.000 → khách trả 92.000 (giống ca kiểm thử ở backend)
const invoice = {
  totalAmount: 92000,
  refundedAmount: 0,
  details: [
    { id: 1, quantity: 1, returnedQuantity: 0, unitPrice: 36000, totalPrice: 36000 },
    { id: 2, quantity: 1, returnedQuantity: 0, unitPrice: 24000, totalPrice: 24000 },
    { id: 3, quantity: 2, returnedQuantity: 0, unitPrice: 18000, totalPrice: 36000 },
  ],
};

describe('estimateRefund', () => {
  it('chia khoản giảm theo tỉ lệ cho món trả, làm tròn đến đồng', () => {
    const r = estimateRefund(invoice, { 1: 1 });
    // 36.000 × 92.000 / 96.000 = 34.500
    expect(r.total).toBe(34500);
    expect(r.lines).toEqual([{ id: 1, quantity: 1, amount: 34500 }]);
    expect(r.isFinal).toBe(false);
  });

  it('không cho trả quá số còn lại của dòng', () => {
    const r = estimateRefund(invoice, { 3: 5 });
    expect(r.lines[0].quantity).toBe(2);
  });

  it('lần trả cuối hoàn đúng số tiền còn lại, không lệch vì làm tròn', () => {
    const partly = {
      ...invoice,
      refundedAmount: 34500,
      details: invoice.details.map((d) => (d.id === 1 ? { ...d, returnedQuantity: 1 } : d)),
    };
    const r = estimateRefund(partly, { 1: 0, 2: 1, 3: 2 });
    expect(r.isFinal).toBe(true);
    expect(r.total).toBe(92000 - 34500);
    expect(r.lines.reduce((s, l) => s + l.amount, 0)).toBe(57500);
  });

  it('không chọn gì thì hoàn 0', () => {
    expect(estimateRefund(invoice, {}).total).toBe(0);
  });

  it('remainingQty không âm', () => {
    expect(remainingQty({ quantity: 2, returnedQuantity: 3 })).toBe(0);
    expect(remainingQty({ quantity: 5, returnedQuantity: 2 })).toBe(3);
  });
});
