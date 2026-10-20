package com.pos.scanner;

import com.pos.common.ApiResponse;
import com.pos.common.BusinessException;
import com.pos.security.CurrentUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/scanner")
@RequiredArgsConstructor
@Tag(name = "Mobile Scanner", description = "Điện thoại quét mã vạch và bắn vào quầy POS đã ghép cặp")
public class ScannerController {

    private final ScannerService scannerService;
    private final CurrentUserService currentUserService;

    @GetMapping("/ping")
    @Operation(summary = "Kiểm tra kết nối máy chủ (không cần đăng nhập, không trả dữ liệu)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> ping() {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("time", System.currentTimeMillis())));
    }

    @GetMapping("/lookup")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CASHIER', 'WAREHOUSE')")
    @Operation(summary = "Tra giá và tồn kho theo mã vạch (không gửi gì tới POS)")
    public ResponseEntity<ApiResponse<ScannerProductDto>> lookup(@RequestParam String barcode) {
        ScannerProductDto product = scannerService.lookup(barcode, currentUserService.getCurrentUser())
                .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND",
                        "Không tìm thấy sản phẩm có mã vạch " + barcode, HttpStatus.NOT_FOUND));
        return ResponseEntity.ok(ApiResponse.ok(product));
    }

    @PostMapping("/broadcast")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CASHIER')")
    @Operation(summary = "Bắn mã vạch vào giỏ hàng của quầy POS đã ghép cặp (409 NOT_PAIRED nếu chưa ghép)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> broadcastBarcode(
            @Valid @RequestBody BarcodeScanRequest req) {
        Map<String, Object> result = scannerService.sendToPos(req, currentUserService.getCurrentUser());
        return ResponseEntity.ok(ApiResponse.ok("Đã gửi mã vạch tới POS", result));
    }

    @GetMapping("/products/cache")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CASHIER', 'WAREHOUSE')")
    @Operation(summary = "Tải danh mục sản phẩm (không có giá vốn) để tra cứu khi mất mạng")
    public ResponseEntity<ApiResponse<List<ScannerProductDto>>> getProductsForOfflineCache() {
        return ResponseEntity.ok(ApiResponse.ok("Tải dữ liệu Offline thành công",
                scannerService.catalog(currentUserService.getCurrentUser())));
    }

    @PostMapping("/sync-batch")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CASHIER')")
    @Operation(summary = "Đồng bộ các mã quét lúc offline; POS sẽ hỏi xác nhận trước khi thêm vào giỏ")
    public ResponseEntity<ApiResponse<Map<String, Object>>> syncBatch(
            @Valid @RequestBody SyncBatchRequest req) {
        Map<String, Object> result = scannerService.syncBatch(req.getPairingId(), req.getScans(), currentUserService.getCurrentUser());
        return ResponseEntity.ok(ApiResponse.ok("Đồng bộ hàng đợi Offline thành công", result));
    }
}
