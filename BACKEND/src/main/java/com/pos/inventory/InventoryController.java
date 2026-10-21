package com.pos.inventory;

import com.pos.common.ApiResponse;
import com.pos.security.CurrentUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory", description = "Quản lý tồn kho theo chi nhánh và điều chuyển hàng")
public class InventoryController {

    private final InventoryService inventoryService;
    private final CurrentUserService currentUserService;

    @GetMapping
    @Operation(summary = "Tồn kho theo chi nhánh (mặc định chi nhánh đang đăng nhập; chỉ ADMIN xem chi nhánh khác)")
    public ResponseEntity<ApiResponse<List<Inventory>>> getByBranch(@RequestParam(required = false) Long branchId) {
        return ResponseEntity.ok(ApiResponse.ok(inventoryService.getInventoryByBranch(currentUserService.branchForRead(branchId, false))));
    }

    @GetMapping("/branch/{branchId}")
    @Operation(summary = "Lấy danh sách tồn kho theo Chi nhánh (Path Variable)")
    public ResponseEntity<ApiResponse<List<Inventory>>> getByBranchPath(@PathVariable Long branchId) {
        return ResponseEntity.ok(ApiResponse.ok(inventoryService.getInventoryByBranch(currentUserService.branchForRead(branchId, false))));
    }

    @GetMapping("/low-stock")
    @Operation(summary = "Lấy danh sách sản phẩm dưới ngưỡng an toàn (sắp hết hàng)")
    public ResponseEntity<ApiResponse<List<Inventory>>> getLowStock(@RequestParam(required = false) Long branchId) {
        return ResponseEntity.ok(ApiResponse.ok(inventoryService.getLowStockByBranch(currentUserService.branchForRead(branchId, false))));
    }

    @GetMapping("/product")
    @Operation(summary = "Xem chi tiết tồn kho của 1 sản phẩm tại chi nhánh")
    public ResponseEntity<ApiResponse<Inventory>> getInventory(
            @RequestParam Long productId,
            @RequestParam(required = false) Long branchId) {
        return ResponseEntity.ok(ApiResponse.ok(inventoryService.getInventory(productId, currentUserService.branchForRead(branchId, false))));
    }

    @PostMapping("/transfer")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE')")
    @Operation(summary = "Điều chuyển hàng từ chi nhánh đang đăng nhập sang chi nhánh khác")
    public ResponseEntity<ApiResponse<Void>> transferStock(@Valid @RequestBody StockTransferRequest req) {
        req.setFromBranchId(currentUserService.branchForWrite(req.getFromBranchId()));
        inventoryService.transferStock(req);
        return ResponseEntity.ok(ApiResponse.ok("Điều chuyển kho thành công", null));
    }
}
