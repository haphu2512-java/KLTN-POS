export const PAYMENT_LABELS = { CASH: 'Tiền mặt', VIETQR: 'Chuyển khoản QR', CARD: 'Thẻ' };

// Loại thu/chi két trong ca
export const MOVEMENT_LABELS = { EXPENSE: 'Chi trong ca', DROP: 'Nộp quỹ', FLOAT_IN: 'Bổ sung tiền lẻ' };

export const isoDate = (d) => {
  const local = new Date(d.getTime() - d.getTimezoneOffset() * 60000);
  return local.toISOString().slice(0, 10);
};

export const formatDay = (val) => (val ? String(val).split('-').reverse().join('/') : '');
