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
    private String urgency; // HIGH (Khẩn cấp), MEDIUM (Cao), LOW (Bình thường)
    private String urgencyLabel;
    private Boolean hasHistory;
    private List<Double> historicalDailySales;

    // Giải trình theo AI_for_POS.md mục 3 và 6
    private String algorithm;
    private Integer forecastDays;
    private Integer leadTimeDays;
    private Double movingAverage7;
    private Double stdDev;
    private Double safetyStock;
    private Double reorderPoint;
    private Boolean intermittent;
    private Double mae;
    private Double rmse;
    private String quality; // GOOD, FAIR, LOW, INSUFFICIENT_DATA
    private String reason;
}

