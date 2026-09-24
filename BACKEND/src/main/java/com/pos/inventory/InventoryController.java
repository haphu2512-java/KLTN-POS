package com.pos.inventory;

import com.pos.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory", description = "Quản lý tồn kho theo chi nhánh và điều chuyển hàng")
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping
    @Operation(summary = "Lấy danh sách tồn kho theo Chi nhánh")
    public ResponseEntity<ApiResponse<List<Inventory>>> getByBranch(@RequestParam Long branchId) {
        return ResponseEntity.ok(ApiResponse.ok(inventoryService.getInventoryByBranch(branchId)));
    }

    @GetMapping("/low-stock")
    @Operation(summary = "Lấy danh sách sản phẩm dưới ngưỡng an toàn (sắp hết hàng)")
    public ResponseEntity<ApiResponse<List<Inventory>>> getLowStock(@RequestParam Long branchId) {
        return ResponseEntity.ok(ApiResponse.ok(inventoryService.getLowStockByBranch(branchId)));
    }

    @GetMapping("/product")
    @Operation(summary = "Xem chi tiết tồn kho của 1 sản phẩm tại chi nhánh")
    public ResponseEntity<ApiResponse<Inventory>> getInventory(
            @RequestParam Long productId,
            @RequestParam Long branchId) {
        return ResponseEntity.ok(ApiResponse.ok(inventoryService.getInventory(productId, branchId)));
    }

    @PostMapping("/transfer")
    @Operation(summary = "Điều chuyển hàng hóa giữa 2 chi nhánh")
    public ResponseEntity<ApiResponse<Void>> transferStock(@Valid @RequestBody StockTransferRequest req) {
        inventoryService.transferStock(req);
        return ResponseEntity.ok(ApiResponse.ok("Điều chuyển kho thành công", null));
    }
}
