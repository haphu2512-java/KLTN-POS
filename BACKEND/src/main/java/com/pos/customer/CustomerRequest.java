package com.pos.customer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Dữ liệu tạo/sửa khách hàng. Không có trường điểm: điểm chỉ đổi qua bán hàng,
 * hoàn trả hoặc quản lý điều chỉnh (có ghi sổ điểm).
 */
public record CustomerRequest(
        @NotBlank(message = "Tên khách hàng không được để trống")
        @Size(max = 100, message = "Tên khách hàng tối đa 100 ký tự")
        String name,
        @Size(max = 20, message = "Số điện thoại tối đa 20 ký tự")
        String phone,
        @Size(max = 255, message = "Địa chỉ tối đa 255 ký tự")
        String address) {
}
