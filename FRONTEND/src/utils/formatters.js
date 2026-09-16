/**
 * Utility functions for retail POS formatting
 */

/**
 * Formats a number into Vietnamese Dong currency string (e.g. 150.000 ₫)
 * @param {number|string} value
 * @returns {string}
 */
export function formatCurrency(value) {
  const num = Number(value) || 0;
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
    maximumFractionDigits: 0
  }).format(num);
}

/**
 * Formats a number with standard thousand separators (e.g. 1.250)
 * @param {number|string} value
 * @returns {string}
 */
export function formatNumber(value) {
  const num = Number(value) || 0;
  return new Intl.NumberFormat('vi-VN').format(num);
}

/**
 * Formats a date into Vietnamese standard time string (DD/MM/YYYY HH:mm:ss)
 * @param {Date|string|number} date
 * @returns {string}
 */
export function formatDateTime(date = new Date()) {
  const d = new Date(date);
  if (isNaN(d.getTime())) return '';
  return new Intl.DateTimeFormat('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false
  }).format(d);
}
