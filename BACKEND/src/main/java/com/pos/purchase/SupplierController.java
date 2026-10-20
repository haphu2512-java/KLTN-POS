package com.pos.purchase;

import com.pos.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@RestController
@RequestMapping("/api/suppliers")
@RequiredArgsConstructor
@Tag(name = "Suppliers", description = "Quản lý danh sách nhà cung cấp hàng hóa")
public class SupplierController {

    private final SupplierService supplierService;

    @GetMapping
    @Operation(summary = "Lấy danh sách tất cả nhà cung cấp")
    public ResponseEntity<ApiResponse<List<Supplier>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(supplierService.getAllSuppliers()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem chi tiết nhà cung cấp theo ID")
    public ResponseEntity<ApiResponse<Supplier>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(supplierService.getSupplierById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE')")
    @Operation(summary = "Tạo mới nhà cung cấp")
    public ResponseEntity<ApiResponse<Supplier>> create(@RequestBody Supplier supplier) {
        return ResponseEntity.ok(ApiResponse.ok("Tạo nhà cung cấp thành công", supplierService.createSupplier(supplier)));
    }
}
