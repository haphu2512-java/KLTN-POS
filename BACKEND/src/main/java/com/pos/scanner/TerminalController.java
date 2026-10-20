package com.pos.scanner;

import com.pos.common.ApiResponse;
import com.pos.security.CurrentUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/scanner")
@RequiredArgsConstructor
@Tag(name = "Mobile Scanner - Ghép cặp", description = "Đăng ký quầy POS và ghép cặp điện thoại với quầy bằng mã QR")
public class TerminalController {

    private final TerminalService terminalService;
    private final CurrentUserService currentUserService;

    public record TerminalRequest(@Size(max = 100) String name) {
    }

    public record PairRequest(@NotBlank(message = "Thiếu mã ghép cặp") @Size(max = 20) String code,
                              @Size(max = 100) String deviceName) {
    }

    // ===== Phía máy POS =====

    @PostMapping("/terminals")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CASHIER')")
    @Operation(summary = "Máy POS đăng ký mình là một quầy (gọi một lần, lưu id trên máy)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> register(@Valid @RequestBody(required = false) TerminalRequest req) {
        PosTerminal t = terminalService.register(req == null ? null : req.name(), currentUserService.getCurrentUser());
        return ResponseEntity.ok(ApiResponse.ok("Đã đăng ký quầy POS", toDto(t)));
    }

    @GetMapping("/terminals/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CASHIER')")
    @Operation(summary = "Thông tin quầy (404 nếu không tồn tại hoặc khác chi nhánh)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> get(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(toDto(terminalService.get(id, currentUserService.getCurrentUser()))));
    }

    @PutMapping("/terminals/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CASHIER')")
    @Operation(summary = "Đổi tên quầy (ví dụ: Quầy 1)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> rename(@PathVariable String id,
                                                                   @Valid @RequestBody TerminalRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Đã đổi tên quầy",
                toDto(terminalService.rename(id, req.name(), currentUserService.getCurrentUser()))));
    }

    @PostMapping("/terminals/{id}/pairing-code")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CASHIER')")
    @Operation(summary = "Tạo mã ghép cặp 6 số để hiện QR trên máy POS")
    public ResponseEntity<ApiResponse<Map<String, Object>>> pairingCode(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(terminalService.createPairingCode(id, currentUserService.getCurrentUser())));
    }

    @GetMapping("/terminals/{id}/pairings")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CASHIER')")
    @Operation(summary = "Danh sách điện thoại đã ghép với quầy")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> pairings(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(terminalService.listPairings(id, currentUserService.getCurrentUser())));
    }

    @DeleteMapping("/terminals/{id}/pairings/{pairingId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CASHIER')")
    @Operation(summary = "Gỡ một điện thoại khỏi quầy")
    public ResponseEntity<ApiResponse<Void>> unpairFromTerminal(@PathVariable String id, @PathVariable String pairingId) {
        terminalService.unpairFromTerminal(id, pairingId, currentUserService.getCurrentUser());
        return ResponseEntity.ok(ApiResponse.ok("Đã gỡ điện thoại khỏi quầy", null));
    }

    // ===== Phía điện thoại =====

    @PostMapping("/pair")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CASHIER')")
    @Operation(summary = "Điện thoại gửi mã đọc từ QR để ghép cặp với quầy")
    public ResponseEntity<ApiResponse<Map<String, Object>>> pair(@Valid @RequestBody PairRequest req) {
        ScannerPairing p = terminalService.pair(req.code(), req.deviceName(), currentUserService.getCurrentUser());
        return ResponseEntity.ok(ApiResponse.ok("Đã ghép cặp với " + p.getTerminal().getName(), TerminalService.toDto(p)));
    }

    @GetMapping("/pairings/{pairingId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CASHIER')")
    @Operation(summary = "Điện thoại kiểm tra ghép cặp của mình còn hiệu lực không")
    public ResponseEntity<ApiResponse<Map<String, Object>>> myPairing(@PathVariable String pairingId) {
        return ResponseEntity.ok(ApiResponse.ok(TerminalService.toDto(
                terminalService.getOwnPairing(pairingId, currentUserService.getCurrentUser()))));
    }

    @DeleteMapping("/pairings/{pairingId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CASHIER')")
    @Operation(summary = "Điện thoại hủy ghép cặp của mình")
    public ResponseEntity<ApiResponse<Void>> unpairOwn(@PathVariable String pairingId) {
        terminalService.unpairOwn(pairingId, currentUserService.getCurrentUser());
        return ResponseEntity.ok(ApiResponse.ok("Đã hủy ghép cặp", null));
    }

    private static Map<String, Object> toDto(PosTerminal t) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", t.getId());
        dto.put("name", t.getName());
        dto.put("branchId", t.getBranchId());
        return dto;
    }
}
