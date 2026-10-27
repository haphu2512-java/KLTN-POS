package com.pos.staff;

import com.pos.common.ApiResponse;
import com.pos.security.CurrentUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/staff")
@RequiredArgsConstructor
@Tag(name = "Staff", description = "Nhân viên trực ca (không phải tài khoản đăng nhập)")
public class StaffController {

    private final StaffService staffService;
    private final CurrentUserService currentUserService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CASHIER')")
    @Operation(summary = "Nhân viên của chi nhánh (ADMIN lọc theo branchId)")
    public ResponseEntity<ApiResponse<List<StaffMember>>> list(@RequestParam(required = false) Long branchId) {
        return ResponseEntity.ok(ApiResponse.ok(staffService.list(branchId, currentUserService.getCurrentUser())));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Thêm nhân viên")
    public ResponseEntity<ApiResponse<StaffMember>> create(@Valid @RequestBody StaffRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Đã thêm nhân viên", staffService.create(req, currentUserService.getCurrentUser())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Sửa thông tin, ngưng/kích hoạt nhân viên")
    public ResponseEntity<ApiResponse<StaffMember>> update(@PathVariable Long id, @Valid @RequestBody StaffRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Đã cập nhật nhân viên", staffService.update(id, req, currentUserService.getCurrentUser())));
    }
}
