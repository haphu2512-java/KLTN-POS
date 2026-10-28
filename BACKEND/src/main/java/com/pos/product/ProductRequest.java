package com.pos.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductRequest {

    /** Bỏ trống khi tạo mới thì hệ thống sinh mã nội bộ theo BarcodeRules */
    @Size(max = 50, message = "Mã vạch tối đa 50 ký tự")
    private String barcode;

    @NotBlank(message = "Tên sản phẩm không được để trống")
    private String name;

    private String category;

    @PositiveOrZero(message = "Giá vốn phải lớn hơn hoặc bằng 0")
    private BigDecimal costPrice;

    @NotNull(message = "Giá bán không được để trống")
    @PositiveOrZero(message = "Giá bán phải lớn hơn hoặc bằng 0")
    private BigDecimal salePrice;

    private String unit;
    private String imageUrl;
}
