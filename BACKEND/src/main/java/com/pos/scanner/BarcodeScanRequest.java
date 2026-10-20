package com.pos.scanner;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BarcodeScanRequest {

    @NotBlank(message = "Mã vạch barcode không được để trống")
    @Size(max = 50, message = "Mã vạch tối đa 50 ký tự")
    private String barcode;

    @Size(max = 100)
    private String deviceName;

    /** Mã duy nhất do điện thoại sinh ra, dùng để server bỏ qua lần gửi trùng */
    @Size(max = 64)
    private String clientScanId;

    /** Thời điểm quét trên điện thoại (ISO-8601), dùng để sắp xếp khi đồng bộ */
    @Size(max = 40)
    private String timestamp;

    /** Chỉ ADD_TO_CART được bắn vào POS; tra giá dùng GET /api/scanner/lookup */
    @Builder.Default
    @Pattern(regexp = "ADD_TO_CART", message = "Chỉ hỗ trợ action ADD_TO_CART")
    private String action = "ADD_TO_CART";
}
