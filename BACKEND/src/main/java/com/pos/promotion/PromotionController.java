package com.pos.promotion;

import com.pos.common.ApiResponse;
import com.pos.common.BusinessException;
import com.pos.security.CurrentUserService;
import com.pos.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Chương trình khuyến mãi: ADMIN tạo, sửa, bật/tắt, xóa; MANAGER xem;
 * máy POS (ADMIN, MANAGER, STAFF) hỏi khuyến mãi áp được cho giỏ hàng đang bán.
 */
@RestController
@RequestMapping("/api/promotions")
@RequiredArgsConstructor
@Tag(name = "Promotions", description = "Quản lý chương trình khuyến mãi và áp khuyến mãi khi bán")
public class PromotionController {

    private final PromotionService promotionService;
    private final CurrentUserService currentUserService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Danh sách chương trình khuyến mãi, mới tạo trước")
    public ResponseEntity<ApiResponse<List<Promotion>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(promotionService.getAll()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Chi tiết chương trình khuyến mãi")
    public ResponseEntity<ApiResponse<Promotion>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(promotionService.getById(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Tạo chương trình khuyến mãi (theo % hoặc số tiền, cả đơn hoặc theo danh mục)")
    public ResponseEntity<ApiResponse<Promotion>> create(@Valid @RequestBody PromotionRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Đã tạo khuyến mãi",
                promotionService.create(req, currentUserService.getCurrentUser().getId())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Sửa chương trình khuyến mãi")
    public ResponseEntity<ApiResponse<Promotion>> update(@PathVariable Long id, @Valid @RequestBody PromotionRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Đã cập nhật khuyến mãi",
                promotionService.update(id, req, currentUserService.getCurrentUser().getId())));
    }

    @PatchMapping("/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Bật hoặc tắt chương trình khuyến mãi")
    public ResponseEntity<ApiResponse<Promotion>> setActive(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        Boolean active = body != null ? body.get("active") : null;
        if (active == null) {
            throw new BusinessException("INVALID_PROMOTION", "Thiếu trạng thái active", HttpStatus.BAD_REQUEST);
        }
        return ResponseEntity.ok(ApiResponse.ok(active ? "Đã bật khuyến mãi" : "Đã tắt khuyến mãi",
                promotionService.setActive(id, active, currentUserService.getCurrentUser().getId())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Xóa chương trình khuyến mãi (hóa đơn cũ vẫn giữ tên và tiền giảm đã lưu)")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        promotionService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Đã xóa khuyến mãi", null));
    }

    @PostMapping("/applicable")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    @Operation(summary = "Khuyến mãi áp được cho giỏ hàng tại chi nhánh đang đăng nhập, giảm nhiều nhất trước")
    public ResponseEntity<ApiResponse<List<PromotionService.Quote>>> applicable(@RequestBody PreviewRequest req) {
        User actor = currentUserService.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.ok(promotionService.preview(
                actor.getCurrentBranchId(), req.customerId(), req.items())));
    }

    public record PreviewRequest(Long customerId, List<PromotionService.PreviewItem> items) {}
}
