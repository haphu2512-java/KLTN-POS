package com.pos.scanner;

import com.pos.common.ApiResponse;
import com.pos.product.Product;
import com.pos.product.ProductRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/scanner")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Mobile Scanner", description = "Tích hợp thiết bị di động quét mã vạch và bắn trực tiếp vào POS")
public class ScannerController {

    private final ProductRepository productRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @PostMapping("/broadcast")
    @Operation(summary = "Nhận mã vạch từ điện thoại và phát trực tiếp qua WebSocket tới màn hình POS")
    public ResponseEntity<ApiResponse<Map<String, Object>>> broadcastBarcode(
            @Valid @RequestBody BarcodeScanRequest req) {
        
        String cleanBarcode = req.getBarcode().trim();
        Optional<Product> productOpt = productRepository.findByBarcode(cleanBarcode);

        Map<String, Object> payload = new HashMap<>();
        payload.put("barcode", cleanBarcode);
        payload.put("branchId", req.getBranchId() != null ? req.getBranchId() : 1L);
        payload.put("deviceName", req.getDeviceName() != null ? req.getDeviceName() : "Expo Mobile Scanner");
        payload.put("clientScanId", req.getClientScanId() != null ? req.getClientScanId() : UUID.randomUUID().toString());
        payload.put("action", req.getAction() != null ? req.getAction() : "ADD_TO_CART");
        payload.put("timestamp", System.currentTimeMillis());

        if (productOpt.isPresent()) {
            payload.put("found", true);
            payload.put("product", productOpt.get());
        } else {
            payload.put("found", false);
            payload.put("product", null);
        }

        try {
            // Broadcast to STOMP topic for all connected POS terminals
            messagingTemplate.convertAndSend("/topic/barcode-scans", payload);
            log.info("Broadcasted barcode '{}' to /topic/barcode-scans from device '{}'", 
                    cleanBarcode, payload.get("deviceName"));
        } catch (Exception e) {
            log.warn("Failed to broadcast barcode to WebSocket: {}", e.getMessage());
        }

        return ResponseEntity.ok(ApiResponse.ok("Đã nhận và phát mã vạch tới POS", payload));
    }

    @GetMapping("/products/cache")
    @Operation(summary = "Tải toàn bộ danh mục sản phẩm về điện thoại để tra cứu Offline khi mất mạng")
    public ResponseEntity<ApiResponse<List<Product>>> getProductsForOfflineCache() {
        List<Product> products = productRepository.findByActiveTrue();
        return ResponseEntity.ok(ApiResponse.ok("Tải dữ liệu Offline thành công", products));
    }

    @PostMapping("/sync-batch")
    @Operation(summary = "Đồng bộ danh sách quét Offline khi điện thoại kết nối mạng trở lại")
    public ResponseEntity<ApiResponse<Map<String, Object>>> syncBatch(
            @RequestBody List<BarcodeScanRequest> scans) {
        int count = 0;
        List<Map<String, Object>> results = new ArrayList<>();

        for (BarcodeScanRequest scan : scans) {
            String cleanBarcode = scan.getBarcode().trim();
            Optional<Product> prod = productRepository.findByBarcode(cleanBarcode);

            Map<String, Object> item = new HashMap<>();
            item.put("barcode", cleanBarcode);
            item.put("clientScanId", scan.getClientScanId());
            item.put("found", prod.isPresent());
            prod.ifPresent(p -> item.put("product", p));
            results.add(item);
            count++;

            // Broadcast each scan to POS
            try {
                messagingTemplate.convertAndSend("/topic/barcode-scans", item);
            } catch (Exception e) {
                log.warn("Error broadcasting synced item: {}", e.getMessage());
            }
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("totalSynced", count);
        resp.put("results", results);

        return ResponseEntity.ok(ApiResponse.ok("Đồng bộ hàng đợi Offline thành công", resp));
    }
}
