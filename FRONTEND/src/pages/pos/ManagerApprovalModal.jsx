import { useState } from 'react';
import { Modal, Button, Input } from '../../components/common';
import { approvalService } from '../../services/approvalService';
import { formatCurrency } from '../../utils/formatters';

/**
 * Quản lý nhập tài khoản + PIN ngay trên máy POS để duyệt cho thu ngân:
 * purpose = 'discount' (giảm giá vượt hạn mức) hoặc 'refund' (hoàn trả hàng).
 * onApproved nhận { token, approverName, maxDiscount }.
 */
export default function ManagerApprovalModal({
  isOpen,
  onClose,
  amount,
  maxWithoutApproval,
  reason,
  onApproved,
  purpose = 'discount'
}) {
  const isRefund = purpose === 'refund';
  const [username, setUsername] = useState('');
  const [pin, setPin] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const [prevOpen, setPrevOpen] = useState(isOpen);
  if (isOpen !== prevOpen) {
    setPrevOpen(isOpen);
    if (isOpen) {
      setPin('');
      setError('');
    }
  }

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!username.trim() || !pin) return;
    setLoading(true);
    setError('');
    try {
      const grant = isRefund
        ? await approvalService.approveRefund(username.trim(), pin, amount)
        : await approvalService.approveDiscount(username.trim(), pin, amount);
      onApproved(grant);
    } catch (err) {
      setError(err.response?.data?.message || 'Không duyệt được, kiểm tra kết nối.');
      setPin('');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={isRefund ? 'Cần quản lý duyệt hoàn trả' : 'Cần quản lý duyệt giảm giá'}
      maxWidth="420px"
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Hủy</Button>
          <Button variant="primary" type="submit" form="manager-approval-form" disabled={loading || !username.trim() || !pin}>
            {loading ? 'Đang kiểm tra...' : 'Duyệt'}
          </Button>
        </>
      }
    >
      <form id="manager-approval-form" onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
        <div style={{ fontSize: '13px', lineHeight: 1.5 }}>
          {isRefund ? 'Hoàn cho khách' : 'Giảm'} <strong>{formatCurrency(amount)}</strong>
          {maxWithoutApproval != null && (
            <> (thu ngân được tự giảm tối đa {formatCurrency(maxWithoutApproval)})</>
          )}
          {reason && (
            <div style={{ color: 'var(--text-muted)' }}>Lý do: {reason}</div>
          )}
        </div>
        <Input
          label="Tài khoản quản lý"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          autoComplete="off"
          autoFocus
        />
        <Input
          label="PIN duyệt"
          type="password"
          inputMode="numeric"
          maxLength={6}
          value={pin}
          onChange={(e) => setPin(e.target.value.replace(/\D/g, ''))}
          autoComplete="off"
        />
        {error && <div style={{ color: 'var(--danger-700, #b91c1c)', fontSize: '13px' }}>{error}</div>}
      </form>
    </Modal>
  );
}
