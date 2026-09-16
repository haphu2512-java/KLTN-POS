import React from 'react';
import './Badge.css';

/**
 * Status and tag badge component
 */
export default function Badge({
  children,
  variant = 'neutral', // success | warning | danger | neutral | brand
  withDot = true,
  className = '',
  ...props
}) {
  return (
    <span className={`badge badge-${variant} ${className}`} {...props}>
      {withDot && <span className="badge-dot" />}
      {children}
    </span>
  );
}
