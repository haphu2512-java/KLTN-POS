package com.pos.scanner;

import jakarta.validation.constraints.NotBlank;
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
    private String barcode;

    private Long branchId;

    private String deviceName;

    private String clientScanId;

    /**
     * Action type: ADD_TO_CART (default for POS scanning), LOOKUP_ONLY, STOCK_CHECK
     */
    @Builder.Default
    private String action = "ADD_TO_CART";
}
