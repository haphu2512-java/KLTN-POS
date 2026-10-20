import './Button.css';

/**
 * Universal Button component with Desktop Hotkey badge support
 */
export default function Button({
  children,
  variant = 'secondary', // primary | success | secondary | danger | ghost
  size = 'md',          // sm | md | lg
  hotkey,
  iconLeft,
  iconRight,
  className = '',
  disabled = false,
  type = 'button',
  onClick,
  ...props
}) {
  return (
    <button
      type={type}
      className={`btn btn-${variant} btn-${size} ${className}`}
      disabled={disabled}
      onClick={onClick}
      {...props}
    >
      {iconLeft && <span className="btn-icon-left">{iconLeft}</span>}
      <span className="btn-text">{children}</span>
      {iconRight && <span className="btn-icon-right">{iconRight}</span>}
      {hotkey && <kbd className="btn-hotkey">{hotkey}</kbd>}
    </button>
  );
}
