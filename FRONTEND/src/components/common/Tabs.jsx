import React from 'react';
import './Tabs.css';

/**
 * Multi-order tab component for POS with add and close capabilities
 */
export default function Tabs({
  tabs = [], // [{ id, label, closable }]
  activeId,
  onSelect,
  onClose,
  onAdd,
  className = ''
}) {
  return (
    <div className={`tabs-nav ${className}`}>
      {tabs.map((tab) => (
        <button
          key={tab.id}
          type="button"
          className={`tab-item ${activeId === tab.id ? 'active' : ''}`}
          onClick={() => onSelect(tab.id)}
        >
          <span>{tab.label}</span>
          {tab.closable && onClose && (
            <span
              className="tab-close"
              onClick={(e) => {
                e.stopPropagation();
                onClose(tab.id);
              }}
              title="Đóng đơn"
            >
              ✕
            </span>
          )}
        </button>
      ))}
      {onAdd && (
        <button
          type="button"
          className="tab-add-btn"
          onClick={onAdd}
          title="Tạo đơn hàng mới (F8)"
        >
          +
        </button>
      )}
    </div>
  );
}
