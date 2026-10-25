package com.pos.shift;

import com.pos.branch.BranchRepository;
import com.pos.common.BusinessException;
import com.pos.staff.StaffMember;
import com.pos.staff.StaffMemberRepository;
import com.pos.staff.StaffService;
import com.pos.user.Role;
import com.pos.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Lịch xếp ca: mỗi ô (chi nhánh, ca, ngày) xếp tối đa một nhân viên. MANAGER xếp ở chi nhánh đang đăng nhập,
 * ADMIN xếp ở chi nhánh bất kỳ; mọi vai trò xem được lịch của chi nhánh mình.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShiftAssignmentService {

    /** Một lần xem tối đa 62 ngày (đủ hai tháng) */
    static final int MAX_RANGE_DAYS = 62;

    private final ShiftAssignmentRepository assignmentRepository;
    private final WorkShiftRepository workShiftRepository;
    private final StaffMemberRepository staffRepository;
    private final StaffService staffService;
    private final BranchRepository branchRepository;

    public List<ShiftAssignmentResponse> list(Long branchId, LocalDate from, LocalDate to, User actor) {
        Long branch = branchFor(branchId, actor);
        if (from.isAfter(to) || ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
            throw new BusinessException("INVALID_DATE_RANGE", "Khoảng ngày không hợp lệ (tối đa " + MAX_RANGE_DAYS + " ngày)");
        }
        List<ShiftAssignment> rows = assignmentRepository.findByBranchIdAndShiftDateBetweenOrderByShiftDateAsc(branch, from, to);
        Map<Long, String> names = staffRepository.findAllById(rows.stream().map(ShiftAssignment::getStaffMemberId).distinct().toList())
                .stream().collect(Collectors.toMap(StaffMember::getId, StaffMember::getFullName));
        return rows.stream().map(a -> toResponse(a, names.get(a.getStaffMemberId()))).toList();
    }

    /** Xếp (hoặc bỏ xếp khi staffMemberId trống) một nhân viên vào một ca của một ngày */
    @Transactional
    public ShiftAssignmentResponse assign(Long branchId, ShiftAssignmentRequest req, User actor) {
        if (actor.getRole() != Role.ADMIN && actor.getRole() != Role.MANAGER) {
            throw new BusinessException("SHIFT_FORBIDDEN", "Chỉ quản lý được xếp ca", HttpStatus.FORBIDDEN);
        }
        Long branch = branchFor(branchId, actor);
        if (!workShiftRepository.existsById(req.getWorkShiftId())) {
            throw new BusinessException("WORK_SHIFT_NOT_FOUND", "Ca làm việc không tồn tại");
        }
        if (req.getShiftDate().isBefore(LocalDate.now())) {
            throw new BusinessException("PAST_ASSIGNMENT", "Không xếp ca cho ngày đã qua");
        }
        ShiftAssignment slot = assignmentRepository
                .findByBranchIdAndWorkShiftIdAndShiftDate(branch, req.getWorkShiftId(), req.getShiftDate()).orElse(null);
        if (req.getStaffMemberId() == null) {
            if (slot != null) assignmentRepository.delete(slot);
            return null;
        }
        StaffMember staff = staffService.activeIn(branch, req.getStaffMemberId());
        if (slot == null) {
            slot = ShiftAssignment.builder().branchId(branch).workShiftId(req.getWorkShiftId()).shiftDate(req.getShiftDate()).build();
        }
        slot.setStaffMemberId(staff.getId());
        slot.setAssignedBy(actor.getId());
        slot.setAssignedAt(LocalDateTime.now());
        return toResponse(assignmentRepository.save(slot), staff.getFullName());
    }

    private Long branchFor(Long requested, User actor) {
        Long branch = actor.getRole() == Role.ADMIN && requested != null ? requested : actor.getCurrentBranchId();
        if (branch == null || !branchRepository.existsById(branch)) {
            throw new BusinessException("BRANCH_REQUIRED", "Chưa chọn chi nhánh hợp lệ");
        }
        return branch;
    }

    private static ShiftAssignmentResponse toResponse(ShiftAssignment a, String staffName) {
        return ShiftAssignmentResponse.builder()
                .id(a.getId())
                .branchId(a.getBranchId())
                .workShiftId(a.getWorkShiftId())
                .shiftDate(a.getShiftDate())
                .staffMemberId(a.getStaffMemberId())
                .staffName(staffName)
                .build();
    }
}
