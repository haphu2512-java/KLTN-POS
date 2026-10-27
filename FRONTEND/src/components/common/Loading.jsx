import './Loading.css';

/**
 * Loading indicator with spinner icon and optional status text
 */
export default function Loading({
  text = 'Đang tải dữ liệu...',
  size = 'md', // 'sm' | 'md' | 'lg'
  inline = false,
  className = '',
  style
}) {
  return (
    <div
      className={`loading-container ${inline ? 'inline' : ''} ${className}`}
      style={style}
      role="status"
      aria-live="polite"
    >
      <span className={`loading-spinner ${size}`} aria-hidden="true" />
      {text && <span className="loading-text">{text}</span>}
    </div>
  );
}
