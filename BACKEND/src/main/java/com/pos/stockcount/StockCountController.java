package com.pos.stockcount;

import com.pos.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stock-counts")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE')")
@Tag(name = "Stock count", description = "Kiểm kê và điều chỉnh tồn kho, báo cáo hao hụt")
public class StockCountController {

    private final StockCountService service;

    @GetMapping
    @Operation(summary = "100 phiếu kiểm kê gần nhất (ADMIN chọn chi nhánh, vai trò khác chi nhánh đang đăng nhập)")
    public ApiResponse<List<StockCount>> list(@RequestParam(required = false) Long branchId) {
        return ApiResponse.ok(service.list(branchId));
    }

    @GetMapping("/{id}")
    public ApiResponse<StockCount> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    @Operation(summary = "Tạo phiếu kiểm kê ở chi nhánh đang đăng nhập")
    public ApiResponse<StockCount> create(@RequestBody(required = false) Map<String, String> body) {
        return ApiResponse.ok("Đã tạo phiếu kiểm kê", service.create(body != null ? body.get("note") : null));
    }

    @PutMapping("/{id}/lines")
    @Operation(summary = "Thêm / cập nhật số đếm của một mặt hàng (productId hoặc barcode; add=true cộng dồn khi quét)")
    public ApiResponse<StockCount> upsertLine(@PathVariable Long id, @RequestBody StockCountService.LineRequest req) {
        return ApiResponse.ok(service.upsertLine(id, req));
    }

    @DeleteMapping("/{id}/lines/{productId}")
    public ApiResponse<StockCount> removeLine(@PathVariable Long id, @PathVariable Long productId) {
        return ApiResponse.ok(service.removeLine(id, productId));
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "Gửi quản lý duyệt")
    public ApiResponse<StockCount> submit(@PathVariable Long id) {
        return ApiResponse.ok("Đã gửi duyệt", service.submit(id));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Quản lý duyệt: cộng/trừ tồn theo chênh lệch và ghi nhật ký")
    public ApiResponse<StockCount> approve(@PathVariable Long id) {
        return ApiResponse.ok("Đã duyệt và điều chỉnh tồn kho", service.approve(id));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<StockCount> cancel(@PathVariable Long id) {
        return ApiResponse.ok("Đã hủy phiếu", service.cancel(id));
    }

    @GetMapping("/shrinkage")
    @Operation(summary = "Hao hụt / dư theo lý do trong kỳ (theo giá vốn), chỉ phiếu đã duyệt")
    public ApiResponse<StockCountService.Shrinkage> shrinkage(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(service.shrinkage(branchId, from, to));
    }

    @GetMapping("/reasons")
    public ApiResponse<List<Map<String, String>>> reasons() {
        return ApiResponse.ok(Arrays.stream(AdjustmentReason.values())
                .map(r -> Map.of("value", r.name(), "label", r.getLabel())).toList());
    }
}
