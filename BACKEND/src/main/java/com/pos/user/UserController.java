package com.pos.user;

import com.pos.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "User Management", description = "Quản lý nhân viên, tài khoản và phân quyền người dùng")
public class UserController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "Lấy danh sách tất cả nhân viên / tài khoản")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(userService.getAllUsers()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem thông tin chi tiết nhân viên theo ID")
    public ResponseEntity<ApiResponse<UserResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getUserById(id)));
    }

    @PostMapping
    @Operation(summary = "Tạo mới tài khoản nhân viên")
    public ResponseEntity<ApiResponse<UserResponse>> create(@Valid @RequestBody CreateUserRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Tạo tài khoản nhân viên thành công", userService.createUser(req)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật thông tin nhân viên (họ tên, vai trò, chi nhánh, trạng thái)")
    public ResponseEntity<ApiResponse<UserResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật nhân viên thành công", userService.updateUser(id, req)));
    }

    @PatchMapping("/{id}/toggle-status")
    @Operation(summary = "Khóa hoặc mở khóa tài khoản nhân viên")
    public ResponseEntity<ApiResponse<UserResponse>> toggleStatus(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật trạng thái thành công", userService.toggleUserStatus(id)));
    }

    @PostMapping("/{id}/change-password")
    @Operation(summary = "Đổi mật khẩu cho tài khoản nhân viên")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String newPassword = body.get("newPassword");
        userService.changePassword(id, newPassword);
        return ResponseEntity.ok(ApiResponse.ok("Đổi mật khẩu thành công", null));
    }
}
