import { useState } from 'react';
import { useAuth } from '../../context/AuthContext';
import { authService } from '../../services/authService';
import Button from '../../components/common/Button';
import './LoginPage.css';

// Mật khẩu tài khoản mẫu cho môi trường demo, đặt trong FRONTEND/.env; để trống thì không điền sẵn
const DEMO_PASSWORD = import.meta.env.VITE_DEMO_PASSWORD || '';

/**
 * Đăng nhập hai bước: nhập tài khoản/mật khẩu, rồi chọn chi nhánh làm việc nếu tài khoản được phân công
 * nhiều chi nhánh (quản trị luôn chọn). Chỉ một chi nhánh thì vào thẳng. Chi nhánh đã chọn dùng cho
 * bán hàng, kho, ca làm và báo cáo của cả phiên; muốn đổi thì đăng xuất và đăng nhập lại.
 */
export default function LoginPage({ onSkipOffline }) {
  const { login } = useAuth();
  const [username, setUsername] = useState('admin');
  const [password, setPassword] = useState(DEMO_PASSWORD);
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');
  // Bước 2: danh sách chi nhánh được chọn (null là đang ở bước 1)
  const [branches, setBranches] = useState(null);
  const [selectedBranchId, setSelectedBranchId] = useState(null);
  const [openShiftBranchId, setOpenShiftBranchId] = useState(null);

  const submit = async (branchId) => {
    setErrorMsg('');
    setLoading(true);
    const result = await login(username, password, branchId);
    setLoading(false);
    if (result.success) return;

    if (result.code === 'BRANCH_SELECTION_REQUIRED' && result.branches?.length) {
      const remembered = authService.getLastBranchId(username);
      const preferred = result.openShiftBranchId || remembered;
      setBranches(result.branches);
      setOpenShiftBranchId(result.openShiftBranchId);
      setSelectedBranchId(
        result.branches.some((b) => b.id === preferred) ? preferred : result.branches[0].id
      );
      return;
    }
    if (result.code === 'SHIFT_OPEN_AT_OTHER_BRANCH' && result.openShiftBranchId) {
      // Chọn sẵn chi nhánh đang có ca để vào đóng ca
      setOpenShiftBranchId(result.openShiftBranchId);
      if (branches?.some((b) => b.id === result.openShiftBranchId)) {
        setSelectedBranchId(result.openShiftBranchId);
      }
    }
    setErrorMsg(result.message || 'Không thể kết nối đến máy chủ Backend hoặc sai thông tin.');
  };

  const handleSubmit = (e) => {
    if (e) e.preventDefault();
    submit(branches ? selectedBranchId : undefined);
  };

  const backToCredentials = () => {
    setBranches(null);
    setSelectedBranchId(null);
    setOpenShiftBranchId(null);
    setErrorMsg('');
  };

  const handleQuickSelect = (u, p) => {
    backToCredentials();
    setUsername(u);
    setPassword(p);
  };

  return (
    <div className="login-container">
      <div className="login-card">
        <div className="login-header">
          <div className="login-brand-badge">POS RETAIL & WAREHOUSE SYSTEM</div>
          <h1 className="login-title">{branches ? 'Chọn chi nhánh' : 'Đăng nhập'}</h1>
          <p className="login-subtitle">
            {branches
              ? `Xin chào ${username}. Chọn chi nhánh bạn làm việc trong phiên này.`
              : 'Vui lòng đăng nhập tài khoản thu ngân hoặc quản lý'}
          </p>
        </div>

        {errorMsg && <div className="login-error">{errorMsg}</div>}

        <form className="login-form" onSubmit={handleSubmit}>
          {branches ? (
            <div className="login-branch-list" role="radiogroup" aria-label="Chi nhánh làm việc">
              {branches.map((b) => (
                <label
                  key={b.id}
                  className={`login-branch-option${selectedBranchId === b.id ? ' is-selected' : ''}`}
                >
                  <input
                    type="radio"
                    name="branch"
                    value={b.id}
                    checked={selectedBranchId === b.id}
                    onChange={() => setSelectedBranchId(b.id)}
                  />
                  <span className="login-branch-text">
                    <span className="login-branch-name">{b.name}</span>
                    {b.address && <span className="login-branch-address">{b.address}</span>}
                    {openShiftBranchId === b.id && (
                      <span className="login-branch-flag">Bạn đang có ca chưa đóng ở đây</span>
                    )}
                  </span>
                </label>
              ))}
            </div>
          ) : (
            <>
              <div className="login-field">
                <label htmlFor="login-username">Tên đăng nhập</label>
                <input
                  id="login-username"
                  type="text"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  placeholder="admin / cashier"
                  required
                  autoFocus
                />
              </div>

              <div className="login-field">
                <label htmlFor="login-password">Mật khẩu</label>
                <input
                  id="login-password"
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="••••••"
                  required
                />
              </div>
            </>
          )}

          <Button
            type="submit"
            variant="success"
            disabled={loading || (branches && !selectedBranchId)}
            style={{ width: '100%', height: '42px', marginTop: '6px' }}
          >
            {loading ? 'Đang xác thực...' : branches ? 'Vào chi nhánh này' : 'Đăng nhập'}
          </Button>

          {branches && (
            <button type="button" className="login-offline-btn" onClick={backToCredentials}>
              Đổi tài khoản
            </button>
          )}
        </form>

        {!branches && (
          <div className="login-quick-roles">
            <div className="login-quick-label">Tài khoản mẫu được nạp sẵn</div>
            <div className="login-quick-buttons">
              <button
                type="button"
                className="quick-role-btn"
                onClick={() => handleQuickSelect('admin', DEMO_PASSWORD)}
              >
                Quản trị (admin)
              </button>
              <button
                type="button"
                className="quick-role-btn"
                onClick={() => handleQuickSelect('cashier', DEMO_PASSWORD)}
              >
                Thu ngân (cashier)
              </button>
            </div>
          </div>
        )}

        {onSkipOffline && !branches && (
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
