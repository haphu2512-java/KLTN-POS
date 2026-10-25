import { useState, useEffect } from 'react';
import { Button } from '../../components/common';
import { formatDateTime } from '../../utils/formatters';

export default function PosHeader({
  branchName = 'Chi nhánh 1',
  cashierName = 'Thu ngân',
  role = 'CASHIER',
  shiftName = 'Chưa mở ca',
  shiftOpen = false,
  onOpenShift,
  onOpenCashMovement,
  isOnline = true,
  isWsConnected = false,
  terminalName,
  pairedCount = 0,
  onOpenPairing,
  onOpenHelp
}) {
  const [timeStr, setTimeStr] = useState(formatDateTime());

  useEffect(() => {
    const timer = setInterval(() => {
      setTimeStr(formatDateTime());
    }, 1000);
    return () => clearInterval(timer);
  }, []);

  const roleLabel = { ADMIN: 'Quản trị', MANAGER: 'Quản lý', WAREHOUSE: 'Thủ kho' }[role] || 'Thu ngân';
  const status = !isOnline ? 'offline' : isWsConnected ? 'live' : 'online';
  const statusText = {
    offline: 'Ngoại tuyến',
    live: 'Đã kết nối máy chủ',
    online: 'Đang kết nối thời gian thực'
  }[status];

  return (
    <header className="pos-header">
      <div className="pos-header-title">
        <h1 className="pos-branch">{branchName}</h1>
        <span className="pos-shift">
          {shiftName} · {roleLabel} {cashierName}
        </span>
      </div>

      <div className="pos-header-right">
        <span
          className={`pos-status pos-status-${status}`}
          title={isWsConnected ? 'Đồng bộ tồn kho theo thời gian thực' : 'Chưa kết nối đồng bộ thời gian thực'}
        >
          <span className="pos-status-dot" aria-hidden="true" />
          {statusText}
        </span>
        <span className="pos-time">{timeStr}</span>
        {shiftOpen && onOpenCashMovement && (
          <Button variant="secondary" size="sm" onClick={onOpenCashMovement} title="Chi trong ca, nộp quỹ, bổ sung tiền lẻ">
            Thu/chi két
          </Button>
        )}
        {onOpenShift && (
          <Button
            variant={shiftOpen ? 'secondary' : 'primary'}
            size="sm"
            onClick={onOpenShift}
            title={shiftOpen ? 'Đối soát tiền và bàn giao ca' : 'Nhận két và mở ca cho nhân viên được xếp'}
          >
            {shiftOpen ? 'Kết ca' : 'Mở ca'}
          </Button>
        )}
        <Button
          variant="secondary"
          size="sm"
          onClick={onOpenPairing}
          title="Ghép điện thoại quét mã vạch với quầy này"
        >
          {terminalName || 'Quầy POS'} · {pairedCount > 0 ? `${pairedCount} điện thoại` : 'Ghép điện thoại'}
        </Button>
        <Button variant="secondary" size="sm" hotkey="F1" onClick={onOpenHelp} title="Xem hướng dẫn phím tắt">
          Phím tắt
        </Button>
      </div>
    </header>
  );
}
