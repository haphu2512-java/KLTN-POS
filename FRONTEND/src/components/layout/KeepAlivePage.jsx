import { PageActiveContext } from '../../hooks/usePageActive';

/**
 * Giữ một màn hình đã mở trong cây React để không mất dữ liệu đang nhập khi chuyển màn hình.
 * Màn hình không hiển thị thì ẩn bằng display:none; đang hiển thị thì dùng display:contents
 * để bố cục y như khi render trực tiếp trong khung ứng dụng.
 */
export default function KeepAlivePage({ active, children }) {
  return (
    <div style={{ display: active ? 'contents' : 'none' }} aria-hidden={!active || undefined}>
      <PageActiveContext.Provider value={active}>{children}</PageActiveContext.Provider>
    </div>
  );
}
