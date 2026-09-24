import React, { createContext, useContext, useState, useEffect } from 'react';
import { authService } from '../services/authService';
import { websocketService } from '../services/websocketService';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => authService.getCurrentUser());
  const [isBackendOnline, setIsBackendOnline] = useState(false);
  const [isWsConnected, setIsWsConnected] = useState(false);
  const [branchId, setBranchId] = useState(1);

  // Check auth expiration and WebSocket status
  useEffect(() => {
    const handleAuthExpired = () => {
      setUser(null);
    };
    window.addEventListener('pos:auth_expired', handleAuthExpired);

    // Listen to WS connection status
    const unsubWs = websocketService.on('connection-status', (status) => {
      setIsWsConnected(status);
    });

    // Try connecting to WS if user logged in
    websocketService.connect();

    return () => {
      window.removeEventListener('pos:auth_expired', handleAuthExpired);
      unsubWs();
    };
  }, []);

  const login = async (username, password) => {
    try {
      const data = await authService.login(username, password);
      setUser(data);
      if (data.branchId) setBranchId(data.branchId);
      setIsBackendOnline(true);
      return { success: true, data };
    } catch (err) {
      // If backend is down or invalid credentials
      return {
        success: false,
        message: err.response?.data?.message || err.message || 'Đăng nhập thất bại'
      };
    }
  };

  const logout = () => {
    authService.logout();
    setUser(null);
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        branchId,
        setBranchId,
        isBackendOnline,
        setIsBackendOnline,
        isWsConnected,
        login,
        logout,
        isAuthenticated: !!user,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}
