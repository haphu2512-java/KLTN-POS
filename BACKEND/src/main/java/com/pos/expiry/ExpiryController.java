package com.pos.expiry;

import com.pos.common.ApiResponse;
import com.pos.stockcount.StockCount;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/expiry")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE')")
@Tag(name = "Expiry", description = "Hạn sử dụng theo lô: hàng cận date, hết hạn, lập phiếu hủy")
public class ExpiryController {

    private final ExpiryService service;

    @GetMapping
    @Operation(summary = "Lô còn hàng đã hết hạn hoặc sắp hết hạn trong days ngày (mặc định 30)")
    public ApiResponse<ExpiryService.ExpiryReport> report(@RequestParam(required = false) Long branchId,
                                                         @RequestParam(required = false) Integer days) {
        return ApiResponse.ok(service.report(branchId, days));
    }

    @PostMapping("/disposal-count")
    @Operation(summary = "Lập phiếu kiểm kê nháp hủy hàng hết hạn (lý do Hết hạn), chờ duyệt")
    public ApiResponse<StockCount> disposalCount() {
        return ApiResponse.ok("Đã lập phiếu kiểm kê hủy hàng hết hạn, kiểm lại rồi gửi duyệt", service.createDisposalCount());
    }
}
