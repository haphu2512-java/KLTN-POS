package com.pos.report;

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
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Tag(name = "Reports & Analytics", description = "Báo cáo doanh thu, lợi nhuận và phân tích sản phẩm bán chạy")
public class ReportController {

    private final ReportService reportService;
    private final CurrentUserService currentUserService;

    @GetMapping("/revenue")
    @Operation(summary = "Báo cáo tổng kết doanh thu và lợi nhuận theo chi nhánh & khoảng ngày")
    public ResponseEntity<ApiResponse<RevenueSummaryResponse>> getRevenueReport(
            @RequestParam(required = false) Long branchId,
            @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(ApiResponse.ok(reportService.getRevenueSummary(currentUserService.branchForRead(branchId, true), days)));
    }

    @GetMapping("/top-products")
    @Operation(summary = "Báo cáo Top sản phẩm bán chạy nhất")
    public ResponseEntity<ApiResponse<List<TopProductResponse>>> getTopProducts(
            @RequestParam(required = false) Long branchId,
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(ApiResponse.ok(reportService.getTopSellingProducts(currentUserService.branchForRead(branchId, true), days, limit)));
    }
}
