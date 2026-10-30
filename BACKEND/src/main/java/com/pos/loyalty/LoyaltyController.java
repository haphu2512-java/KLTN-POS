package com.pos.loyalty;

import com.pos.common.ApiResponse;
import com.pos.customer.Customer;
import com.pos.security.CurrentUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/loyalty")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CASHIER')")
@RequiredArgsConstructor
@Tag(name = "Loyalty", description = "Tích điểm, đổi điểm và sổ điểm thành viên")
public class LoyaltyController {

    private final LoyaltyService loyaltyService;
    private final CurrentUserService currentUserService;

    @GetMapping("/policy")
    @Operation(summary = "Quy tắc tích/đổi điểm đang áp dụng (POS dùng để tính trước số điểm được dùng)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> policy() {
        LoyaltyPolicy p = loyaltyService.getPolicy();
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "earnPerAmount", p.getEarnPerAmount(),
                "pointValue", p.getPointValue(),
                "redeemMaxPercent", p.getRedeemMaxPercent(),
                "minRedeemPoints", p.getMinRedeemPoints())));
    }

    @GetMapping("/customers/{customerId}/transactions")
    @Operation(summary = "Lịch sử biến động điểm của khách hàng, mới nhất trước")
    public ResponseEntity<ApiResponse<List<PointTransaction>>> history(@PathVariable Long customerId) {
        return ResponseEntity.ok(ApiResponse.ok(loyaltyService.history(customerId)));
    }

    @PostMapping("/customers/{customerId}/adjust")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Quản lý cộng/trừ điểm tay (bắt buộc lý do), ghi vào sổ điểm")
    public ResponseEntity<ApiResponse<Customer>> adjust(@PathVariable Long customerId, @RequestBody AdjustRequest req) {
        int delta = req.points() != null ? req.points() : 0;
        return ResponseEntity.ok(ApiResponse.ok("Đã điều chỉnh điểm", loyaltyService.adjust(
                customerId, delta, req.reason(), currentUserService.getCurrentUser().getId())));
    }

    public record AdjustRequest(Integer points, String reason) {}
}
