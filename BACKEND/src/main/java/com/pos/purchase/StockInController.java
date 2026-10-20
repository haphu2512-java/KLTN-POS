package com.pos.purchase;

import com.pos.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@RestController
@RequestMapping("/api/stock-in")
@RequiredArgsConstructor
@Tag(name = "Stock In", description = "Quản lý phiếu nhập kho hàng hóa từ nhà cung cấp")
public class StockInController {

    private final StockInService stockInService;

    @GetMapping
    @Operation(summary = "Lấy danh sách phiếu nhập kho (có thể lọc theo chi nhánh)")
    public ResponseEntity<ApiResponse<List<StockIn>>> getAll(@RequestParam(required = false) Long branchId) {
        return ResponseEntity.ok(ApiResponse.ok(stockInService.getAllStockIn(branchId)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem chi tiết phiếu nhập kho theo ID")
    public ResponseEntity<ApiResponse<StockIn>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(stockInService.getStockInById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE')")
    @Operation(summary = "Tạo phiếu nhập kho (tự động cộng tồn kho tương ứng)")
    public ResponseEntity<ApiResponse<StockIn>> create(@Valid @RequestBody StockInRequest req) {
        StockIn created = stockInService.createStockIn(req);
        return ResponseEntity.ok(ApiResponse.ok("Tạo phiếu nhập kho thành công", created));
    }
}
