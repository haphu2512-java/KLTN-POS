package com.pos.purchaseorder;

import com.pos.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/purchase-orders")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE')")
@Tag(name = "Purchase orders", description = "Đơn đặt hàng nhà cung cấp, lập từ đề xuất AI dự báo")
public class PurchaseOrderController {

    private final PurchaseOrderService service;

    @GetMapping
    public ApiResponse<List<PurchaseOrder>> list(@RequestParam(required = false) Long branchId) {
        return ApiResponse.ok(service.list(branchId));
    }

    @GetMapping("/{id}")
    public ApiResponse<PurchaseOrder> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping("/from-forecast")
    @Operation(summary = "Lập đơn nháp từ đề xuất nhập hàng của AI (mỗi nhà cung cấp một đơn)")
    public ApiResponse<List<PurchaseOrder>> fromForecast(@RequestParam(defaultValue = "7") int days) {
        List<PurchaseOrder> orders = service.createFromForecast(days);
        return ApiResponse.ok("Đã lập " + orders.size() + " đơn đặt hàng nháp", orders);
    }

    @PostMapping
    public ApiResponse<PurchaseOrder> create(@RequestBody PurchaseOrderService.UpdateRequest req) {
        return ApiResponse.ok("Đã tạo đơn đặt hàng", service.createManual(req));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Sửa đơn nháp: nhà cung cấp, ghi chú, ngày dự kiến, số lượng / đơn giá")
    public ApiResponse<PurchaseOrder> update(@PathVariable Long id, @RequestBody PurchaseOrderService.UpdateRequest req) {
        return ApiResponse.ok(service.update(id, req));
    }

    @PostMapping("/{id}/send")
    public ApiResponse<PurchaseOrder> send(@PathVariable Long id) {
        return ApiResponse.ok("Đã chuyển sang Đã gửi", service.send(id));
    }

    @PostMapping("/{id}/receive")
    @Operation(summary = "Nhận hàng (lines rỗng = nhận đủ phần còn lại); tạo phiếu nhập kho tương ứng")
    public ApiResponse<PurchaseOrderService.ReceiveResult> receive(@PathVariable Long id,
                                                                  @RequestBody(required = false) PurchaseOrderService.ReceiveRequest req) {
        return ApiResponse.ok("Đã nhận hàng và nhập kho", service.receive(id, req));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<PurchaseOrder> cancel(@PathVariable Long id) {
        return ApiResponse.ok("Đã hủy đơn", service.cancel(id));
    }
}
