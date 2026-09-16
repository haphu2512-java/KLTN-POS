import React, { useState, useEffect } from 'react';
import { Badge, Button } from '../../components/common';
import { formatDateTime } from '../../utils/formatters';

export default function PosHeader({
  branchName = 'Chi nhánh Quận 1',
  cashierName = 'Nguyễn Văn A',
  shiftName = 'Ca Sáng',
  isOnline = true,
  onOpenHelp,
  onNavigateToInventory,
  onNavigateToForecast
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
          <span>Thu ngân: <strong>{cashierName}</strong></span>
          <span className="pos-divider">•</span>
          <span>{shiftName}</span>
        </div>

        <Badge variant={isOnline ? 'success' : 'danger'}>
          {isOnline ? 'Máy chủ: Online' : 'Mất kết nối'}
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
          title="Chuyển sang màn hình Dự báo AI & Báo cáo"
        >
          📈 Dự báo AI
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
      </div>
    </header>
  );
}
