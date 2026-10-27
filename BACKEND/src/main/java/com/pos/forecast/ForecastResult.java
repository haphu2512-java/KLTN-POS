package com.pos.forecast;

import com.pos.branch.Branch;
import com.pos.product.Product;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Lưu kết quả dự báo nhu cầu tồn kho, được tính bởi nightly scheduled job.
 * API đọc từ bảng này thay vì tính toán lại mỗi request → giảm tải DB.
 */
@Entity
@Table(name = "forecast_result", uniqueConstraints = {
        @UniqueConstraint(name = "uk_forecast_product_branch", columnNames = {"product_id", "branch_id"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForecastResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(name = "daily_demand_forecast", nullable = false)
    private Double dailyDemandForecast;

    @Column(name = "total_predicted_demand", nullable = false)
    private Integer totalPredictedDemand;

    @Column(name = "suggested_restock_qty", nullable = false)
    private Integer suggestedRestockQty;

    @Column(name = "urgency", nullable = false, length = 10)
    private String urgency;

    @Column(name = "current_stock", nullable = false)
    private Integer currentStock;

    @Column(name = "has_history", nullable = false)
    private Boolean hasHistory;

    @Column(name = "algorithm", length = 20)
    private String algorithm;

    @Column(name = "forecast_days")
    private Integer forecastDays;

    // Các cột sau cho phép null để bảng cũ tự nâng cấp bằng ddl-auto=update
    @Column(name = "moving_average7")
    private Double movingAverage7;

    @Column(name = "std_dev")
    private Double stdDev;

    @Column(name = "safety_stock")
    private Double safetyStock;

    @Column(name = "reorder_point")
    private Double reorderPoint;

    @Column(name = "intermittent")
    private Boolean intermittent;

    @Column(name = "mae")
    private Double mae;

    @Column(name = "rmse")
    private Double rmse;

    @Column(name = "quality", length = 20)
    private String quality;

    @Column(name = "history_days")
    private Integer historyDays;

    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;
}
