package com.pos.auth;

import com.pos.branch.Branch;

/** Chi nhánh hiện cho người dùng chọn ở màn hình đăng nhập */
public record BranchOption(Long id, String name, String address) {
    public static BranchOption of(Branch b) {
        return new BranchOption(b.getId(), b.getName(), b.getAddress());
    }
}
