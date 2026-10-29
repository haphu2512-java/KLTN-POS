package com.pos.approval;

import com.pos.common.ApiResponse;
import com.pos.security.CurrentUserService;
import com.pos.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/approvals")
@RequiredArgsConstructor
@Tag(name = "Approvals", description = "Quản lý nhập PIN duyệt tại quầy (giảm giá vượt hạn mức)")
public class ApprovalController {

    private final ApprovalService approvalService;
    private final CurrentUserService currentUserService;

    @GetMapping("/policy")
    @Operation(summary = "Hạn mức giảm giá của thu ngân và tài khoản hiện tại đã đặt PIN duyệt chưa")
    public ResponseEntity<ApiResponse<Map<String, Object>>> policy() {
        User user = currentUserService.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "cashierMaxDiscountPercent", approvalService.getCashierMaxDiscountPercent(),
                "hasPin", approvalService.hasPin(user))));
    }

    @PutMapping("/pin")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Quản lý đặt/đổi PIN duyệt (4-6 chữ số), cần mật khẩu hiện tại")
    public ResponseEntity<ApiResponse<Void>> setPin(@RequestBody Map<String, String> body) {
        approvalService.setPin(currentUserService.getCurrentUser(), body.get("currentPassword"), body.get("pin"));
        return ResponseEntity.ok(ApiResponse.ok("Đã lưu PIN duyệt", null));
    }

    @PostMapping("/discount")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CASHIER')")
    @Operation(summary = "Quản lý nhập tài khoản + PIN trên máy POS để duyệt giảm giá vượt hạn mức; trả mã duyệt dùng một lần (5 phút)")
    public ResponseEntity<ApiResponse<ApprovalService.GrantResult>> approveDiscount(@RequestBody DiscountApprovalRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Quản lý đã duyệt", approvalService.approveDiscount(
                currentUserService.getCurrentUser(), req.approverUsername(), req.pin(), req.amount())));
    }

    public record DiscountApprovalRequest(String approverUsername, String pin, BigDecimal amount) {}
}
