import { describe, it, expect } from 'vitest';
import {
  predictMovingAverage,
  predictExponentialSmoothing,
  standardDeviation,
  calculateMAE,
  calculateReplenishment,
} from '../forecasting';

// Cùng số liệu với ForecastCalculatorTest.java để công thức hai phía khớp nhau
describe('công thức dự báo', () => {
  it('độ lệch chuẩn mẫu tính cả ngày không bán', () => {
    expect(standardDeviation([0, 0, 6, 0])).toBeCloseTo(3, 9);
    expect(standardDeviation([5])).toBe(0);
  });

  it('trung bình trượt lấy N ngày gần nhất', () => {
    expect(predictMovingAverage([100, 1, 2, 3], 3)).toBe(2);
    expect(predictMovingAverage([])).toBe(0);
  });

  it('exponential smoothing hội tụ về mức bán ổn định', () => {
    expect(predictExponentialSmoothing(Array(30).fill(10))).toBeCloseTo(10, 6);
  });

  it('MAE', () => {
    expect(calculateMAE([10, 12], [8, 14])).toBe(2);
    expect(calculateMAE([1], [1, 2])).toBe(0);
  });
});

describe('calculateReplenishment', () => {
  it('chưa có lịch sử bán thì không đề xuất nhập', () => {
    const r = calculateReplenishment(0, Array(30).fill(0), 7);
    expect(r.suggestedQty).toBe(0);
    expect(r.urgency).toBe('HIGH');
  });

  it('Q = ⌈ROP + d̄·T − tồn⌉ với ROP = d̄·L + Z·σ·√L', () => {
    const history = Array(30).fill(10);
    const r = calculateReplenishment(5, history, 7, 'MOVING_AVG');
    // σ = 0 → SS = 0, ROP = 10 × 2 = 20, Q = ⌈20 + 70 − 5⌉ = 85
    expect(r.reorderPoint).toBe(20);
    expect(r.suggestedQty).toBe(85);
    expect(r.urgency).toBe('HIGH'); // tồn 5 ≤ nhu cầu trong thời gian giao hàng (20)
  });

  it('tồn đủ thì không đề xuất và mức ưu tiên thấp', () => {
    const r = calculateReplenishment(500, Array(30).fill(10), 7, 'MOVING_AVG');
    expect(r.suggestedQty).toBe(0);
    expect(r.urgency).toBe('LOW');
  });
});
