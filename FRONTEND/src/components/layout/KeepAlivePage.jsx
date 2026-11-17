import { memo } from 'react';
import { PageActiveContext } from '../../hooks/usePageActive';

/**
 * Nội dung màn hình. Khi màn hình đang ẩn thì KHÔNG render lại theo props mới:
 * mỗi lần chuyển màn hình, App tạo props mới cho mọi màn hình đã mở, nếu không chặn ở đây thì
 * các màn hình ẩn (có bảng hàng nghìn dòng như Dự báo AI) cũng render lại và làm giật giao diện.
 * Màn hình ẩn vẫn tự cập nhật khi state/hook bên trong nó đổi; props mới được áp dụng khi hiện lại.
 */
const PageContent = memo(
  function PageContent({ children }) {
    return children;
  },
  (_prev, next) => !next.active
);

/**
 * Giữ một màn hình đã mở trong cây React để không mất dữ liệu đang nhập khi chuyển màn hình.
 * Màn hình không hiển thị thì ẩn bằng display:none; đang hiển thị thì dùng display:contents
 * để bố cục y như khi render trực tiếp trong khung ứng dụng.
 */
export default function KeepAlivePage({ active, children }) {
  return (
    <div style={{ display: active ? 'contents' : 'none' }} aria-hidden={!active || undefined}>
      <PageActiveContext.Provider value={active}>
        <PageContent active={active}>{children}</PageContent>
      </PageActiveContext.Provider>
    </div>
  );
}
