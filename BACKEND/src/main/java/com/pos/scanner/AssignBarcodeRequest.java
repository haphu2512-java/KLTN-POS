package com.pos.scanner;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Mã vạch thật đọc từ bao bì, dùng để thay mã cũ của sản phẩm */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssignBarcodeRequest {

    @NotBlank(message = "Mã vạch barcode không được để trống")
    @Size(max = 50, message = "Mã vạch tối đa 50 ký tự")
    @Pattern(regexp = "\\s*[0-9A-Za-z.\\-]+\\s*", message = "Mã vạch chỉ gồm chữ, số, dấu chấm hoặc gạch ngang")
    private String barcode;
}
