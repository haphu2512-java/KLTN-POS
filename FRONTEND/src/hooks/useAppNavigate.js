import { useNavigate } from 'react-router-dom';

/**
 * Hook cầu nối: cung cấp các callbacks onNavigateTo* dựa trên React Router.
 * Các page component hiện tại tiếp tục nhận props cũ mà không cần sửa,
 * nhưng bên dưới navigation sử dụng URL history thay vì setState.
 */
export default function useAppNavigate() {
  const navigate = useNavigate();

  return {
    onNavigateToPos: () => navigate('/'),
    onNavigateToInventory: () => navigate('/inventory'),
    onNavigateToForecast: () => navigate('/forecast'),
    onNavigateToReport: () => navigate('/report'),
    onNavigateToPartners: () => navigate('/partners'),
    onNavigateToStaff: () => navigate('/staff'),
    onNavigateToShifts: () => navigate('/shifts'),
  };
}
