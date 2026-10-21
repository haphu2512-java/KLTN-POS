package com.pos.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {
    private Long id;
    private String username;
    private String fullName;
    private String role;
    /** Chi nhánh của phiên đăng nhập (đã chọn lúc đăng nhập) */
    private Long branchId;
    private String branchName;
}
