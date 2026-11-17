package com.pos.audit;

import com.pos.common.ApiResponse;
import com.pos.security.CurrentUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
@Tag(name = "Audit log", description = "Nhật ký thao tác: ai làm gì, lúc nào")
public class AuditController {

    private final AuditLogRepository repository;
    private final CurrentUserService currentUserService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Tra cứu nhật ký thao tác (ADMIN: mọi chi nhánh; MANAGER: chi nhánh đang đăng nhập)")
    public ApiResponse<AuditPage> search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) String username,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(30);
        Long branch = currentUserService.branchForRead(branchId, true);
        Page<AuditLog> result = repository.search(start.atStartOfDay(), end.plusDays(1).atStartOfDay(), action, branch,
                StringUtils.hasText(username) ? username.trim() : null,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200)));
        return ApiResponse.ok(new AuditPage(result.getContent(), result.getTotalElements(), result.getNumber(), result.getSize()));
    }

    public record AuditPage(List<AuditLog> items, long total, int page, int size) {}

    @GetMapping("/actions")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Danh sách loại thao tác để lọc")
    public ApiResponse<List<Map<String, String>>> actions() {
        return ApiResponse.ok(Arrays.stream(AuditAction.values())
                .map(a -> Map.of("value", a.name(), "label", a.getLabel()))
                .toList());
    }
}
