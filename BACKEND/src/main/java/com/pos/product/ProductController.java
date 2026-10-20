package com.pos.product;

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
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Quản lý danh mục sản phẩm và tra cứu mã vạch POS")
public class ProductController {

    private final ProductService productService;

    @GetMapping
    @Operation(summary = "Lấy danh sách sản phẩm (hỗ trợ tìm kiếm theo tên, mã vạch hoặc lọc danh mục)")
    public ResponseEntity<ApiResponse<List<Product>>> getAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category) {
        return ResponseEntity.ok(ApiResponse.ok(productService.getAllProducts(search, category)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy chi tiết sản phẩm theo ID")
    public ResponseEntity<ApiResponse<Product>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(productService.getProductById(id)));
    }

    @GetMapping("/barcode/{barcode}")
    @Operation(summary = "Tra cứu nhanh sản phẩm theo mã vạch Barcode (dùng cho đầu đọc POS)")
    public ResponseEntity<ApiResponse<Product>> getByBarcode(@PathVariable String barcode) {
        return ResponseEntity.ok(ApiResponse.ok(productService.getProductByBarcode(barcode)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE')")
    @Operation(summary = "Tạo mới sản phẩm")
    public ResponseEntity<ApiResponse<Product>> create(@Valid @RequestBody ProductRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Tạo sản phẩm thành công", productService.createProduct(req)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE')")
    @Operation(summary = "Cập nhật sản phẩm")
    public ResponseEntity<ApiResponse<Product>> update(@PathVariable Long id, @Valid @RequestBody ProductRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật sản phẩm thành công", productService.updateProduct(id, req)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE')")
    @Operation(summary = "Xóa sản phẩm (Soft delete)")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok(ApiResponse.ok("Xóa sản phẩm thành công", null));
    }
}
