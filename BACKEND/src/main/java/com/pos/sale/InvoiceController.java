package com.pos.sale;

import com.pos.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
@Tag(name = "Invoices & Sales", description = "Quản lý hóa đơn bán lẻ POS và thanh toán tại quầy")
public class InvoiceController {

    private final InvoiceService invoiceService;

    @GetMapping
    @Operation(summary = "Lấy danh sách hóa đơn bán lẻ (hỗ trợ phân trang hoặc lấy tất cả theo chi nhánh)")
    public ResponseEntity<ApiResponse<?>> getAll(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        if (page != null && size != null) {
            Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size));
            return ResponseEntity.ok(ApiResponse.ok(invoiceService.getInvoices(branchId, pageable)));
        }
        return ResponseEntity.ok(ApiResponse.ok(invoiceService.getAllInvoices(branchId)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem chi tiết hóa đơn theo ID")
    public ResponseEntity<ApiResponse<Invoice>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(invoiceService.getInvoiceById(id)));
    }

    @PostMapping
    @Operation(summary = "Tạo hóa đơn thanh toán (tự động kiểm tra và trừ tồn kho nguyên tử)")
    public ResponseEntity<ApiResponse<Invoice>> create(@Valid @RequestBody InvoiceRequest req) {
        Invoice invoice = invoiceService.createInvoice(req);
        return ResponseEntity.ok(ApiResponse.ok("Tạo hóa đơn thành công", invoice));
    }

    @PostMapping("/{id}/refund")
    @Operation(summary = "Hoàn trả hàng / Hủy hóa đơn (tự động hoàn kho nguyên tử và thu hồi điểm)")
    public ResponseEntity<ApiResponse<Invoice>> refund(
            @PathVariable Long id,
            @RequestBody(required = false) java.util.Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        Invoice refunded = invoiceService.refundInvoice(id, reason);
        return ResponseEntity.ok(ApiResponse.ok("Hoàn trả hàng thành công", refunded));
    }
}
