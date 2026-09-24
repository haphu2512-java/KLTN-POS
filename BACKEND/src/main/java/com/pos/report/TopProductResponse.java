package com.pos.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopProductResponse {
    private Long productId;
    private String barcode;
    private String productName;
    private int quantitySold;
    private BigDecimal totalRevenue;
    private BigDecimal estimatedProfit;
}
