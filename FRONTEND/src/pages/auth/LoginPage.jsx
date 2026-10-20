import { useState } from 'react';
import { useAuth } from '../../context/AuthContext';
import Button from '../../components/common/Button';
import './LoginPage.css';

export default function LoginPage({ onSkipOffline }) {
  const { login } = useAuth();
  const [username, setUsername] = useState('admin');
  const [password, setPassword] = useState('123456');
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');

  const handleSubmit = async (e) => {
    if (e) e.preventDefault();
    setErrorMsg('');
    setLoading(true);

    const result = await login(username, password);
    setLoading(false);

    if (!result.success) {
      setErrorMsg(result.message || 'Không thể kết nối đến máy chủ Backend hoặc sai thông tin.');
    }
  };

  const handleQuickSelect = (u, p) => {
    setUsername(u);
    setPassword(p);
  };

  return (
    <div className="login-container">
      <div className="login-card">
        <div className="login-header">
          <div className="login-brand-badge">POS RETAIL & WAREHOUSE SYSTEM</div>
          <h1 className="login-title">Đăng nhập</h1>
          <p className="login-subtitle">Vui lòng đăng nhập tài khoản thu ngân hoặc quản lý</p>
        </div>

        {errorMsg && (
          <div className="login-error">
            {errorMsg}
          </div>
        )}

        <form className="login-form" onSubmit={handleSubmit}>
          <div className="login-field">
            <label>Tên đăng nhập</label>
            <input
              type="text"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="admin / cashier"
              required
              autoFocus
            />
          </div>

          <div className="login-field">
            <label>Mật khẩu</label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••"
              required
            />
          </div>

          <Button
            type="submit"
            variant="success"
            disabled={loading}
            style={{ width: '100%', height: '42px', marginTop: '6px' }}
          >
            {loading ? 'Đang xác thực...' : 'Đăng nhập'}
          </Button>
        </form>

        <div className="login-quick-roles">
          <div className="login-quick-label">Tài khoản mẫu được nạp sẵn</div>
          <div className="login-quick-buttons">
            <button
              type="button"
              className="quick-role-btn"
              onClick={() => handleQuickSelect('admin', '123456')}
            >
              Quản trị (admin)
            </button>
            <button
              type="button"
              className="quick-role-btn"
              onClick={() => handleQuickSelect('cashier', '123456')}
            >
              Thu ngân (cashier)
            </button>
          </div>
        </div>

        {onSkipOffline && (
          <button
            type="button"
            className="login-offline-btn"
            onClick={onSkipOffline}
          >
            Dùng thử ngoại tuyến (Offline Demo)
          </button>
        )}
      </div>
    </div>
  );
}
