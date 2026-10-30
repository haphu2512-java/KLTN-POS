package com.pos.customer;

import com.pos.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CASHIER')")
@RequiredArgsConstructor
@Tag(name = "Customers", description = "Quản lý khách hàng và tích điểm thành viên")
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping
    @Operation(summary = "Lấy danh sách khách hàng (hỗ trợ tìm theo tên hoặc SĐT)")
    public ResponseEntity<ApiResponse<List<Customer>>> getAll(@RequestParam(required = false) String search) {
        return ResponseEntity.ok(ApiResponse.ok(customerService.getAllCustomers(search)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem chi tiết khách hàng theo ID")
    public ResponseEntity<ApiResponse<Customer>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(customerService.getCustomerById(id)));
    }

    @GetMapping("/phone/{phone}")
    @Operation(summary = "Tìm khách hàng nhanh bằng SĐT (dùng cho POS F3)")
    public ResponseEntity<ApiResponse<Customer>> getByPhone(@PathVariable String phone) {
        return ResponseEntity.ok(ApiResponse.ok(customerService.getCustomerByPhone(phone)));
    }

    @PostMapping
    @Operation(summary = "Tạo mới thông tin khách hàng")
    public ResponseEntity<ApiResponse<Customer>> create(@Valid @RequestBody CustomerRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Tạo khách hàng thành công", customerService.createCustomer(req)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Sửa tên, SĐT, địa chỉ khách hàng (không sửa điểm ở đây)")
    public ResponseEntity<ApiResponse<Customer>> update(@PathVariable Long id, @Valid @RequestBody CustomerRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Đã cập nhật khách hàng", customerService.updateCustomer(id, req)));
    }
}
