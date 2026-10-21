package com.pos.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

    @NotBlank(message = "Họ tên không được để trống")
    private String fullName;

    @NotNull(message = "Vai trò không được để trống")
    private Role role;

    /** Các chi nhánh được phân công; bắt buộc ít nhất 1 với vai trò khác ADMIN */
    private Set<Long> branchIds;

    private Boolean active;

    private String newPassword;
}
