package com.pos.support;

import com.pos.user.Role;
import com.pos.user.User;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Tiện ích test: chi nhánh mặc định để ký token và phân công chi nhánh khi tạo nhân viên */
public final class TestAuth {

    private TestAuth() {
    }

    /** Chi nhánh đăng nhập mặc định: chi nhánh nhỏ nhất được gán, ADMIN dùng chi nhánh 1 */
    public static Long branchOf(User user) {
        if (user.getRole() == Role.ADMIN || user.getBranchIds().isEmpty()) {
            return 1L;
        }
        return Collections.min(user.getBranchIds());
    }

    public static Set<Long> branches(Long... ids) {
        return new HashSet<>(Set.of(ids));
    }
}
