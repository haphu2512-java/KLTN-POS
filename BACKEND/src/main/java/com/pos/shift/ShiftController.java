package com.pos.shift;

import com.pos.common.ApiResponse;
import com.pos.security.CurrentUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/shifts")
@RequiredArgsConstructor
@Tag(name = "Shifts", description = "Ca làm việc: mở ca, chốt tiền cuối ca và bàn giao")
public class ShiftController {

    private final ShiftService shiftService;
    private final CurrentUserService currentUserService;

    @GetMapping("/work-shifts")
    @Operation(summary = "Danh sách các ca cố định trong ngày")
    public ResponseEntity<ApiResponse<List<WorkShift>>> getWorkShifts() {
        return ResponseEntity.ok(ApiResponse.ok(shiftService.getWorkShifts()));
    }

    @PostMapping("/work-shifts")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Thêm một ca cố định mới")
    public ResponseEntity<ApiResponse<WorkShift>> createWorkShift(@Valid @RequestBody WorkShiftRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Đã thêm ca", shiftService.createWorkShift(req)));
    }

    @PutMapping("/work-shifts/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Sửa tên, giờ bắt đầu/kết thúc của một ca cố định")
    public ResponseEntity<ApiResponse<WorkShift>> updateWorkShift(@PathVariable Long id, @Valid @RequestBody WorkShiftRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Đã cập nhật ca", shiftService.updateWorkShift(id, req)));
    }

    @GetMapping("/current")
    @Operation(summary = "Ca đang mở của tài khoản đăng nhập, hoặc gợi ý bàn giao từ ca trước")
    public ResponseEntity<ApiResponse<CurrentShiftResponse>> getCurrent() {
        return ResponseEntity.ok(ApiResponse.ok(shiftService.getCurrent(currentUserService.getCurrentUser())));
    }

    @PostMapping("/open")
    @Operation(summary = "Mở ca với số tiền mặt đầu ca")
    public ResponseEntity<ApiResponse<ShiftSessionResponse>> open(@Valid @RequestBody OpenShiftRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Đã mở ca", shiftService.openShift(req, currentUserService.getCurrentUser())));
    }

    @PostMapping("/current/close")
    @Operation(summary = "Đóng ca của mình: nhập tiền mặt đếm được và ghi chú bàn giao")
    public ResponseEntity<ApiResponse<ShiftSessionResponse>> closeOwn(@Valid @RequestBody CloseShiftRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Đã đóng ca", shiftService.closeOwnShift(req, currentUserService.getCurrentUser())));
    }

    @GetMapping
    @Operation(summary = "Lịch sử ca kèm tổng thiếu/dư (mặc định hôm nay)",
            description = "ADMIN lọc theo chi nhánh, nhân viên, trạng thái; MANAGER chỉ chi nhánh đang đăng nhập; "
                    + "vai trò khác chỉ thấy ca của chính mình.")
    public ResponseEntity<ApiResponse<ShiftSummaryResponse>> list(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) ShiftStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        ShiftFilter filter = new ShiftFilter(branchId, employeeId, from, to, status);
        return ResponseEntity.ok(ApiResponse.ok(shiftService.listShifts(filter, currentUserService.getCurrentUser())));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết một ca (của mình, hoặc quản lý xem ca trong chi nhánh)")
    public ResponseEntity<ApiResponse<ShiftSessionResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(shiftService.getShift(id, currentUserService.getCurrentUser())));
    }

    @GetMapping("/{id}/invoices")
    @Operation(summary = "Hóa đơn bán và hoàn trả làm thay đổi tiền két của ca, để dò nguyên nhân thiếu/dư")
    public ResponseEntity<ApiResponse<List<ShiftInvoiceRow>>> invoices(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(shiftService.getShiftInvoices(id, currentUserService.getCurrentUser())));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Quản lý đóng hộ ca nhân viên quên đóng")
    public ResponseEntity<ApiResponse<ShiftSessionResponse>> closeById(@PathVariable Long id, @Valid @RequestBody CloseShiftRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Đã đóng ca", shiftService.closeShiftById(id, req, currentUserService.getCurrentUser())));
    }
}
