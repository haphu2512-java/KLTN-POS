package com.pos.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductRequest {

    @NotBlank(message = "Mã vạch (Barcode) không được để trống")
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
}
