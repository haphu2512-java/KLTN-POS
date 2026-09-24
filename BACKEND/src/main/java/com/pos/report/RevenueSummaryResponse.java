package com.pos.report;

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
public class RevenueSummaryResponse {
    private BigDecimal totalRevenue;
    private BigDecimal totalCost;
    private BigDecimal grossProfit;
    private double profitMarginPercent;
    private int totalOrders;
    private BigDecimal averageOrderValue;
    private List<DailyRevenueItem> dailyTrend;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyRevenueItem {
        private String date;
        private BigDecimal revenue;
        private BigDecimal cost;
        private BigDecimal profit;
        private int orderCount;
    }
}
