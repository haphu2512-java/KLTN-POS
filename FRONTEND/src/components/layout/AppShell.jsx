import './AppShell.css';

const ICONS = {
  pos: <path d="M6 3h12v18l-3-2-3 2-3-2-3 2zM9 8h6M9 12h6" />,
  inventory: <path d="M3 7.5 12 3l9 4.5v9L12 21l-9-4.5zM3 7.5 12 12l9-4.5M12 12v9" />,
  forecast: <path d="M3 17l6-6 4 4 8-9M15 6h6v6" />,
  report: <path d="M5 20V10M12 20V4M19 20v-7" />,
  shifts: <path d="M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 7v5l3 2" />,
  partners: <path d="M9 11.2a3.2 3.2 0 1 0 0-6.4 3.2 3.2 0 0 0 0 6.4zM3 20c0-3.3 2.7-5.5 6-5.5s6 2.2 6 5.5M16 5.5a3 3 0 0 1 0 5.6M18 14.8c1.7.7 3 2.4 3 5.2" />,
  staff: <path d="M12 11a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7zM5 20c0-3.6 3.1-6 7-6s7 2.4 7 6" />,
};

function RailIcon({ name }) {
  return (
    <svg
      width="22"
      height="22"
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

/**
 * Khung ứng dụng: thanh điều hướng dọc bên trái + vùng nội dung của từng màn hình.
 * Mục nào không có handler (ví dụ không đủ quyền) sẽ không hiện.
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
  children,
}) {
  const items = [
    { key: 'pos', label: 'Bán hàng', onClick: onNavigateToPos },
    { key: 'inventory', label: 'Kho hàng', onClick: onNavigateToInventory },
    { key: 'forecast', label: 'Dự báo', onClick: onNavigateToForecast },
    { key: 'shifts', label: 'Ca làm', onClick: onNavigateToShifts },
    { key: 'report', label: 'Báo cáo', onClick: onNavigateToReport },
    { key: 'partners', label: 'Đối tác', onClick: onNavigateToPartners },
    { key: 'staff', label: 'Tài khoản', onClick: onNavigateToStaff },
  ].filter((item) => item.onClick);

  const displayName = currentUser?.fullName || currentUser?.username || '';

  return (
    <div className="shell">
      <nav className="shell-rail" aria-label="Chức năng">
        {items.map((item) => (
          <button
            key={item.key}
            type="button"
            className={`shell-rail-item${item.key === active ? ' is-active' : ''}`}
            aria-current={item.key === active ? 'page' : undefined}
            onClick={item.key === active ? undefined : item.onClick}
          >
            <RailIcon name={item.key} />
            {item.label}
          </button>
        ))}
        <div className="shell-rail-spacer" />
        <div className="shell-rail-user">
          <div className="shell-avatar" aria-hidden="true">{initialsOf(displayName)}</div>
          <span className="shell-user-name" title={displayName}>{displayName}</span>
          {branchName && (
            <span className="shell-user-branch" title={`Đang làm việc tại ${branchName}`}>{branchName}</span>
          )}
          {onLogout && (
            <button type="button" className="shell-rail-item shell-logout" onClick={onLogout}>
              Đăng xuất
            </button>
          )}
        </div>
      </nav>
      <div className="shell-main">{children}</div>
    </div>
  );
}
