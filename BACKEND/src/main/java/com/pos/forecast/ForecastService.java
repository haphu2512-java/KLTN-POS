package com.pos.forecast;

import com.pos.branch.Branch;
import com.pos.branch.BranchRepository;
import com.pos.common.BusinessException;
import com.pos.inventory.Inventory;
import com.pos.inventory.InventoryRepository;
import com.pos.product.Product;
import com.pos.product.ProductRepository;
import com.pos.sale.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
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
    private String configuredAlgorithm = ForecastCalculator.EXP_SMOOTH;

    @Value("${ai.forecast.default-days}")
    private int configuredForecastDays = 7;

    @Value("${ai.forecast.alpha:0.35}")
    private double alpha = 0.35;

    @Value("${ai.forecast.ma-window:7}")
    private int maWindow = 7;

    @Value("${ai.forecast.service-level-z:1.65}")
    private double serviceLevelZ = 1.65;

    @Value("${ai.forecast.lead-time-days:2}")
    private int leadTimeDays = 2;

    @Value("${ai.forecast.history-days:30}")
    private int historyDays = HISTORY_DAYS;

    @Value("${ai.forecast.validation-days:7}")
    private int validationDays = 7;

    /** Số ngày lịch sử bán hàng mặc định dùng để dự báo, tính cả hôm nay (AI_for_POS.md mục 5: days=30) */
    static final int HISTORY_DAYS = 30;

    private static final Map<String, Integer> URGENCY_ORDER = Map.of(
            ForecastCalculator.URGENCY_HIGH, 0,
            ForecastCalculator.URGENCY_MEDIUM, 1,
            ForecastCalculator.URGENCY_LOW, 2);

    private static final Comparator<ForecastResponse> BY_URGENCY = Comparator
            .comparing((ForecastResponse r) -> URGENCY_ORDER.getOrDefault(r.getUrgency(), 3))
            .thenComparing(ForecastResponse::getSuggestedRestockQty, Comparator.reverseOrder())
            .thenComparing(r -> r.getProductName() == null ? "" : r.getProductName());

    ForecastCalculator calculator() {
        return new ForecastCalculator(new ForecastCalculator.Config(
                alpha, maWindow, serviceLevelZ, leadTimeDays, validationDays, 0.5));
    }

    /**
     * Moving Average forecasting algorithm (AI_for_POS.md mục 3)
     */
    public double predictNextDemandMovingAverage(List<Double> sales, int window) {
        return ForecastCalculator.round(ForecastCalculator.movingAverage(sales, window), 1);
    }

    /**
     * Exponential Smoothing forecasting algorithm (AI_for_POS.md mục 3)
     */
    public double predictNextDemandExponentialSmoothing(List<Double> sales, double alpha) {
        return ForecastCalculator.round(ForecastCalculator.exponentialSmoothing(sales, alpha, maWindow), 1);
    }

    /**
     * Dự báo và đề xuất nhập hàng cho mọi sản phẩm đang bán tại chi nhánh (AI_for_POS.md mục 3).
     * Chỉ dùng doanh số của đúng chi nhánh, bỏ hóa đơn đã hoàn trả và điền 0 cho những ngày không bán được.
     */
    public List<ForecastResponse> getDemandForecastAndReplenishment(Long branchId, int forecastDays, String algorithm) {
        String normalizedAlgorithm = ForecastCalculator.normalizeAlgorithm(algorithm);
        ForecastCalculator calc = calculator();
        LocalDate firstDay = historyStart();
        Map<Long, double[]> salesByProduct = loadDailySales(branchId, firstDay);
        Map<Long, Integer> stockByProduct = loadBranchStock(branchId);

        List<ForecastResponse> responses = new ArrayList<>();
        for (Product product : productRepository.findByActiveTrue()) {
            responses.add(forecast(calc, product, salesByProduct.get(product.getId()),
                    stockByProduct.getOrDefault(product.getId(), 0), forecastDays, normalizedAlgorithm));
        }
        responses.sort(BY_URGENCY);
        return responses;
    }

    /** Dự báo cho một sản phẩm tại chi nhánh, kèm chuỗi lịch sử để vẽ biểu đồ thực tế và dự báo. */
    public ForecastResponse getProductForecast(Long branchId, Long productId, int forecastDays, String algorithm) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND",
                        "Không tìm thấy sản phẩm " + productId, HttpStatus.NOT_FOUND));
        LocalDate firstDay = historyStart();
        double[] series = loadDailySales(branchId, firstDay).get(productId);
        int stock = loadBranchStock(branchId).getOrDefault(productId, 0);
        return forecast(calculator(), product, series, stock, forecastDays,
                ForecastCalculator.normalizeAlgorithm(algorithm));
    }

    private ForecastResponse forecast(ForecastCalculator calc, Product product, double[] series, int currentStock,
                                      int forecastDays, String algorithm) {
        // Sản phẩm không bán ngày nào trong cửa sổ: chuỗi toàn 0, mô hình trả hasHistory = false
        List<Double> dailySales = new ArrayList<>(historyDays);
        for (int i = 0; i < historyDays; i++) {
            dailySales.add(series != null ? series[i] : 0.0);
        }
        ForecastCalculator.Model model = calc.model(dailySales, algorithm);
        return toResponse(calc, product, model, currentStock, forecastDays, algorithm,
                model.hasHistory() ? dailySales : List.of());
    }

    private ForecastResponse toResponse(ForecastCalculator calc, Product product, ForecastCalculator.Model model,
                                        int currentStock, int forecastDays, String algorithm,
                                        List<Double> historicalDailySales) {
        ForecastCalculator.Plan plan = calc.plan(model, currentStock, forecastDays);
        return ForecastResponse.builder()
                .productId(product.getId())
                .barcode(product.getBarcode())
                .productName(product.getName())
                .category(product.getCategory())
                .unit(product.getUnit())
                .currentStock(currentStock)
                .costPrice(product.getCostPrice())
                .dailyDemandForecast(ForecastCalculator.round(model.dailyDemand(), 2))
                .totalPredictedDemand(plan.totalPredictedDemand())
                .suggestedRestockQty(plan.suggestedRestockQty())
                .urgency(plan.urgency())
                .urgencyLabel(plan.urgencyLabel())
                .hasHistory(model.hasHistory())
                .historicalDailySales(historicalDailySales)
                .algorithm(algorithm)
                .forecastDays(forecastDays)
                .leadTimeDays(calc.getConfig().leadTimeDays())
                .movingAverage7(ForecastCalculator.round(model.movingAverage(), 2))
                .stdDev(ForecastCalculator.round(model.stdDev(), 2))
                .safetyStock(ForecastCalculator.round(model.safetyStock(), 2))
                .reorderPoint(ForecastCalculator.round(model.reorderPoint(), 2))
                .intermittent(model.intermittent())
                .mae(model.mae())
                .rmse(model.rmse())
                .quality(model.quality())
                .reason(plan.reason())
                .build();
    }

    /**
     * Đọc kết quả dự báo đã được cache từ nightly job.
     * Cache chỉ dùng khi cùng thuật toán, cùng số ngày dự báo và đã có đủ các cột SS/ROP; tồn kho được
     * lấy lại theo thời gian thực để số lượng đề xuất phản ánh các giao dịch trong ngày.
     * Nếu chưa có cache phù hợp, fallback tính toán realtime.
     */
    public List<ForecastResponse> getCachedOrComputeForecast(Long branchId, int forecastDays, String algorithm) {
        String normalizedAlgorithm = ForecastCalculator.normalizeAlgorithm(algorithm);
        List<ForecastResult> cached = forecastResultRepository
                .findByBranchIdOrderByUrgencyAscProductNameAsc(branchId);

        boolean cacheMatches = !cached.isEmpty() && cached.stream().allMatch(fr ->
                normalizedAlgorithm.equals(ForecastCalculator.normalizeAlgorithm(fr.getAlgorithm()))
                        && Integer.valueOf(forecastDays).equals(fr.getForecastDays())
                        && fr.getReorderPoint() != null);

        if (cacheMatches) {
            ForecastCalculator calc = calculator();
            Map<Long, Integer> stockByProduct = loadBranchStock(branchId);
            List<ForecastResponse> responses = new ArrayList<>();
            for (ForecastResult fr : cached) {
                Product product = fr.getProduct();
                ForecastCalculator.Model model = new ForecastCalculator.Model(
                        Boolean.TRUE.equals(fr.getHasHistory()),
                        fr.getHistoryDays() != null ? fr.getHistoryDays() : historyDays,
                        orZero(fr.getDailyDemandForecast()), orZero(fr.getMovingAverage7()), orZero(fr.getStdDev()),
                        orZero(fr.getSafetyStock()), orZero(fr.getReorderPoint()),
                        Boolean.TRUE.equals(fr.getIntermittent()), fr.getMae(), fr.getRmse(),
                        fr.getQuality() != null ? fr.getQuality() : ForecastCalculator.QUALITY_INSUFFICIENT);
                responses.add(toResponse(calc, product, model,
                        stockByProduct.getOrDefault(product.getId(), 0), forecastDays, normalizedAlgorithm,
                        null)); // Không lưu raw sales trong cache
            }
            responses.sort(BY_URGENCY);
            return responses;
        }

        // Fallback: chưa có cache phù hợp → tính toán realtime
        log.info("[FORECAST] Chưa có cache phù hợp cho branchId={} (algorithm={}, days={}), tính toán realtime...",
                branchId, normalizedAlgorithm, forecastDays);
        return getDemandForecastAndReplenishment(branchId, forecastDays, normalizedAlgorithm);
    }

    private static double orZero(Double value) {
        return value != null ? value : 0.0;
    }

    /**
     * Doanh số theo ngày của mọi sản phẩm tại chi nhánh, mỗi sản phẩm là mảng historyDays phần tử
     * (ngày không bán = 0). Sản phẩm không bán được ngày nào trong cửa sổ sẽ không có trong map.
     */
    /**
     * Cửa sổ lịch sử kết thúc ở hôm qua: hôm nay mới bán được một phần, tính như một ngày đủ
     * sẽ kéo mức bán trung bình xuống (giống default_as_of của dịch vụ AI_FORECAST).
     * Hóa đơn hôm nay rơi ra ngoài mảng nhờ kiểm tra dayIndex trong loadDailySales.
     */
    private LocalDate historyStart() {
        return LocalDate.now().minusDays(historyDays);
    }

    private Map<Long, double[]> loadDailySales(Long branchId, LocalDate firstDay) {
        Map<Long, double[]> salesByProduct = new HashMap<>();
        List<Object[]> rows = invoiceRepository.getBranchDailyProductSales(branchId, firstDay.atStartOfDay());
        for (Object[] row : rows) {
            if (row[0] == null || row[1] == null || row[2] == null) continue;
            int dayIndex = (int) ChronoUnit.DAYS.between(firstDay, toLocalDate(row[1]));
            if (dayIndex < 0 || dayIndex >= historyDays) continue;
            long productId = ((Number) row[0]).longValue();
            salesByProduct.computeIfAbsent(productId, k -> new double[historyDays])[dayIndex]
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
                    .movingAverage7(r.getMovingAverage7())
                    .stdDev(r.getStdDev())
                    .safetyStock(r.getSafetyStock())
                    .reorderPoint(r.getReorderPoint())
                    .intermittent(r.getIntermittent())
                    .mae(r.getMae())
                    .rmse(r.getRmse())
                    .quality(r.getQuality())
                    .historyDays(historyDays)
                    .algorithm(algorithm)
                    .forecastDays(forecastDays)
                    .calculatedAt(now)
                    .build());
        }

        forecastResultRepository.saveAll(entities);
    }

    /**
     * Nightly scheduled background job to refresh AI forecasts (cron 02:00 AM, AI_for_POS.md mục 5).
     * Kết quả được persist vào bảng forecast_result để API đọc nhanh.
     */
    @Scheduled(cron = "0 0 2 * * ?")
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
