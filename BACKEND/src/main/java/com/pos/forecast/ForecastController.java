package com.pos.forecast;

import com.pos.common.ApiResponse;
import com.pos.security.CurrentUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@RestController
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE')")
@RequestMapping("/api/forecast")
@RequiredArgsConstructor
@Tag(name = "AI Forecast", description = "Dự báo nhu cầu bán hàng và gợi ý số lượng nhập kho bằng AI")
public class ForecastController {

    private final ForecastService forecastService;
    private final CurrentUserService currentUserService;

    @GetMapping({"/demand", "/replenishment"})
    @Operation(summary = "Dự báo nhu cầu bán hàng (đọc từ cache nightly job, fallback tính realtime)")
    public ResponseEntity<ApiResponse<List<ForecastResponse>>> getDemandForecast(
            @RequestParam(required = false) Long branchId,
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(defaultValue = "EXP_SMOOTHING") String algorithm) {
        List<ForecastResponse> results = forecastService.getCachedOrComputeForecast(
                currentUserService.branchForRead(branchId, false), days, algorithm);
        return ResponseEntity.ok(ApiResponse.ok(results));
    }

    @GetMapping("/recommendations")
    @Operation(summary = "Danh sách sản phẩm cần nhập hàng ưu tiên (lọc từ kết quả dự báo)")
    public ResponseEntity<ApiResponse<List<ForecastResponse>>> getRecommendations(
            @RequestParam(required = false) Long branchId) {
        List<ForecastResponse> all = forecastService.getCachedOrComputeForecast(
                currentUserService.branchForRead(branchId, false), 7, "EXP_SMOOTH");
        List<ForecastResponse> needRestock = all.stream()
                .filter(r -> r.getSuggestedRestockQty() > 0)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(needRestock));
    }
}
