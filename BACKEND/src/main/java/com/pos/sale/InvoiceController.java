package com.pos.sale;

import com.pos.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
@Tag(name = "Invoices & Sales", description = "Quản lý hóa đơn bán lẻ POS và thanh toán tại quầy")
public class InvoiceController {

    private final InvoiceService invoiceService;

    @GetMapping
    @Operation(summary = "Lấy danh sách hóa đơn bán lẻ (có thể lọc theo chi nhánh)")
    public ResponseEntity<ApiResponse<List<Invoice>>> getAll(@RequestParam(required = false) Long branchId) {
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
}
