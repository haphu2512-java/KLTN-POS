package com.pos.staff;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class StaffRequest {

    /** Chi nhánh của nhân viên; MANAGER luôn dùng chi nhánh đang đăng nhập, chỉ ADMIN chọn được */
    private Long branchId;

    @NotBlank(message = "Nhập họ tên nhân viên")
    @Size(max = 100, message = "Họ tên tối đa 100 ký tự")
    private String fullName;

    @Pattern(regexp = "^$|^[0-9+ ]{8,20}$", message = "Số điện thoại không hợp lệ")
    private String phone;

    @Size(max = 50, message = "Vị trí tối đa 50 ký tự")
    private String position;

    private Boolean active;
}
