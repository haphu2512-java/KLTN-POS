package com.pos.forecast;

import java.util.List;
import java.util.Locale;

/**
 * Các công thức dự báo và tồn kho của AI_for_POS.md (mục 3, 4, 6), không phụ thuộc Spring hay CSDL.
 * Bản Python trong AI_FORECAST/ cài đặt đúng các công thức này; hai bên được đối chiếu bằng
 * AI_FORECAST/tests/fixtures/parity_cases.json.
 */
public class ForecastCalculator {

    public static final String EXP_SMOOTH = "EXP_SMOOTH";
    public static final String MOVING_AVG = "MOVING_AVG";

    public static final String URGENCY_HIGH = "HIGH";
    public static final String URGENCY_MEDIUM = "MEDIUM";
    public static final String URGENCY_LOW = "LOW";

    public static final String QUALITY_GOOD = "GOOD";
    public static final String QUALITY_FAIR = "FAIR";
    public static final String QUALITY_LOW = "LOW";
    public static final String QUALITY_INSUFFICIENT = "INSUFFICIENT_DATA";

    /**
     * Tham số mô hình.
     *
     * @param alpha                hệ số làm mịn α của Exponential Smoothing (mục 3.1)
     * @param maWindow             cửa sổ trung bình trượt (mục 3.2)
     * @param z                    hệ số mức phục vụ, 1.65 ứng với 95% (mục 3.3)
     * @param leadTimeDays         thời gian giao hàng L (mục 3.3, 3.4)
     * @param validationDays       số ngày cuối giữ lại để tính MAE/RMSE (mục 6)
     * @param intermittentZeroRatio tỉ lệ ngày không bán vượt ngưỡng này thì coi là bán ngắt quãng (mục 4.2)
     */
    public record Config(double alpha, int maWindow, double z, int leadTimeDays, int validationDays,
                         double intermittentZeroRatio) {
        public static Config defaults() {
            return new Config(0.35, 7, 1.65, 2, 7, 0.5);
        }
    }

    /** Phần kết quả chỉ phụ thuộc lịch sử bán, được lưu cache bởi job ban đêm. */
    public record Model(boolean hasHistory, int historyDays, double dailyDemand, double movingAverage,
                        double stdDev, double safetyStock, double reorderPoint, boolean intermittent,
                        Double mae, Double rmse, String quality) {
    }

    /** Phần kết quả phụ thuộc tồn kho hiện tại và số ngày dự báo, luôn tính lại theo thời gian thực. */
    public record Plan(int totalPredictedDemand, int suggestedRestockQty, String urgency, String urgencyLabel,
                       String reason) {
    }

    private final Config config;

    public ForecastCalculator(Config config) {
        this.config = config;
    }

    public Config getConfig() {
        return config;
    }

    /** "MOVING_AVG" hoặc mặc định "EXP_SMOOTH" (chấp nhận "EXP_SMOOTHING" từ client cũ) */
    public static String normalizeAlgorithm(String algorithm) {
        return MOVING_AVG.equalsIgnoreCase(algorithm == null ? null : algorithm.trim()) ? MOVING_AVG : EXP_SMOOTH;
    }

    // ---------------------------------------------------------------- mô hình (mục 3.1, 3.2)

    /** ȳ = (1/N) Σ y_{t-i} trên N ngày gần nhất. */
    public static double movingAverage(List<Double> sales, int window) {
        if (sales == null || sales.isEmpty()) return 0.0;
        int size = window > 0 ? Math.min(sales.size(), window) : sales.size();
        double sum = 0;
        for (int i = sales.size() - size; i < sales.size(); i++) sum += sales.get(i);
        return sum / size;
    }

    /**
     * ŷ_{t+1} = α·y_t + (1 − α)·ŷ_t. Khởi tạo ŷ_1 bằng trung bình initWindow ngày đầu để một ngày
     * bất thường đầu chuỗi không kéo lệch kết quả.
     */
    public static double exponentialSmoothing(List<Double> sales, double alpha, int initWindow) {
        if (sales == null || sales.isEmpty()) return 0.0;
        if (alpha <= 0 || alpha > 1) throw new IllegalArgumentException("alpha phải nằm trong (0, 1]");
        double forecast = movingAverage(sales.subList(0, Math.min(initWindow, sales.size())), initWindow);
        for (double actual : sales) {
            forecast = alpha * actual + (1 - alpha) * forecast;
        }
        return forecast;
    }

    /** Độ lệch chuẩn mẫu σ_d. Chuỗi đã điền 0 cho ngày không bán nên các ngày đó vẫn được tính. */
    public static double stdDev(List<Double> sales) {
        int n = sales == null ? 0 : sales.size();
        if (n < 2) return 0.0;
        double mean = sales.stream().mapToDouble(Double::doubleValue).sum() / n;
        double sq = 0;
        for (double x : sales) sq += (x - mean) * (x - mean);
        return Math.sqrt(sq / (n - 1));
    }

    static double zeroRatio(List<Double> sales) {
        if (sales.isEmpty()) return 0.0;
        return (double) sales.stream().filter(x -> x == 0).count() / sales.size();
    }

    // ---------------------------------------------------------------- tồn kho (mục 3.3 – 3.5)

    /** SS = Z × σ_d × √L */
    public static double safetyStock(double stdDev, double leadTimeDays, double z) {
        return z * stdDev * Math.sqrt(leadTimeDays);
    }

    /** ROP = d̄ × L + SS */
    public static double reorderPoint(double dailyDemand, double leadTimeDays, double safetyStock) {
        return dailyDemand * leadTimeDays + safetyStock;
    }

    /** Q_order = max(0, ⌈ROP + d̄ × T − Q_current⌉) */
    public static int orderQuantity(double reorderPoint, double dailyDemand, int reviewDays, double currentStock) {
        // Làm tròn 6 chữ số trước khi lấy trần để sai số dấu phẩy động không cộng thêm 1
        double raw = round(reorderPoint + dailyDemand * reviewDays - currentStock, 6);
        return (int) Math.max(0, Math.ceil(raw));
    }

    /**
     * Khẩn cấp (HIGH): hết hàng hoặc tồn kho ≤ d̄ × L (hết trước khi hàng về).
     * Cao (MEDIUM): tồn kho ≤ ROP. Bình thường (LOW): còn lại.
     */
    public static String classifyUrgency(boolean hasHistory, double currentStock, double dailyDemand,
                                         double leadTimeDays, double reorderPoint) {
        if (currentStock <= 0) return URGENCY_HIGH;
        if (!hasHistory) return URGENCY_LOW;
        if (currentStock <= dailyDemand * leadTimeDays) return URGENCY_HIGH;
        if (currentStock <= reorderPoint) return URGENCY_MEDIUM;
        return URGENCY_LOW;
    }

    public static String urgencyLabel(String urgency) {
        return switch (urgency) {
            case URGENCY_HIGH -> "Khẩn cấp";
            case URGENCY_MEDIUM -> "Cao";
            default -> "Bình thường";
        };
    }

    // ---------------------------------------------------------------- đánh giá (mục 6)

    public static double mae(List<Double> actual, double predicted) {
        return actual.stream().mapToDouble(a -> Math.abs(a - predicted)).average().orElse(0);
    }

    public static double rmse(List<Double> actual, double predicted) {
        return Math.sqrt(actual.stream().mapToDouble(a -> (a - predicted) * (a - predicted)).average().orElse(0));
    }

    /** Dự báo của mô hình đã chọn cho ngày kế tiếp. */
    public double predict(List<Double> sales, String algorithm) {
        return MOVING_AVG.equals(normalizeAlgorithm(algorithm))
                ? movingAverage(sales, config.maWindow())
                : exponentialSmoothing(sales, config.alpha(), config.maWindow());
    }

    // ---------------------------------------------------------------- ghép kết quả

    /** Tính mô hình từ chuỗi bán theo ngày (ngày cũ nhất trước, đã điền 0 cho ngày không bán). */
    public Model model(List<Double> sales, String algorithm) {
        boolean hasHistory = sales.stream().anyMatch(x -> x > 0);
        if (!hasHistory) {
            return new Model(false, sales.size(), 0, 0, 0, 0, 0, false, null, null, QUALITY_INSUFFICIENT);
        }
        double daily = predict(sales, algorithm);
        double sigma = stdDev(sales);
        double ss = safetyStock(sigma, config.leadTimeDays(), config.z());
        double rop = reorderPoint(daily, config.leadTimeDays(), ss);
        boolean intermittent = zeroRatio(sales) > config.intermittentZeroRatio();

        // Backtest: học trên phần đầu, dự báo validationDays ngày cuối mà mô hình chưa thấy
        Double mae = null, rmse = null;
        String quality = QUALITY_INSUFFICIENT;
        int v = config.validationDays();
        if (v > 0 && sales.size() >= 2 * v) {
            List<Double> train = sales.subList(0, sales.size() - v);
            List<Double> test = sales.subList(sales.size() - v, sales.size());
            double forecast = predict(train, algorithm);
            mae = round(mae(test, forecast), 3);
            rmse = round(rmse(test, forecast), 3);
            quality = quality(mae, round(movingAverage(test, v), 3));
        }
        return new Model(true, sales.size(), daily, movingAverage(sales, config.maWindow()), sigma, ss, rop,
                intermittent, mae, rmse, quality);
    }

    /** Sai số tương đối MAE / trung bình bán thực tế ở tập kiểm định: ≤ 30% GOOD, ≤ 60% FAIR. */
    static String quality(double mae, double validationMean) {
        double relativeError = validationMean > 0 ? mae / validationMean : (mae == 0 ? 0 : Double.POSITIVE_INFINITY);
        if (relativeError <= 0.3) return QUALITY_GOOD;
        if (relativeError <= 0.6) return QUALITY_FAIR;
        return QUALITY_LOW;
    }

    /** Số lượng đề xuất, mức khẩn cấp và lý do theo tồn kho hiện tại. */
    public Plan plan(Model model, int currentStock, int forecastDays) {
        int lead = config.leadTimeDays();
        if (!model.hasHistory()) {
            String urgency = classifyUrgency(false, currentStock, 0, lead, 0);
            return new Plan(0, 0, urgency, urgencyLabel(urgency),
                    "Chưa có lịch sử bán trong " + model.historyDays() + " ngày gần nhất nên chưa đủ thông tin "
                            + "để dự báo. Không đề xuất nhập hàng.");
        }
        int qty = orderQuantity(model.reorderPoint(), model.dailyDemand(), forecastDays, currentStock);
        String urgency = classifyUrgency(true, currentStock, model.dailyDemand(), lead, model.reorderPoint());
        String reason = String.format(Locale.ROOT,
                "Tồn kho %d, nhu cầu dự báo %.1f/ngày, lead time %d ngày, tồn kho an toàn %.1f, "
                        + "điểm đặt hàng lại %.1f. ",
                currentStock, model.dailyDemand(), lead, model.safetyStock(), model.reorderPoint());
        reason += qty > 0
                ? "Đề xuất nhập " + qty + " để đủ bán " + forecastDays + " ngày tới mà vẫn giữ trên điểm đặt hàng lại."
                : "Tồn kho đủ cho " + forecastDays + " ngày tới, chưa cần nhập.";
        if (model.intermittent()) {
            reason += " Sản phẩm bán ngắt quãng nên dự báo kém chắc chắn hơn.";
        }
        int total = (int) Math.floor(model.dailyDemand() * forecastDays + 0.5);
        return new Plan(total, qty, urgency, urgencyLabel(urgency), reason);
    }

    static double round(double value, int digits) {
        double scale = Math.pow(10, digits);
        return Math.round(value * scale) / scale;
    }
}
