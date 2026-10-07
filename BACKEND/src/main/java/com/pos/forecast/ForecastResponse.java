package com.pos.forecast;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForecastResponse {

    private Long productId;
    private String barcode;
    private String productName;
    private String category;
    private String unit;
    private Integer currentStock;
    private BigDecimal costPrice;
    private Double dailyDemandForecast;
    private Integer totalPredictedDemand;
    private Integer suggestedRestockQty;
    private String urgency; // HIGH, MEDIUM, LOW
    private Boolean hasHistory;
    private List<Double> historicalDailySales;
}

