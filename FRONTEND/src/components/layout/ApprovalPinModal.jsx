import { useState } from 'react';
import { Modal, Button, Input } from '../common';
import { approvalService } from '../../services/approvalService';

/** Quản lý tự đặt/đổi PIN duyệt tại quầy (giảm giá vượt hạn mức của thu ngân) */
export default function ApprovalPinModal({ isOpen, onClose }) {
  const [currentPassword, setCurrentPassword] = useState('');
  const [pin, setPin] = useState('');
  const [confirmPin, setConfirmPin] = useState('');
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const [done, setDone] = useState(false);

  const [prevOpen, setPrevOpen] = useState(isOpen);
  if (isOpen !== prevOpen) {
    setPrevOpen(isOpen);
    if (isOpen) {
      setCurrentPassword('');
      setPin('');
      setConfirmPin('');
      setError('');
      setDone(false);
    }
  }

  const handleSave = async (e) => {
    e.preventDefault();
    if (!/^\d{4,6}$/.test(pin)) {
      setError('PIN phải gồm 4 đến 6 chữ số.');
      return;
    }
    if (pin !== confirmPin) {
      setError('Hai lần nhập PIN không khớp.');
      return;
    }
    setSaving(true);
    setError('');
    try {
      await approvalService.setPin(currentPassword, pin);
      setDone(true);
    } catch (err) {
      setError(err.response?.data?.message || 'Không lưu được PIN.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="PIN duyệt tại quầy"
      maxWidth="420px"
      footer={
        done ? (
          <Button variant="primary" onClick={onClose}>Đóng</Button>
        ) : (
          <>
            <Button variant="secondary" onClick={onClose}>Hủy</Button>
            <Button variant="primary" type="submit" form="approval-pin-form" disabled={saving}>
              {saving ? 'Đang lưu...' : 'Lưu PIN'}
            </Button>
          </>
        )
      }
    >
      {done ? (
        <p style={{ margin: 0 }}>
          Đã lưu PIN. Khi thu ngân giảm giá vượt hạn mức, bạn nhập tài khoản và PIN này ngay trên máy bán hàng để duyệt.
        </p>
      ) : (
        <form id="approval-pin-form" onSubmit={handleSave} style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          <p style={{ margin: 0, fontSize: '13px', color: 'var(--text-muted)' }}>
            PIN dùng để duyệt giảm giá vượt hạn mức của thu ngân. Không chia sẻ PIN cho nhân viên.
          </p>
          <Input
            label="Mật khẩu đăng nhập hiện tại"
            type="password"
            value={currentPassword}
            onChange={(e) => setCurrentPassword(e.target.value)}
            autoComplete="current-password"
            required
          />
          <Input
            label="PIN mới (4-6 chữ số)"
            type="password"
            inputMode="numeric"
            maxLength={6}
            value={pin}
            onChange={(e) => setPin(e.target.value.replace(/\D/g, ''))}
            autoComplete="off"
            required
          />
          <Input
            label="Nhập lại PIN"
            type="password"
            inputMode="numeric"
            maxLength={6}
            value={confirmPin}
            onChange={(e) => setConfirmPin(e.target.value.replace(/\D/g, ''))}
            autoComplete="off"
            required
          />
          {error && <div style={{ color: 'var(--danger-700, #b91c1c)', fontSize: '13px' }}>{error}</div>}
        </form>
      )}
    </Modal>
  );
}
