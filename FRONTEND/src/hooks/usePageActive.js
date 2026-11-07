import { createContext, useContext } from 'react';

/** true khi màn hình chứa component đang hiển thị (xem components/layout/KeepAlivePage.jsx) */
export const PageActiveContext = createContext(true);

export default function usePageActive() {
  return useContext(PageActiveContext);
}
