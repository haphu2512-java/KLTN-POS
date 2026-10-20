import { Button } from '../../components/common';
import { formatCurrency } from '../../utils/formatters';

export default function PosPayment({
  customer,
  onOpenCustomerModal,
  subtotal,
  discount,
  onChangeDiscount,
  finalAmount,
  paymentMethod,
  onChangePaymentMethod,
  receivedAmount,
  onChangeReceivedAmount,
  changeAmount,
  onCancelOrder,
  onProcessPayment,
  isProcessing = false
}) {
  const quickCashOptions = [100000, 200000, 500000];

  return (
    <div className="pos-payment-panel">
      {/* Customer Header Box */}
      <div className="pos-customer-box">
        <div className="pos-customer-info">
          <span className="text-muted" style={{ fontSize: '13px' }}>
            Khách hàng
          </span>
          <div className="font-semibold" style={{ fontSize: '14px', color: 'var(--brand-900)' }}>
            {customer ? customer.name : 'Khách lẻ'}
            {customer?.phone && (
              <span className="text-muted font-normal" style={{ fontSize: '12px', marginLeft: '6px' }}>
                ({customer.phone})
              </span>
            )}
          </div>
        </div>
        <Button size="sm" variant="secondary" onClick={onOpenCustomerModal}>
          Đổi (F3)
        </Button>
      </div>

      {/* Bill Calculation Breakdown */}
      <div className="pos-calc-box">
        <div className="pos-calc-row">
          <span className="text-muted">Tiền hàng</span>
          <span className="font-medium tabular-nums">{formatCurrency(subtotal)}</span>
        </div>

        <div className="pos-calc-row">
          <span className="text-muted">Giảm giá</span>
          <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
            <input
              type="number"
              className="pos-discount-input"
              value={discount || ''}
              onChange={(e) => onChangeDiscount(Math.max(0, Number(e.target.value) || 0))}
              placeholder="0"
              min="0"
            />
            <span className="text-muted" style={{ fontSize: '12px' }}>₫</span>
          </div>
        </div>

        <div className="pos-calc-divider" />

        <div className="pos-calc-row pos-total-row">
          <span className="pos-total-label">Cần thanh toán</span>
          <span className="pos-total-value tabular-nums">{formatCurrency(finalAmount)}</span>
        </div>
      </div>

      {/* Payment Method Selector */}
      <div className="pos-method-box">
        <span className="text-muted" style={{ fontSize: '13px' }}>
          Hình thức thanh toán
        </span>
        <div className="pos-method-buttons">
          <button
            type="button"
            className={`pos-method-btn ${paymentMethod === 'CASH' ? 'active' : ''}`}
            onClick={() => onChangePaymentMethod('CASH')}
          >
            Tiền mặt
          </button>
          <button
            type="button"
            className={`pos-method-btn ${paymentMethod === 'VIETQR' ? 'active' : ''}`}
            onClick={() => onChangePaymentMethod('VIETQR')}
          >
            VietQR
          </button>
          <button
            type="button"
            className={`pos-method-btn ${paymentMethod === 'CARD' ? 'active' : ''}`}
            onClick={() => onChangePaymentMethod('CARD')}
          >
            Thẻ POS
          </button>
        </div>
      </div>

      {/* Cash Tender Calculation (Shown when Cash is active) */}
      {paymentMethod === 'CASH' && (
        <div className="pos-cash-box">
          <div className="pos-calc-row" style={{ marginBottom: '6px' }}>
            <span className="text-muted font-medium">Khách đưa</span>
            <div style={{ position: 'relative', width: '160px' }}>
              <input
                type="number"
                className="pos-received-input"
                value={receivedAmount || ''}
                onChange={(e) => onChangeReceivedAmount(Number(e.target.value) || 0)}
                placeholder="0"
              />
              <span className="pos-input-currency">₫</span>
            </div>
          </div>

          {/* Quick Denominations */}
          <div className="pos-quick-cash">
            <button
              type="button"
              className="pos-cash-chip"
              onClick={() => onChangeReceivedAmount(finalAmount)}
            >
              Đủ tiền
            </button>
            {quickCashOptions.map((amount) => (
              <button
                key={amount}
                type="button"
                className="pos-cash-chip"
                onClick={() => onChangeReceivedAmount(amount)}
              >
                {amount / 1000}k
              </button>
            ))}
          </div>

          <div className="pos-calc-row pos-change-row">
            <span className="text-muted font-medium">Tiền thừa</span>
            <span
              className={`font-bold tabular-nums ${
                changeAmount >= 0 ? 'text-success' : 'text-danger'
              }`}
              style={{ fontSize: '16px' }}
            >
              {changeAmount >= 0
                ? formatCurrency(changeAmount)
                : `Còn thiếu ${formatCurrency(Math.abs(changeAmount))}`}
            </span>
          </div>
        </div>
      )}

      {/* Action Buttons */}
      <div className="pos-actions-box">
        <Button
          variant="danger"
          size="lg"
          hotkey="F9"
          onClick={onCancelOrder}
          className="pos-btn-cancel"
        >
          Hủy đơn
        </Button>
        <Button
          variant="success"
          size="lg"
          hotkey="F4"
          onClick={onProcessPayment}
          disabled={isProcessing || finalAmount <= 0}
          className="pos-btn-pay"
        >
          {isProcessing ? 'Đang xử lý...' : 'Thanh toán'}
        </Button>
      </div>
    </div>
  );
}
