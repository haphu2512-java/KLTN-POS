import { createContext, useContext, useState, useEffect } from 'react';
import { authService } from '../services/authService';
import { websocketService } from '../services/websocketService';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => authService.getCurrentUser());
  const [isBackendOnline, setIsBackendOnline] = useState(() => !!localStorage.getItem('pos_token'));
  const [isWsConnected, setIsWsConnected] = useState(false);
  // Chi nhánh của phiên đăng nhập: chọn lúc đăng nhập và nằm trong token, muốn đổi phải đăng nhập lại
  const branchId = user?.branchId || null;
  const branchName = user?.branchName || (branchId ? `Chi nhánh ${branchId}` : '');

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

  /**
   * Kết quả: { success, data } khi vào được; { success: false, code, message, branches, openShiftBranchId }
   * khi cần chọn chi nhánh (code = BRANCH_SELECTION_REQUIRED) hoặc bị từ chối.
   */
  const login = async (username, password, selectedBranchId) => {
    try {
      const data = await authService.login(username, password, selectedBranchId);
      setUser(data);
      setIsBackendOnline(true);
      return { success: true, data };
    } catch (err) {
      const body = err.response?.data;
      return {
        success: false,
        code: body?.code,
        message: body?.message || err.message || 'Đăng nhập thất bại',
        branches: body?.data?.branches || null,
        openShiftBranchId: body?.data?.openShiftBranchId || null
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
        branchName,
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

// eslint-disable-next-line react-refresh/only-export-components -- hook đi kèm Provider
export function useAuth() {
  return useContext(AuthContext);
}
