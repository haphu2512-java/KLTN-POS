package com.pos.forecast;

import com.pos.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/forecast")
@RequiredArgsConstructor
@Tag(name = "AI Forecast", description = "Dự báo nhu cầu bán hàng và gợi ý số lượng nhập kho bằng AI")
public class ForecastController {

    private final ForecastService forecastService;

    @GetMapping({"/demand", "/replenishment"})
    @Operation(summary = "Tính toán dự báo nhu cầu bán hàng và đề xuất nhập kho cho chi nhánh")
    public ResponseEntity<ApiResponse<List<ForecastResponse>>> getDemandForecast(
            @RequestParam(defaultValue = "1") Long branchId,
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(defaultValue = "EXP_SMOOTHING") String algorithm) {
        List<ForecastResponse> results = forecastService.getDemandForecastAndReplenishment(branchId, days, algorithm);
        return ResponseEntity.ok(ApiResponse.ok(results));
    }
}
