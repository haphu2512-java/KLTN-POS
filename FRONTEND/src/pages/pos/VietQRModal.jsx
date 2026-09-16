import React from 'react';
import { Modal, Button } from '../../components/common';
import { formatCurrency } from '../../utils/formatters';

export default function VietQRModal({
  isOpen,
  onClose,
  amount = 0,
  orderId = 'DH001',
  onConfirmSuccess
}) {
  // Using QuickLink standard format: https://api.vietqr.io/image/970422-0908123456-compact2.jpg?amount=...&addInfo=...
  const bankId = 'MB'; // MB Bank
  const accountNo = '0388999888';
  const accountName = 'CUA HANG TIEN LOI POS';
  const transferContent = `POS ${orderId}`;
  
  const qrUrl = `https://api.vietqr.io/image/${bankId}-${accountNo}-compact2.jpg?amount=${amount}&addInfo=${encodeURIComponent(
    transferContent
  )}&accountName=${encodeURIComponent(accountName)}`;

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="Quét mã VietQR chuyển khoản (Napas247)"
      maxWidth="460px"
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Hủy / Đổi tiền mặt (Esc)
          </Button>
          <Button variant="success" onClick={onConfirmSuccess}>
            ✔ Đã nhận được tiền
          </Button>
        </>
      }
    >
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', textAlign: 'center', gap: '14px' }}>
        <div style={{
          backgroundColor: '#ffffff',
          padding: '12px',
          borderRadius: 'var(--radius-md)',
          border: '1px solid var(--border-subtle)',
          boxShadow: 'var(--shadow-sm)'
        }}>
          <img
            src={qrUrl}
            alt="Mã VietQR Chuyển Khoản"
            style={{ width: '260px', height: 'auto', display: 'block' }}
            onError={(e) => {
              // Fallback placeholder if offline
              e.target.style.display = 'none';
              e.target.nextSibling.style.display = 'flex';
            }}
          />
          <div
            style={{
              display: 'none',
              width: '260px',
              height: '260px',
              alignItems: 'center',
              justifyContent: 'center',
              backgroundColor: 'var(--brand-50)',
              color: 'var(--text-muted)',
              fontSize: '13px',
              flexDirection: 'column',
              gap: '8px'
            }}
          >
            <span>[Mã QR Thanh Toán Offline]</span>
            <strong>{formatCurrency(amount)}</strong>
          </div>
        </div>

        <div style={{ width: '100%', textAlign: 'left', backgroundColor: 'var(--brand-50)', padding: '12px 14px', borderRadius: 'var(--radius-sm)', fontSize: '13px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '4px' }}>
            <span className="text-muted">Số tiền:</span>
            <span className="font-bold tabular-nums text-success" style={{ fontSize: '16px' }}>
              {formatCurrency(amount)}
            </span>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '4px' }}>
            <span className="text-muted">Ngân hàng:</span>
            <span className="font-medium">MB Bank (Quân Đội)</span>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '4px' }}>
            <span className="text-muted">Số tài khoản:</span>
            <span className="font-medium" style={{ fontFamily: 'var(--font-mono)' }}>{accountNo}</span>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '4px' }}>
            <span className="text-muted">Chủ tài khoản:</span>
            <span className="font-medium">{accountName}</span>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <span className="text-muted">Nội dung CK:</span>
            <span className="font-bold" style={{ color: 'var(--primary-700)' }}>{transferContent}</span>
          </div>
        </div>
      </div>
    </Modal>
  );
}
