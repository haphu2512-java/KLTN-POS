package com.pos.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRequest {

    @NotBlank(message = "Họ tên không được để trống")
    private String fullName;

    @NotNull(message = "Vai trò không được để trống")
    private Role role;

    private Long branchId;

    private Boolean active;

    private String newPassword;
}
