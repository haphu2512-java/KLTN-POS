package com.pos.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "Tên đăng nhập không được để trống")
    private String username;

    @NotBlank(message = "Mật khẩu không được để trống")
    private String password;

    /** Chi nhánh làm việc của phiên này; để trống khi tài khoản chỉ được gán một chi nhánh */
    private Long branchId;
}
