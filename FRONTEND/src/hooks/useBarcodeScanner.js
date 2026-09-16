import { useEffect, useRef } from 'react';

/**
 * Custom hook to detect high-speed keystrokes from USB/Wireless HID barcode scanners
 * @param {Function} onScan - Callback receiving the scanned barcode string
 * @param {Object} options - { minLength: 3, maxIntervalMs: 50, enabled: true }
 */
export function useBarcodeScanner(onScan, options = {}) {
  const {
    minLength = 3,
    maxIntervalMs = 50,
    enabled = true
  } = options;

  const bufferRef = useRef('');
  const lastTimeRef = useRef(0);
  const onScanRef = useRef(onScan);

  useEffect(() => {
    onScanRef.current = onScan;
  }, [onScan]);

  useEffect(() => {
    if (!enabled) return;

    function handleKeyDown(e) {
      // Ignore modifier keys
      if (['Shift', 'Control', 'Alt', 'Meta', 'CapsLock'].includes(e.key)) {
        return;
      }

      const now = Date.now();
      const interval = now - lastTimeRef.current;

      // If the user took longer than maxIntervalMs, reset buffer unless it's the very first char
      if (interval > maxIntervalMs) {
        bufferRef.current = '';
      }
      lastTimeRef.current = now;

      if (e.key === 'Enter') {
        const scannedCode = bufferRef.current.trim();
        if (scannedCode.length >= minLength) {
          e.preventDefault();
          if (onScanRef.current) {
            onScanRef.current(scannedCode);
          }
        }
        bufferRef.current = '';
      } else if (e.key.length === 1) {
        bufferRef.current += e.key;
      }
    }

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [enabled, minLength, maxIntervalMs]);
}
