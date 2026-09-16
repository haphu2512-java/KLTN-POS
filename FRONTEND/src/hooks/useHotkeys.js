import { useEffect } from 'react';

/**
 * Custom hook to register global function keys and shortcuts for POS operations
 * @param {Object} keyMap - e.g. { 'F2': () => {}, 'F4': () => {}, 'Escape': () => {} }
 * @param {boolean} enabled - Whether listeners are active
 */
export function useHotkeys(keyMap, enabled = true) {
  useEffect(() => {
    if (!enabled) return;

    function handleKeyDown(e) {
      const handler = keyMap[e.key];
      if (handler) {
        // Prevent default browser actions for standard POS keys like F1, F3, F4, etc.
        if (['F1', 'F2', 'F3', 'F4', 'F8', 'F9'].includes(e.key)) {
          e.preventDefault();
        }
        handler(e);
      }
    }

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [keyMap, enabled]);
}
