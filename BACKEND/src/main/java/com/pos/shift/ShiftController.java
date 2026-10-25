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
    private final ShiftAssignmentService assignmentService;
    private final WorkShiftService workShiftService;
    private final CurrentUserService currentUserService;

    @GetMapping("/work-shifts")
    @Operation(summary = "Lịch ca trong ngày của chi nhánh (ADMIN chọn chi nhánh, hoặc template=true để xem lịch mẫu chung)")
    public ResponseEntity<ApiResponse<List<WorkShift>>> getWorkShifts(
            @RequestParam(required = false) Long branchId,
            @RequestParam(defaultValue = "false") boolean template) {
        return ResponseEntity.ok(ApiResponse.ok(workShiftService.list(branchId, template, currentUserService.getCurrentUser())));
    }

    @PostMapping("/work-shifts")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Thêm ca vào lịch ca của chi nhánh (branchId trống là lịch mẫu chung)")
    public ResponseEntity<ApiResponse<WorkShift>> createWorkShift(@Valid @RequestBody WorkShiftRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Đã thêm ca", workShiftService.create(req)));
    }

    @PutMapping("/work-shifts/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Sửa tên, giờ bắt đầu/kết thúc, bật/ngưng một ca")
    public ResponseEntity<ApiResponse<WorkShift>> updateWorkShift(@PathVariable Long id, @Valid @RequestBody WorkShiftRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Đã cập nhật ca", workShiftService.update(id, req)));
    }

    @DeleteMapping("/work-shifts/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Xoá ca chưa từng được dùng")
    public ResponseEntity<ApiResponse<Void>> deleteWorkShift(@PathVariable Long id) {
        workShiftService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Đã xoá ca", null));
    }

    @PostMapping("/work-shifts/customize")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Tạo lịch ca riêng cho chi nhánh từ lịch mẫu chung")
    public ResponseEntity<ApiResponse<List<WorkShift>>> customizeBranchShifts(@RequestParam Long branchId) {
        return ResponseEntity.ok(ApiResponse.ok("Đã tạo lịch ca riêng cho chi nhánh", workShiftService.customizeBranch(branchId)));
    }

    @GetMapping("/assignments")
    @Operation(summary = "Lịch xếp ca của chi nhánh trong khoảng ngày (ADMIN chọn chi nhánh)")
    public ResponseEntity<ApiResponse<List<ShiftAssignmentResponse>>> assignments(
            @RequestParam(required = false) Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.ok(assignmentService.list(branchId, from, to, currentUserService.getCurrentUser())));
    }

    @PutMapping("/assignments")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Xếp nhân viên vào một ca của một ngày; staffMemberId trống là bỏ xếp")
    public ResponseEntity<ApiResponse<ShiftAssignmentResponse>> assign(
            @RequestParam(required = false) Long branchId, @Valid @RequestBody ShiftAssignmentRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Đã cập nhật lịch ca", assignmentService.assign(branchId, req, currentUserService.getCurrentUser())));
    }

    @GetMapping("/current")
    @Operation(summary = "Ca đang mở ở chi nhánh đăng nhập, hoặc gợi ý ca, người được xếp và tiền ca trước để lại")
    public ResponseEntity<ApiResponse<CurrentShiftResponse>> getCurrent() {
        return ResponseEntity.ok(ApiResponse.ok(shiftService.getCurrent(currentUserService.getCurrentUser())));
    }

    @PostMapping("/open")
    @Operation(summary = "Mở ca cho nhân viên được xếp (quản lý chọn được người trực thay) với số tiền mặt đầu ca")
    public ResponseEntity<ApiResponse<ShiftSessionResponse>> open(@Valid @RequestBody OpenShiftRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Đã mở ca", shiftService.openShift(req, currentUserService.getCurrentUser())));
    }

    @PostMapping("/current/close")
    @Operation(summary = "Đóng ca đang mở ở chi nhánh: đối soát tiền mặt, VietQR, thẻ và bàn giao")
    public ResponseEntity<ApiResponse<ShiftSessionResponse>> closeCurrent(@Valid @RequestBody CloseShiftRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Đã đóng ca", shiftService.closeCurrentShift(req, currentUserService.getCurrentUser())));
    }

    @GetMapping("/current/cash-movements")
    @Operation(summary = "Thu/chi két của ca đang mở (cả khoản đã xóa)")
    public ResponseEntity<ApiResponse<List<CashMovementResponse>>> currentCashMovements() {
        return ResponseEntity.ok(ApiResponse.ok(shiftService.getCurrentCashMovements(currentUserService.getCurrentUser())));
    }

    @PostMapping("/current/cash-movements")
    @Operation(summary = "Ghi chi trong ca, nộp quỹ giữa ca hoặc bổ sung tiền lẻ")
    public ResponseEntity<ApiResponse<CashMovementResponse>> addCashMovement(@Valid @RequestBody CashMovementRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Đã ghi thu/chi két", shiftService.addCashMovement(req, currentUserService.getCurrentUser())));
    }

    @PutMapping("/cash-movements/{id}")
    @Operation(summary = "Sửa khoản thu/chi khi ca còn mở")
    public ResponseEntity<ApiResponse<CashMovementResponse>> updateCashMovement(@PathVariable Long id, @Valid @RequestBody CashMovementRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Đã sửa thu/chi két", shiftService.updateCashMovement(id, req, currentUserService.getCurrentUser())));
    }

    @DeleteMapping("/cash-movements/{id}")
    @Operation(summary = "Xóa (mềm) khoản thu/chi khi ca còn mở")
    public ResponseEntity<ApiResponse<Void>> deleteCashMovement(@PathVariable Long id) {
        shiftService.deleteCashMovement(id, currentUserService.getCurrentUser());
        return ResponseEntity.ok(ApiResponse.ok("Đã xóa khoản thu/chi", null));
    }

    @GetMapping
    @Operation(summary = "Lịch sử ca kèm tổng thiếu/dư (mặc định hôm nay)",
            description = "ADMIN lọc theo chi nhánh, nhân viên trực (staffId), trạng thái; "
                    + "vai trò khác chỉ thấy ca ở chi nhánh đang đăng nhập.")
    public ResponseEntity<ApiResponse<ShiftSummaryResponse>> list(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Long staffId,
            @RequestParam(required = false) ShiftStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        ShiftFilter filter = new ShiftFilter(branchId, staffId, from, to, status);
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

    @GetMapping("/{id}/cash-movements")
    @Operation(summary = "Thu/chi két của một ca")
    public ResponseEntity<ApiResponse<List<CashMovementResponse>>> cashMovements(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(shiftService.getCashMovements(id, currentUserService.getCurrentUser())));
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Quản lý duyệt ca lệch tiền: ghi cách xử lý và khóa sổ")
    public ResponseEntity<ApiResponse<ShiftSessionResponse>> review(@PathVariable Long id, @Valid @RequestBody ReviewShiftRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Đã duyệt ca", shiftService.reviewShift(id, req, currentUserService.getCurrentUser())));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Quản lý đóng hộ ca nhân viên quên đóng")
    public ResponseEntity<ApiResponse<ShiftSessionResponse>> closeById(@PathVariable Long id, @Valid @RequestBody CloseShiftRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Đã đóng ca", shiftService.closeShiftById(id, req, currentUserService.getCurrentUser())));
    }
}
