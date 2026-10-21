import { useEffect, useState } from 'react';
import { branchService } from '../services/branchService';

// Danh sách chi nhánh ít thay đổi: tải một lần, các màn hình dùng chung
let cache = null;
let pending = null;

function loadBranches() {
  if (cache) return Promise.resolve(cache);
  if (!pending) {
    pending = branchService
      .getActiveBranches()
      .then((list) => {
        cache = list;
        return list;
      })
      .finally(() => {
        pending = null;
      });
  }
  return pending;
}

/** Chi nhánh đang hoạt động; khi chưa tải được thì trả về danh sách dự phòng */
export default function useBranches(fallback = []) {
  const [branches, setBranches] = useState(cache || fallback);

  useEffect(() => {
    let alive = true;
    loadBranches()
      .then((list) => alive && setBranches(list))
      .catch(() => {});
    return () => {
      alive = false;
    };
  }, []);

  return branches;
}

export function branchNameOf(branches, id) {
  return branches.find((b) => b.id === Number(id))?.name || (id ? `Chi nhánh ${id}` : '');
}
