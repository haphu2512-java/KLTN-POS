package com.pos.branch;

import com.pos.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/branches")
@RequiredArgsConstructor
@Tag(name = "Branches", description = "Quản lý hệ thống chi nhánh cửa hàng")
public class BranchController {

    private final BranchService branchService;

    @GetMapping
    @Operation(summary = "Lấy danh sách tất cả chi nhánh")
    public ResponseEntity<ApiResponse<List<Branch>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(branchService.getAllBranches()));
    }

    @GetMapping("/active")
    @Operation(summary = "Lấy danh sách chi nhánh đang hoạt động")
    public ResponseEntity<ApiResponse<List<Branch>>> getActive() {
        return ResponseEntity.ok(ApiResponse.ok(branchService.getActiveBranches()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem chi tiết chi nhánh theo ID")
    public ResponseEntity<ApiResponse<Branch>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(branchService.getBranchById(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Tạo chi nhánh mới (Chỉ Admin)")
    public ResponseEntity<ApiResponse<Branch>> create(@RequestBody Branch branch) {
        return ResponseEntity.ok(ApiResponse.ok("Tạo chi nhánh thành công", branchService.createBranch(branch)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cập nhật thông tin chi nhánh (Chỉ Admin)")
    public ResponseEntity<ApiResponse<Branch>> update(@PathVariable Long id, @RequestBody Branch branch) {
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật chi nhánh thành công", branchService.updateBranch(id, branch)));
    }
}
