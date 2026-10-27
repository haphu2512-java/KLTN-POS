/**
 * Công thức dự báo phía frontend, chỉ dùng khi không gọi được backend (chế độ ngoại tuyến).
 * Bám đúng ForecastCalculator.java và AI_for_POS.md (mục 3, 4) để hai nơi cho cùng kết quả.
 */

export const FORECAST_DEFAULTS = {
  alpha: 0.35, // Hệ số làm mịn Exponential Smoothing
  maWindow: 7, // Cửa sổ trung bình trượt
  z: 1.65, // Mức phục vụ 95%
  leadTimeDays: 2 // Thời gian giao hàng L
};

/**
 * Trung bình trượt N ngày gần nhất.
 * @param {number[]} salesHistory - Số lượng bán theo ngày, ngày cũ nhất trước
 * @param {number} windowSize
 * @returns {number}
 */
export function predictMovingAverage(salesHistory = [], windowSize = FORECAST_DEFAULTS.maWindow) {
  if (!salesHistory || salesHistory.length === 0) return 0;
  const slice = salesHistory.slice(-windowSize);
  return slice.reduce((acc, val) => acc + val, 0) / slice.length;
}

/**
 * Exponential Smoothing: F(t+1) = α·Y(t) + (1 − α)·F(t).
 * Khởi tạo bằng trung bình 7 ngày đầu, giống backend.
 * @param {number[]} salesHistory
 * @param {number} alpha
 * @returns {number}
 */
export function predictExponentialSmoothing(salesHistory = [], alpha = FORECAST_DEFAULTS.alpha) {
  if (!salesHistory || salesHistory.length === 0) return 0;
  let forecast = predictMovingAverage(salesHistory.slice(0, FORECAST_DEFAULTS.maWindow), FORECAST_DEFAULTS.maWindow);
  for (const actual of salesHistory) {
    forecast = alpha * actual + (1 - alpha) * forecast;
  }
  return forecast;
}

/** Độ lệch chuẩn mẫu, tính cả những ngày không bán (giá trị 0). */
export function standardDeviation(values = []) {
  if (values.length < 2) return 0;
  const mean = values.reduce((a, b) => a + b, 0) / values.length;
  const sq = values.reduce((sum, x) => sum + (x - mean) ** 2, 0);
  return Math.sqrt(sq / (values.length - 1));
}

/**
 * MAE = (1/n) Σ |thực tế − dự báo|
 * @param {number[]} actual
 * @param {number[]} predicted
 * @returns {number}
 */
export function calculateMAE(actual = [], predicted = []) {
  if (actual.length === 0 || actual.length !== predicted.length) return 0;
  const totalError = actual.reduce((sum, val, idx) => sum + Math.abs(val - predicted[idx]), 0);
  return Number((totalError / actual.length).toFixed(2));
}

/**
 * Đề xuất nhập hàng theo AI_for_POS.md:
 *   SS = Z × σ × √L,  ROP = d̄ × L + SS,  Q = max(0, ⌈ROP + d̄ × T − tồn kho⌉)
 * Sản phẩm chưa có lịch sử bán thì không đề xuất nhập (không bịa dữ liệu).
 * @param {number} currentStock
 * @param {number[]} salesHistory - Số lượng bán theo ngày, đã điền 0 cho ngày không bán
 * @param {number} forecastDays - Chu kỳ đặt hàng T
 * @param {'EXP_SMOOTHING'|'MOVING_AVG'} algorithm
 */
export function calculateReplenishment(currentStock = 0, salesHistory = [], forecastDays = 7, algorithm = 'EXP_SMOOTHING') {
  const { z, leadTimeDays } = FORECAST_DEFAULTS;
  const hasHistory = salesHistory.some((x) => x > 0);
  if (!hasHistory) {
    return {
      hasHistory, dailyRate: 0, totalDemand: 0, safetyStock: 0, reorderPoint: 0, suggestedQty: 0,
      urgency: currentStock <= 0 ? 'HIGH' : 'LOW'
    };
  }
  const dailyRate = algorithm === 'MOVING_AVG'
    ? predictMovingAverage(salesHistory)
    : predictExponentialSmoothing(salesHistory);
  const safetyStock = z * standardDeviation(salesHistory) * Math.sqrt(leadTimeDays);
  const reorderPoint = dailyRate * leadTimeDays + safetyStock;
  const raw = Math.round((reorderPoint + dailyRate * forecastDays - currentStock) * 1e6) / 1e6;
  const suggestedQty = Math.max(0, Math.ceil(raw));

  let urgency = 'LOW';
  if (currentStock <= 0 || currentStock <= dailyRate * leadTimeDays) urgency = 'HIGH';
  else if (currentStock <= reorderPoint) urgency = 'MEDIUM';

  const round2 = (v) => Math.round(v * 100) / 100;
  return {
    hasHistory,
    dailyRate: round2(dailyRate),
    totalDemand: Math.floor(dailyRate * forecastDays + 0.5),
    safetyStock: round2(safetyStock),
    reorderPoint: round2(reorderPoint),
    suggestedQty,
    urgency
  };
}
