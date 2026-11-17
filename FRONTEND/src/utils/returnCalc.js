/**
 * Ước tính tiền hoàn trả trên máy quầy, cùng quy tắc với InvoiceService.createReturn ở backend:
 * mỗi dòng hoàn = đơn giá × số lượng trả × (khách thực trả / tạm tính), làm tròn đến đồng;
 * lần trả cuối (trả hết phần còn lại) hoặc khi vượt số tiền còn lại thì hoàn đúng số tiền còn lại.
 * Server mới là nơi quyết định; số này dùng để hiển thị và xin quản lý duyệt PIN.
 *
 * @param {object} invoice hóa đơn có details[{ id, quantity, returnedQuantity, unitPrice, totalPrice }]
 * @param {Record<number, number>} picks số lượng trả theo id dòng
 * @returns {{ lines: Array<{ id, quantity, amount }>, total: number, isFinal: boolean, remainingMoney: number }}
 */
export function estimateRefund(invoice, picks) {
  const details = invoice?.details || [];
  const paid = Number(invoice?.totalAmount) || 0;
  const already = Number(invoice?.refundedAmount) || 0;
  const subtotal = details.reduce((s, d) => s + (Number(d.totalPrice) || 0), 0);
  const remainingMoney = Math.max(0, paid - already);

  const lines = [];
  let total = 0;
  for (const d of details) {
    const qty = Math.max(0, Math.min(Number(picks?.[d.id]) || 0, remainingQty(d)));
    if (qty <= 0) continue;
    const gross = (Number(d.unitPrice) || 0) * qty;
    const amount = subtotal > 0 ? roundHalfUp((gross * paid) / subtotal) : 0;
    lines.push({ id: d.id, quantity: qty, amount });
    total += amount;
  }
  const isFinal = details.length > 0 && details.every((d) => (Number(picks?.[d.id]) || 0) === remainingQty(d));
  if (lines.length > 0 && (isFinal || total > remainingMoney)) {
    const adjust = remainingMoney - total;
    lines[lines.length - 1].amount = Math.max(0, lines[lines.length - 1].amount + adjust);
    total = remainingMoney;
  }
  return { lines, total, isFinal, remainingMoney };
}

/** Số lượng còn có thể trả của một dòng hóa đơn */
export function remainingQty(detail) {
  return Math.max(0, (Number(detail?.quantity) || 0) - (Number(detail?.returnedQuantity) || 0));
}

// Math.round làm tròn .5 lên cho số dương, khớp RoundingMode.HALF_UP với tiền (luôn dương)
function roundHalfUp(v) {
  return Math.round(v + Number.EPSILON);
}
