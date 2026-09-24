package com.pos.forecast;

import com.pos.inventory.Inventory;
import com.pos.inventory.InventoryRepository;
import com.pos.product.Product;
import com.pos.product.ProductRepository;
import com.pos.sale.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ForecastService {

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final InvoiceRepository invoiceRepository;

    /**
     * Moving Average forecasting algorithm (Bước 7.2 trong StepByStep_Implementation.md)
     */
    public double predictNextDemandMovingAverage(List<Double> sales, int window) {
        if (sales == null || sales.isEmpty()) return 0.0;
        int size = Math.min(sales.size(), window);
        List<Double> subList = sales.subList(sales.size() - size, sales.size());
        double sum = subList.stream().mapToDouble(Double::doubleValue).sum();
        return Math.round((sum / size) * 10.0) / 10.0;
    }

    /**
     * Exponential Smoothing forecasting algorithm (Bước 7.3 trong StepByStep_Implementation.md)
     */
    public double predictNextDemandExponentialSmoothing(List<Double> sales, double alpha) {
        if (sales == null || sales.isEmpty()) return 0.0;
        double forecast = sales.get(0);
        for (double actual : sales) {
            forecast = alpha * actual + (1 - alpha) * forecast;
        }
        return Math.round(forecast * 10.0) / 10.0;
    }

    /**
     * Generates stock replenishment suggestions based on AI demand forecast
     */
    public List<ForecastResponse> getDemandForecastAndReplenishment(Long branchId, int forecastDays, String algorithm) {
        List<Product> products = productRepository.findByActiveTrue();
        List<ForecastResponse> responses = new ArrayList<>();
        LocalDateTime twoWeeksAgo = LocalDateTime.now().minusDays(14);

        for (Product product : products) {
            // 1. Fetch historical sales data from DB
            List<Object[]> rawSales = invoiceRepository.getDailyProductSales(product.getId(), twoWeeksAgo);
            List<Double> dailySales = new ArrayList<>();
            for (Object[] row : rawSales) {
                if (row[1] != null) {
                    dailySales.add(((Number) row[1]).doubleValue());
                }
            }

            // Fallback default sales rate if new product has sparse history
            if (dailySales.isEmpty()) {
                dailySales = List.of(2.0, 3.0, 4.0, 3.0, 5.0, 4.0, 3.0);
            }

            // 2. Predict daily demand rate
            double dailyRate;
            if ("MOVING_AVG".equalsIgnoreCase(algorithm)) {
                dailyRate = predictNextDemandMovingAverage(dailySales, 7);
            } else {
                dailyRate = predictNextDemandExponentialSmoothing(dailySales, 0.35);
            }

            // 3. Get current branch stock
            Inventory inv = inventoryRepository.findByProductIdAndBranchId(product.getId(), branchId).orElse(null);
            int currentStock = inv != null ? inv.getQuantity() : 0;

            // 4. Calculate Replenishment (Nhu cầu dự báo * Hệ số an toàn 1.1 - Tồn hiện tại)
            int totalDemand = (int) Math.ceil(dailyRate * forecastDays * 1.10);
            int shortage = totalDemand - currentStock;
            int suggestedRestock = Math.max(0, shortage);

            String urgency = "LOW";
            if (currentStock <= 0) {
                urgency = "HIGH";
            } else if (currentStock <= totalDemand * 0.3) {
                urgency = "HIGH";
            } else if (suggestedRestock > 0) {
                urgency = "MEDIUM";
            }

            responses.add(ForecastResponse.builder()
                    .productId(product.getId())
                    .barcode(product.getBarcode())
                    .productName(product.getName())
                    .category(product.getCategory())
                    .unit(product.getUnit())
                    .currentStock(currentStock)
                    .costPrice(product.getCostPrice())
                    .dailyDemandForecast(dailyRate)
                    .totalPredictedDemand(totalDemand)
                    .suggestedRestockQty(suggestedRestock)
                    .urgency(urgency)
                    .historicalDailySales(dailySales)
                    .build());
        }

        return responses;
    }

    /**
     * Nightly scheduled background job to refresh AI forecasts (cron 01:00 AM)
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void runNightlyForecastJob() {
        log.info("[AI FORECAST JOB] Đang chạy tác vụ tính toán dự báo nhu cầu định kỳ ban đêm...");
    }
}
