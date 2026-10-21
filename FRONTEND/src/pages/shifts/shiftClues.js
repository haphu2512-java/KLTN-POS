import { formatCurrency } from '../../utils/formatters';
import { PAYMENT_LABELS } from './shiftUtils';

const same = (a, b) => Math.abs(Number(a || 0) - Number(b || 0)) < 0.5;

/**
 * Gợi ý nguyên nhân thiếu/dư từ các hóa đơn của ca: tìm khoản tiền khớp đúng số chênh lệch.
 * Chỉ là gợi ý để thu ngân/quản trị kiểm tra lại, không kết luận thay.
 */
export function findClues(shift, invoices) {
  if (!shift || shift.cashDifference == null) return [];
  const diff = Number(shift.cashDifference);
  if (diff === 0) return [];
  const gap = Math.abs(diff);
  const clues = [];

  invoices.forEach((inv) => {
    const code = `#${inv.id}`;
    const isCash = inv.paymentMethod === 'CASH';
    if (inv.kind === 'SALE' && isCash && same(inv.totalAmount, gap)) {
      clues.push(
        diff < 0
          ? `Thiếu đúng bằng hóa đơn tiền mặt ${code} (${formatCurrency(inv.totalAmount)}): kiểm tra đã thu tiền của khách chưa.`
          : `Dư đúng bằng hóa đơn tiền mặt ${code}: kiểm tra hóa đơn này có bị ghi hai lần hay khách trả bằng cách khác không.`
      );
    }
    if (inv.kind === 'SALE' && !isCash && diff > 0 && same(inv.totalAmount, gap)) {
      clues.push(
        `Dư đúng bằng hóa đơn ${code} ghi "${PAYMENT_LABELS[inv.paymentMethod] || inv.paymentMethod}": ` +
          'có thể khách trả tiền mặt nhưng bấm nhầm phương thức thanh toán.'
      );
    }
    if (inv.kind === 'SALE' && isCash && diff < 0 && Number(inv.changeAmount) > 0 && same(inv.changeAmount, gap)) {
      clues.push(`Thiếu đúng bằng tiền thối của hóa đơn ${code} (${formatCurrency(inv.changeAmount)}): có thể đã thối tiền hai lần.`);
    }
    if (inv.refundCountedHere && isCash && diff > 0 && same(inv.totalAmount, gap)) {
      clues.push(`Dư đúng bằng tiền hoàn trả hóa đơn ${code}: kiểm tra đã chi tiền hoàn trả cho khách chưa.`);
    }
  });

  if (clues.length === 0) {
    clues.push(
      `Không có hóa đơn nào khớp đúng ${formatCurrency(gap)}. Kiểm tra lại tiền đầu ca nhận bàn giao, ` +
        'các khoản thu chi ngoài hệ thống và ghi chú bàn giao.'
    );
  }
  return clues;
}
