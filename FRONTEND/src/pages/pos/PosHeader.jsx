import React, { useState, useEffect } from 'react';
import { Badge, Button } from '../../components/common';
import { formatDateTime } from '../../utils/formatters';

export default function PosHeader({
  branchName = 'Chi nhánh Quận 1',
  cashierName = 'Nguyễn Văn A',
  role = 'CASHIER',
  shiftName = 'Ca Sáng',
  isOnline = true,
  isWsConnected = false,
  onOpenHelp,
  onLogout,
  onNavigateToInventory,
  onNavigateToForecast,
  onNavigateToReport
}) {
  const [timeStr, setTimeStr] = useState(formatDateTime());

  useEffect(() => {
    const timer = setInterval(() => {
      setTimeStr(formatDateTime());
    }, 1000);
    return () => clearInterval(timer);
  }, []);

  return (
    <header className="pos-header">
      <div className="pos-header-left">
        <div className="pos-logo">
          <span className="pos-logo-icon">🏪</span>
          <span className="pos-logo-title">POS RETAIL</span>
        </div>
        <div className="pos-branch-badge">
          <Badge variant="brand" withDot={false}>{branchName}</Badge>
        </div>
      </div>

      <div className="pos-header-center">
        <span className="pos-time">{timeStr}</span>
      </div>

      <div className="pos-header-right">
        <div className="pos-user-info">
          <span>{role === 'ADMIN' ? '👑 Quản lý' : '💼 Thu ngân'}: <strong>{cashierName}</strong></span>
          <span className="pos-divider">•</span>
          <span>{shiftName}</span>
        </div>

        <Badge variant={isOnline ? 'success' : 'secondary'} title={isWsConnected ? 'STOMP WebSocket Realtime: Đã kết nối' : 'WebSocket: Đang chờ...'}>
          {isOnline ? (isWsConnected ? '🟢 Máy chủ & WS' : '🟢 API Online') : '⚪ Ngoại tuyến'}
        </Badge>

        <Button
          variant="secondary"
          size="sm"
          onClick={onNavigateToInventory}
          title="Chuyển sang màn hình Quản lý Kho & Nhập hàng"
        >
          📦 Quản lý Kho
        </Button>

        <Button
          variant="secondary"
          size="sm"
          onClick={onNavigateToForecast}
          title="Chuyển sang màn hình Dự báo AI"
        >
          📈 Dự báo AI
        </Button>

        <Button
          variant="secondary"
          size="sm"
          onClick={onNavigateToReport}
          title="Xem báo cáo doanh thu & sản phẩm bán chạy"
        >
          📊 Báo cáo
        </Button>

        <Button
          variant="ghost"
          size="sm"
          hotkey="F1"
          onClick={onOpenHelp}
          title="Xem hướng dẫn phím tắt"
        >
          Trợ giúp
        </Button>

        {onLogout && (
          <Button
            variant="danger"
            size="sm"
            onClick={onLogout}
            title="Đăng xuất khỏi ca làm việc"
          >
            Đăng xuất
          </Button>
        )}
      </div>
    </header>
  );
}
