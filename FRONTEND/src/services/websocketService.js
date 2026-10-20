import { Client } from '@stomp/stompjs';

class WebSocketService {
  constructor() {
    this.client = null;
    this.isConnected = false;
    this.listeners = new Map();
  }

  connect(wsUrl) {
    if (!wsUrl) {
      const isHttps = typeof window !== 'undefined' && window.location.protocol === 'https:';
      const wsProtocol = isHttps ? 'wss:' : 'ws:';
      const host = typeof window !== 'undefined' ? window.location.hostname : 'localhost';
      const envWsUrl = import.meta.env?.VITE_WS_URL;
      wsUrl = envWsUrl || `${wsProtocol}//${host}:8080/ws`;
    }
    if (this.client && this.client.active) {
      return;
    }

    try {
      this.client = new Client({
        brokerURL: wsUrl,
        reconnectDelay: 5000,
        heartbeatIncoming: 4000,
        heartbeatOutgoing: 4000,
        onConnect: () => {
          this.isConnected = true;
          this.notifyConnectionChange(true);

          // Subscribe to inventory updates
          this.client.subscribe('/topic/inventory-updates', (message) => {
            try {
              const data = JSON.parse(message.body);
              this.emit('inventory-update', data);
            } catch (e) {
              console.warn('Invalid inventory-update payload:', message.body);
            }
          });

          // Subscribe to sales updates
          this.client.subscribe('/topic/sales-updates', (message) => {
            try {
              const data = JSON.parse(message.body);
              this.emit('sales-update', data);
            } catch (e) {
              console.warn('Invalid sales-update payload:', message.body);
            }
          });
        },
        onDisconnect: () => {
          this.isConnected = false;
          this.notifyConnectionChange(false);
        },
        onStompError: (frame) => {
          console.warn('STOMP error:', frame.headers['message']);
          this.isConnected = false;
          this.notifyConnectionChange(false);
        },
        onWebSocketClose: () => {
          this.isConnected = false;
          this.notifyConnectionChange(false);
        }
      });

      this.client.activate();
    } catch (err) {
      console.warn('WebSocket connection init failed:', err.message);
      this.isConnected = false;
      this.notifyConnectionChange(false);
    }
  }

  disconnect() {
    if (this.client) {
      this.client.deactivate();
      this.isConnected = false;
      this.notifyConnectionChange(false);
    }
  }

  on(event, callback) {
    if (!this.listeners.has(event)) {
      this.listeners.set(event, new Set());
    }
    this.listeners.get(event).add(callback);
    return () => this.off(event, callback);
  }

  off(event, callback) {
    if (this.listeners.has(event)) {
      this.listeners.get(event).delete(callback);
    }
  }

  emit(event, data) {
    if (this.listeners.has(event)) {
      this.listeners.get(event).forEach((cb) => cb(data));
    }
  }

  notifyConnectionChange(status) {
    this.emit('connection-status', status);
  }
}

export const websocketService = new WebSocketService();
export default websocketService;
