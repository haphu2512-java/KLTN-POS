/**
 * AI Demand Forecasting Algorithms
 * Directly implements algorithms defined in StepByStep_Implementation.md (Phần 7)
 */

/**
 * 1. Simple Moving Average (Trung bình trượt)
 * @param {number[]} salesHistory - Array of recent daily sales counts
 * @param {number} windowSize - Number of recent days to average (e.g. 7 days)
 * @returns {number} Predicted daily demand
 */
export function predictMovingAverage(salesHistory = [], windowSize = 7) {
  if (!salesHistory || salesHistory.length === 0) return 0;
  const slice = salesHistory.slice(-windowSize);
  const sum = slice.reduce((acc, val) => acc + val, 0);
  return Number((sum / slice.length).toFixed(1));
}

/**
 * 2. Exponential Smoothing (San bằng mũ)
 * F_{t+1} = alpha * Y_t + (1 - alpha) * F_t
 * @param {number[]} salesHistory - Array of daily sales counts
 * @param {number} alpha - Smoothing factor between 0 and 1 (default 0.35)
 * @returns {number} Predicted daily demand
 */
export function predictExponentialSmoothing(salesHistory = [], alpha = 0.35) {
  if (!salesHistory || salesHistory.length === 0) return 0;
  let forecast = salesHistory[0];
  for (let i = 1; i < salesHistory.length; i++) {
    forecast = alpha * salesHistory[i] + (1 - alpha) * forecast;
  }
  return Number(forecast.toFixed(1));
}

/**
 * 3. Mean Absolute Error (MAE - Đánh giá độ sai lệch mô hình)
 * MAE = (1/n) * sum(|Actual_i - Forecast_i|)
 * @param {number[]} actual
 * @param {number[]} predicted
 * @returns {number}
 */
export function calculateMAE(actual = [], predicted = []) {
  if (actual.length === 0 || actual.length !== predicted.length) return 0;
  const totalError = actual.reduce(
    (sum, val, idx) => sum + Math.abs(val - predicted[idx]),
    0
  );
  return Number((totalError / actual.length).toFixed(2));
}

/**
 * 4. Automatic Replenishment Suggestion
 * Formula: If predictedDemand > currentStock => suggestQty = ceil(predictedDemand - currentStock)
 * @param {number} currentStock
 * @param {number} dailyForecast
 * @param {number} forecastDays (default 7 days)
 * @param {number} safetyBuffer (safety stock buffer, e.g. 1.1 = +10%)
 * @returns {{ totalDemand: number, suggestedQty: number, urgency: 'HIGH'|'MEDIUM'|'LOW' }}
 */
export function calculateReplenishment(
  currentStock = 0,
  dailyForecast = 0,
  forecastDays = 7,
  safetyBuffer = 1.1
) {
  const totalDemand = Math.round(dailyForecast * forecastDays * safetyBuffer);
  const shortage = totalDemand - currentStock;
  const suggestedQty = shortage > 0 ? shortage : 0;

  let urgency = 'LOW';
  if (currentStock <= 0) {
    urgency = 'HIGH'; // Hết hàng
  } else if (currentStock <= totalDemand * 0.3) {
    urgency = 'HIGH'; // Dưới 30% nhu cầu
  } else if (suggestedQty > 0) {
    urgency = 'MEDIUM'; // Cần bổ sung
  }

  return {
    totalDemand,
    suggestedQty,
    urgency
  };
}
