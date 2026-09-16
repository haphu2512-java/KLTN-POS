import React, { forwardRef } from 'react';
import './Input.css';

/**
 * Clean Desktop Form Input with hotkey badge & prefix/suffix support
 */
const Input = forwardRef(function Input(
  {
    label,
    error,
    prefix,
    suffix,
    hotkey,
    size = 'md', // sm | md | lg
    className = '',
    id,
    ...props
  },
  ref
) {
  const inputId = id || (label ? label.toLowerCase().replace(/\s+/g, '-') : undefined);

  return (
    <div className={`input-group ${className}`}>
      {label && (
        <label htmlFor={inputId} className="input-label">
          {label}
        </label>
      )}
      <div className={`input-wrapper input-${size} ${error ? 'has-error' : ''}`}>
        {prefix && <span className="input-prefix">{prefix}</span>}
        <input
          ref={ref}
          id={inputId}
          className="input-control selectable"
          {...props}
        />
        {hotkey && (
          <span className="input-suffix">
            <kbd>{hotkey}</kbd>
          </span>
        )}
        {suffix && <span className="input-suffix">{suffix}</span>}
      </div>
      {error && <span className="input-error-msg">{error}</span>}
    </div>
  );
});

export default Input;
