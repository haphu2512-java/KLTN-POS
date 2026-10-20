package com.pos.forecast;

import com.pos.branch.Branch;
import com.pos.branch.BranchRepository;
import com.pos.inventory.Inventory;
import com.pos.inventory.InventoryRepository;
import com.pos.product.Product;
import com.pos.product.ProductRepository;
import com.pos.sale.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ForecastService {

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final InvoiceRepository invoiceRepository;
    private final BranchRepository branchRepository;
    private final ForecastResultRepository forecastResultRepository;

    @Value("${ai.forecast.algorithm}")
    private String configuredAlgorithm = "EXP_SMOOTH";

    @Value("${ai.forecast.default-days}")
    private int configuredForecastDays = 7;

    @Value("${ai.forecast.safety-factor}")
    private double safetyFactor = 1.10;

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

    /** Số ngày lịch sử bán hàng dùng để dự báo (tính cả hôm nay) */
    static final int HISTORY_DAYS = 14;

    /**
     * Generates stock replenishment suggestions based on AI demand forecast.
     * Chỉ dùng doanh số của đúng chi nhánh, bỏ hóa đơn đã hoàn trả và điền 0 cho những ngày không bán được.
     */
    public List<ForecastResponse> getDemandForecastAndReplenishment(Long branchId, int forecastDays, String algorithm) {
        List<Product> products = productRepository.findByActiveTrue();
        List<ForecastResponse> responses = new ArrayList<>();

        LocalDate lastDay = LocalDate.now();
        LocalDate firstDay = lastDay.minusDays(HISTORY_DAYS - 1);
        Map<Long, double[]> salesByProduct = loadDailySales(branchId, firstDay);
        Map<Long, Integer> stockByProduct = loadBranchStock(branchId);
        boolean movingAverage = "MOVING_AVG".equals(normalizeAlgorithm(algorithm));

        for (Product product : products) {
            // 1. Chuỗi bán hàng theo ngày của sản phẩm tại chi nhánh (đã điền 0 cho ngày không bán)
            double[] series = salesByProduct.get(product.getId());
            boolean hasHistory = series != null;
            List<Double> dailySales = new ArrayList<>();
            if (hasHistory) {
                for (double qty : series) {
                    dailySales.add(qty);
                }
            }

            // 2. Predict daily demand rate
            double dailyRate = 0.0;
            int totalDemand = 0;

            // 3. Get current branch stock
            int currentStock = stockByProduct.getOrDefault(product.getId(), 0);

            if (hasHistory) {
                dailyRate = movingAverage
                        ? predictNextDemandMovingAverage(dailySales, 7)
                        : predictNextDemandExponentialSmoothing(dailySales, 0.35);

                // 4. Nhu cầu dự báo * Hệ số an toàn
                double factor = safetyFactor > 0 ? safetyFactor : 1.10;
                totalDemand = (int) Math.ceil(dailyRate * forecastDays * factor);
            }
            // Sản phẩm chưa có lịch sử bán hàng: không gán dữ liệu giả, gợi ý restock = 0

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
                    .suggestedRestockQty(suggestRestock(hasHistory, totalDemand, currentStock))
                    .urgency(classifyUrgency(hasHistory, totalDemand, currentStock))
                    .hasHistory(hasHistory)
                    .historicalDailySales(dailySales)
                    .build());
        }

        return responses;
    }

    /**
     * Đọc kết quả dự báo đã được cache từ nightly job.
     * Cache chỉ dùng khi cùng thuật toán và số ngày dự báo; tồn kho được lấy lại theo thời gian thực
     * để gợi ý nhập hàng phản ánh các giao dịch trong ngày.
     * Nếu chưa có cache phù hợp, fallback tính toán realtime.
     */
    public List<ForecastResponse> getCachedOrComputeForecast(Long branchId, int forecastDays, String algorithm) {
        String normalizedAlgorithm = normalizeAlgorithm(algorithm);
        List<ForecastResult> cached = forecastResultRepository
                .findByBranchIdOrderByUrgencyAscProductNameAsc(branchId);

        boolean cacheMatches = !cached.isEmpty() && cached.stream().allMatch(fr ->
                normalizedAlgorithm.equals(normalizeAlgorithm(fr.getAlgorithm()))
                        && Integer.valueOf(forecastDays).equals(fr.getForecastDays()));

        if (cacheMatches) {
            Map<Long, Integer> stockByProduct = loadBranchStock(branchId);
            List<ForecastResponse> responses = new ArrayList<>();
            for (ForecastResult fr : cached) {
                Product product = fr.getProduct();
                boolean hasHistory = Boolean.TRUE.equals(fr.getHasHistory());
                int totalDemand = fr.getTotalPredictedDemand() != null ? fr.getTotalPredictedDemand() : 0;
                int currentStock = stockByProduct.getOrDefault(product.getId(), 0);
                responses.add(ForecastResponse.builder()
                        .productId(product.getId())
                        .barcode(product.getBarcode())
                        .productName(product.getName())
                        .category(product.getCategory())
                        .unit(product.getUnit())
                        .currentStock(currentStock)
                        .costPrice(product.getCostPrice())
                        .dailyDemandForecast(fr.getDailyDemandForecast())
                        .totalPredictedDemand(totalDemand)
                        .suggestedRestockQty(suggestRestock(hasHistory, totalDemand, currentStock))
                        .urgency(classifyUrgency(hasHistory, totalDemand, currentStock))
                        .hasHistory(hasHistory)
                        .historicalDailySales(null) // Không lưu raw sales trong cache
                        .build());
            }
            return responses;
        }

        // Fallback: chưa có cache phù hợp → tính toán realtime
        log.info("[FORECAST] Chưa có cache phù hợp cho branchId={} (algorithm={}, days={}), tính toán realtime...",
                branchId, normalizedAlgorithm, forecastDays);
        return getDemandForecastAndReplenishment(branchId, forecastDays, normalizedAlgorithm);
    }

    /** "MOVING_AVG" hoặc mặc định "EXP_SMOOTH" (chấp nhận cả "EXP_SMOOTHING" từ client cũ) */
    static String normalizeAlgorithm(String algorithm) {
        return "MOVING_AVG".equalsIgnoreCase(algorithm) ? "MOVING_AVG" : "EXP_SMOOTH";
    }

    static int suggestRestock(boolean hasHistory, int totalDemand, int currentStock) {
        return hasHistory ? Math.max(0, totalDemand - currentStock) : 0;
    }

    static String classifyUrgency(boolean hasHistory, int totalDemand, int currentStock) {
        if (currentStock <= 0) {
            return "HIGH";
        }
        if (!hasHistory) {
            return "LOW";
        }
        if (currentStock <= totalDemand * 0.3) {
            return "HIGH";
        }
        return totalDemand - currentStock > 0 ? "MEDIUM" : "LOW";
    }

    /**
     * Doanh số theo ngày của mọi sản phẩm tại chi nhánh, mỗi sản phẩm là mảng HISTORY_DAYS phần tử
     * (ngày không bán = 0). Sản phẩm không bán được ngày nào trong cửa sổ sẽ không có trong map.
     */
    private Map<Long, double[]> loadDailySales(Long branchId, LocalDate firstDay) {
        Map<Long, double[]> salesByProduct = new HashMap<>();
        List<Object[]> rows = invoiceRepository.getBranchDailyProductSales(branchId, firstDay.atStartOfDay());
        for (Object[] row : rows) {
            if (row[0] == null || row[1] == null || row[2] == null) continue;
            int dayIndex = (int) ChronoUnit.DAYS.between(firstDay, toLocalDate(row[1]));
            if (dayIndex < 0 || dayIndex >= HISTORY_DAYS) continue;
            long productId = ((Number) row[0]).longValue();
            salesByProduct.computeIfAbsent(productId, k -> new double[HISTORY_DAYS])[dayIndex]
                    += ((Number) row[2]).doubleValue();
        }
        return salesByProduct;
    }

    private Map<Long, Integer> loadBranchStock(Long branchId) {
        Map<Long, Integer> stockByProduct = new HashMap<>();
        for (Inventory inv : inventoryRepository.findByBranchId(branchId)) {
            if (inv.getProduct() != null && inv.getQuantity() != null) {
                stockByProduct.put(inv.getProduct().getId(), inv.getQuantity());
            }
        }
        return stockByProduct;
    }

    private static LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate date) return date;
        if (value instanceof java.sql.Date date) return date.toLocalDate();
        if (value instanceof LocalDateTime dateTime) return dateTime.toLocalDate();
        if (value instanceof java.util.Date date) return new java.sql.Date(date.getTime()).toLocalDate();
        return LocalDate.parse(value.toString().substring(0, 10));
    }

    /**
     * Lưu kết quả dự báo vào DB cho 1 chi nhánh (gọi bởi nightly job).
     */
    @Transactional
    public void persistForecastResults(Long branchId, List<ForecastResponse> results, String algorithm, int forecastDays) {
        // Xóa kết quả cũ
        forecastResultRepository.deleteByBranchId(branchId);

        Branch branch = branchRepository.findById(branchId).orElse(null);
        if (branch == null) return;

        LocalDateTime now = LocalDateTime.now();
        List<ForecastResult> entities = new ArrayList<>();

        for (ForecastResponse r : results) {
            Product product = productRepository.findById(r.getProductId()).orElse(null);
            if (product == null) continue;

            entities.add(ForecastResult.builder()
                    .product(product)
                    .branch(branch)
                    .dailyDemandForecast(r.getDailyDemandForecast())
                    .totalPredictedDemand(r.getTotalPredictedDemand())
                    .suggestedRestockQty(r.getSuggestedRestockQty())
                    .urgency(r.getUrgency())
                    .currentStock(r.getCurrentStock())
                    .hasHistory(r.getHasHistory())
                    .algorithm(algorithm)
                    .forecastDays(forecastDays)
                    .calculatedAt(now)
                    .build());
        }

        forecastResultRepository.saveAll(entities);
    }

    /**
     * Nightly scheduled background job to refresh AI forecasts (cron 01:00 AM).
     * Kết quả được persist vào bảng forecast_result để API đọc nhanh.
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void runNightlyForecastJob() {
        log.info("[AI FORECAST JOB] Đang chạy tác vụ tính toán dự báo nhu cầu định kỳ ban đêm (Thuật toán: {}, Số ngày: {})...",
                configuredAlgorithm, configuredForecastDays);
        try {
            List<Branch> branches = branchRepository.findAll();
            for (Branch branch : branches) {
                List<ForecastResponse> results = getDemandForecastAndReplenishment(
                        branch.getId(), configuredForecastDays, configuredAlgorithm);
                long restockCount = results.stream().filter(r -> r.getSuggestedRestockQty() > 0).count();

                // Persist kết quả vào DB
                persistForecastResults(branch.getId(), results, configuredAlgorithm, configuredForecastDays);

                log.info("[AI FORECAST JOB] Chi nhánh '{}' (ID: {}): phân tích {} sản phẩm, {} sản phẩm cần nhập hàng. Đã lưu cache.",
                        branch.getName(), branch.getId(), results.size(), restockCount);
            }
            log.info("[AI FORECAST JOB] Hoàn thành tác vụ tính toán dự báo định kỳ ban đêm.");
        } catch (Exception e) {
            log.error("[AI FORECAST JOB] Lỗi khi thực hiện dự báo ban đêm: {}", e.getMessage(), e);
        }
    }
}
