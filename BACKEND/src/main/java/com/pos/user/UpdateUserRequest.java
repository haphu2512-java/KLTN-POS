package com.pos.user;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRequest {

    /** Tên hiển thị (không bắt buộc, tài khoản chỉ để phân quyền); để trống thì giữ nguyên */
    @Size(max = 100, message = "Tên hiển thị tối đa 100 ký tự")
    private String fullName;

    @NotNull(message = "Vai trò không được để trống")
    private Role role;

    /** Các chi nhánh được phân công; bắt buộc ít nhất 1 với vai trò khác ADMIN */
    private Set<Long> branchIds;

    private Boolean active;

    private String newPassword;
}
