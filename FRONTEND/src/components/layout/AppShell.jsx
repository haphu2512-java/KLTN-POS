import { useEffect, useRef, useState } from 'react';
import ApprovalPinModal from './ApprovalPinModal';
import AssistantPanel from '../assistant/AssistantPanel';
import './AppShell.css';

const ICONS = {
  pos: <path d="M6 3h12v18l-3-2-3 2-3-2-3 2zM9 8h6M9 12h6" />,
  inventory: <path d="M3 7.5 12 3l9 4.5v9L12 21l-9-4.5zM3 7.5 12 12l9-4.5M12 12v9" />,
  forecast: <path d="M3 17l6-6 4 4 8-9M15 6h6v6" />,
  report: <path d="M5 20V10M12 20V4M19 20v-7" />,
  shifts: <path d="M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 7v5l3 2" />,
  partners: <path d="M9 11.2a3.2 3.2 0 1 0 0-6.4 3.2 3.2 0 0 0 0 6.4zM3 20c0-3.3 2.7-5.5 6-5.5s6 2.2 6 5.5M16 5.5a3 3 0 0 1 0 5.6M18 14.8c1.7.7 3 2.4 3 5.2" />,
  promotions: <path d="M20.6 13.4 13.4 20.6a2 2 0 0 1-2.8 0L3 13V3h10l7.6 7.6a2 2 0 0 1 0 2.8zM7.5 7.5h.01" />,
  purchaseorder: <path d="M3 3h2l2.4 12.2a2 2 0 0 0 2 1.6h8.2a2 2 0 0 0 2-1.6L21 7H6M10 21a1 1 0 1 0 0-2 1 1 0 0 0 0 2zM18 21a1 1 0 1 0 0-2 1 1 0 0 0 0 2zM12 10h5M14.5 7.5v5" />,
  stockcount: <path d="M9 5H7a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-2M9 5a2 2 0 0 0 2 2h2a2 2 0 0 0 2-2M9 5a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2M9 14l2 2 4-4" />,
  audit: <path d="M9 4h6M8 4H6v17h12V4h-2M9 10h6M9 14h6M9 18h3" />,
  assistant: <path d="M4 5h16v11H9l-5 4zM8.5 10.5h.01M12 10.5h.01M15.5 10.5h.01" />,
  staff: <path d="M12 11a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7zM5 20c0-3.6 3.1-6 7-6s7 2.4 7 6" />,
  pin: <path d="M6 11h12v10H6zM9 11V8a3 3 0 0 1 6 0v3M12 15v2" />,
  logout: <path d="M15 4h4v16h-4M10 8l-4 4 4 4M6 12h10" />,
  collapse: <path d="M15 6l-6 6 6 6" />,
  expand: <path d="M9 6l6 6-6 6" />,
  more: <path d="M12 6h.01M12 12h.01M12 18h.01" />,
};

function RailIcon({ name, size = 20 }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {ICONS[name]}
    </svg>
  );
}

function initialsOf(name = '') {
  const words = name.replace(/\(.*?\)/g, '').trim().split(/\s+/).filter(Boolean);
  if (words.length === 0) return '?';
  const pick = words.length === 1 ? [words[0]] : words.slice(-2);
  return pick.map((w) => w[0]).join('').toUpperCase();
}

const COLLAPSE_KEY = 'pos.sidebarCollapsed';

function readCollapsed() {
  try {
    return localStorage.getItem(COLLAPSE_KEY) === '1';
  } catch {
    return false;
  }
}

const ROLE_LABEL = { ADMIN: 'Quản trị viên', MANAGER: 'Quản lý', CASHIER: 'Thu ngân', WAREHOUSE: 'Thủ kho' };

/**
 * Khung ứng dụng: thanh điều hướng bên trái (nhóm theo chức năng, thu gọn được) + vùng nội dung.
 * Mục nào không có handler (ví dụ không đủ quyền) sẽ không hiện; nhóm không còn mục nào cũng ẩn.
 */
export default function AppShell({
  active,
  currentUser,
  branchName,
  onLogout,
  onNavigateToPos,
  onNavigateToInventory,
  onNavigateToForecast,
  onNavigateToReport,
  onNavigateToPartners,
  onNavigateToStaff,
  onNavigateToShifts,
  onNavigateToPromotions,
  onNavigateToAudit,
  onNavigateToStockCount,
  onNavigateToPurchaseOrders,
  assistantEnabled = false,
  children,
}) {
  const displayName = currentUser?.fullName || currentUser?.username || '';
  const canApprove = currentUser?.role === 'ADMIN' || currentUser?.role === 'MANAGER';
  const [isPinOpen, setIsPinOpen] = useState(false);
  const [isAssistantOpen, setIsAssistantOpen] = useState(false);
  const [collapsed, setCollapsed] = useState(readCollapsed);
  const [menuOpen, setMenuOpen] = useState(false);
  const userRef = useRef(null);

  const toggleCollapsed = () => {
    setCollapsed((v) => {
      try {
        localStorage.setItem(COLLAPSE_KEY, v ? '0' : '1');
      } catch {
        /* bỏ qua: chỉ là tiện ích ghi nhớ */
      }
      return !v;
    });
  };

  // Đóng menu tài khoản khi bấm ra ngoài hoặc nhấn Esc
  useEffect(() => {
    if (!menuOpen) return undefined;
    const onDown = (e) => {
      if (userRef.current && !userRef.current.contains(e.target)) setMenuOpen(false);
    };
    const onKey = (e) => e.key === 'Escape' && setMenuOpen(false);
    document.addEventListener('mousedown', onDown);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDown);
      document.removeEventListener('keydown', onKey);
    };
  }, [menuOpen]);

  const groups = [
    {
      title: 'Bán hàng',
      items: [
        { key: 'pos', label: 'Bán hàng', onClick: onNavigateToPos },
        { key: 'promotions', label: 'Khuyến mãi', onClick: onNavigateToPromotions },
        { key: 'partners', label: 'Khách hàng & NCC', onClick: onNavigateToPartners },
      ],
    },
    {
      title: 'Kho',
      items: [
        { key: 'inventory', label: 'Kho hàng', onClick: onNavigateToInventory },
        { key: 'stockcount', label: 'Kiểm kê', onClick: onNavigateToStockCount },
        { key: 'purchaseorder', label: 'Đặt hàng', onClick: onNavigateToPurchaseOrders },
      ],
    },
    {
      title: 'Phân tích',
      items: [
        { key: 'report', label: 'Báo cáo', onClick: onNavigateToReport },
        { key: 'forecast', label: 'Dự báo AI', onClick: onNavigateToForecast },
        assistantEnabled && {
          key: 'assistant',
          label: 'Trợ lý AI',
          toggle: true,
          title: 'Hỏi trợ lý về doanh thu, tồn kho, hàng sắp hết',
          onClick: () => setIsAssistantOpen((v) => !v),
        },
      ],
    },
    {
      title: 'Quản trị',
      items: [
        { key: 'shifts', label: 'Ca làm', onClick: onNavigateToShifts },
        { key: 'staff', label: 'Tài khoản', onClick: onNavigateToStaff },
        { key: 'audit', label: 'Nhật ký', onClick: onNavigateToAudit },
      ],
    },
  ]
    .map((g) => ({ ...g, items: g.items.filter((item) => item && item.onClick) }))
    .filter((g) => g.items.length > 0);

  const renderItem = (item) => {
    const isActive = item.toggle ? isAssistantOpen : item.key === active;
    return (
      <li key={item.key}>
        <button
          type="button"
          className={`shell-nav-item${isActive ? ' is-active' : ''}`}
          aria-current={!item.toggle && isActive ? 'page' : undefined}
          aria-expanded={item.toggle ? isAssistantOpen : undefined}
          onClick={!item.toggle && isActive ? undefined : item.onClick}
          title={collapsed ? item.label : item.title}
        >
          <RailIcon name={item.key} />
          <span className="shell-nav-label">{item.label}</span>
        </button>
      </li>
    );
  };

  const roleLabel = ROLE_LABEL[currentUser?.role] || currentUser?.role || '';
  const hasMenu = canApprove || onLogout;

  return (
    <div className={`shell${collapsed ? ' is-collapsed' : ''}`}>
      <nav className="shell-rail" aria-label="Chức năng">
        <div className="shell-brand">
          <span className="shell-brand-mark" aria-hidden="true">P</span>
          <span className="shell-brand-name">KLTN POS</span>
          <button
            type="button"
            className="shell-collapse"
            onClick={toggleCollapsed}
            aria-label={collapsed ? 'Mở rộng thanh điều hướng' : 'Thu gọn thanh điều hướng'}
            title={collapsed ? 'Mở rộng' : 'Thu gọn'}
          >
            <RailIcon name={collapsed ? 'expand' : 'collapse'} size={16} />
          </button>
        </div>

        <div className="shell-nav">
          {groups.map((g) => (
            <section key={g.title} className="shell-group" aria-label={g.title}>
              <h2 className="shell-group-title">{g.title}</h2>
              <ul>{g.items.map(renderItem)}</ul>
            </section>
          ))}
        </div>

        <div className="shell-user" ref={userRef}>
          {menuOpen && (
            <div className="shell-user-menu" role="menu">
              {branchName && (
                <div className="shell-user-menu-info">
                  <span className="shell-user-menu-label">Chi nhánh</span>
                  <span>{branchName}</span>
                </div>
              )}
              {canApprove && (
                <button
                  type="button"
                  role="menuitem"
                  className="shell-menu-item"
                  onClick={() => {
                    setMenuOpen(false);
                    setIsPinOpen(true);
                  }}
                  title="Đặt PIN để duyệt giảm giá vượt hạn mức tại quầy"
                >
                  <RailIcon name="pin" size={16} />
                  Đặt PIN duyệt
                </button>
              )}
              {onLogout && (
                <button type="button" role="menuitem" className="shell-menu-item is-danger" onClick={onLogout}>
                  <RailIcon name="logout" size={16} />
                  Đăng xuất
                </button>
              )}
            </div>
          )}
          <button
            type="button"
            className="shell-user-btn"
            onClick={hasMenu ? () => setMenuOpen((v) => !v) : undefined}
            aria-haspopup={hasMenu ? 'menu' : undefined}
            aria-expanded={hasMenu ? menuOpen : undefined}
            title={branchName ? `${displayName} · ${branchName}` : displayName}
          >
            <span className="shell-avatar" aria-hidden="true">{initialsOf(displayName)}</span>
            <span className="shell-user-text">
              <span className="shell-user-name">{displayName}</span>
              <span className="shell-user-meta">{branchName || roleLabel}</span>
            </span>
            {hasMenu && (
              <span className="shell-user-more" aria-hidden="true">
                <RailIcon name="more" size={16} />
              </span>
            )}
          </button>
        </div>
      </nav>
      <div className="shell-main">{children}</div>
      {assistantEnabled && (
        <AssistantPanel
          isOpen={isAssistantOpen}
          onClose={() => setIsAssistantOpen(false)}
          currentUser={currentUser}
        />
      )}
      {canApprove && <ApprovalPinModal isOpen={isPinOpen} onClose={() => setIsPinOpen(false)} />}
    </div>
  );
}
