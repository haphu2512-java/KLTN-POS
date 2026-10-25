package com.pos.shift;

import com.pos.branch.BranchRepository;
import com.pos.common.BusinessException;
import com.pos.user.Role;
import com.pos.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Lịch ca trong ngày theo chi nhánh. Mỗi chi nhánh có thể có số ca và giờ ca riêng (vd. 2 ca/ngày hay 4 ca/ngày);
 * chi nhánh chưa cấu hình riêng thì dùng lịch ca mẫu chung (branch_id null).
 */
@Service
@RequiredArgsConstructor
public class WorkShiftService {

    private final WorkShiftRepository workShiftRepository;
    private final ShiftSessionRepository shiftSessionRepository;
    private final ShiftAssignmentRepository assignmentRepository;
    private final BranchRepository branchRepository;

    /** Các ca chi nhánh đang dùng: ca riêng nếu có, không thì lịch mẫu chung. {@code branchId} null trả lịch mẫu */
    public List<WorkShift> shiftsOf(Long branchId) {
        if (branchId != null && workShiftRepository.existsByBranchId(branchId)) {
            return workShiftRepository.findByBranchIdOrderBySortOrderAsc(branchId);
        }
        return workShiftRepository.findByBranchIdIsNullOrderBySortOrderAsc();
    }

    public List<WorkShift> activeShiftsOf(Long branchId) {
        return shiftsOf(branchId).stream().filter(ws -> Boolean.TRUE.equals(ws.getActive())).toList();
    }

    /** Ca {@code workShiftId} thuộc lịch ca chi nhánh đang dùng, nếu không thì báo lỗi */
    public WorkShift requireForBranch(Long branchId, Long workShiftId) {
        return shiftsOf(branchId).stream()
                .filter(ws -> ws.getId().equals(workShiftId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("WORK_SHIFT_NOT_FOUND", "Ca làm việc không thuộc lịch ca của chi nhánh"));
    }

    /**
     * Lịch ca để xem: ADMIN chọn chi nhánh bất kỳ (bỏ trống và {@code template} = true để xem lịch mẫu),
     * vai trò khác xem lịch ca của chi nhánh đang đăng nhập.
     */
    public List<WorkShift> list(Long branchId, boolean template, User actor) {
        if (actor.getRole() == Role.ADMIN) {
            return template ? shiftsOf(null) : shiftsOf(branchId != null ? branchId : actor.getCurrentBranchId());
        }
        return shiftsOf(actor.getCurrentBranchId());
    }

    @Transactional
    public WorkShift create(WorkShiftRequest req) {
        Long branchId = req.getBranchId();
        if (branchId != null && !branchRepository.existsById(branchId)) {
            throw new BusinessException("BRANCH_NOT_FOUND", "Chi nhánh không tồn tại", HttpStatus.NOT_FOUND);
        }
        List<WorkShift> scope = branchId == null ? workShiftRepository.findByBranchIdIsNullOrderBySortOrderAsc()
                : workShiftRepository.findByBranchIdOrderBySortOrderAsc(branchId);
        int nextOrder = scope.stream().mapToInt(WorkShift::getSortOrder).max().orElse(0) + 1;
        WorkShift shift = WorkShift.builder().branchId(branchId).sortOrder(nextOrder).active(true).build();
        apply(shift, req);
        return workShiftRepository.save(shift);
    }

    @Transactional
    public WorkShift update(Long id, WorkShiftRequest req) {
        WorkShift shift = find(id);
        apply(shift, req);
        return workShiftRepository.save(shift);
    }

    /**
     * Tạo lịch ca riêng cho chi nhánh bằng cách chép lịch mẫu chung, để chỉnh tiếp số ca và giờ ca.
     * Lịch xếp ca đã có theo ca mẫu được chuyển sang ca tương ứng của chi nhánh.
     */
    @Transactional
    public List<WorkShift> customizeBranch(Long branchId) {
        if (branchId == null || !branchRepository.existsById(branchId)) {
            throw new BusinessException("BRANCH_NOT_FOUND", "Chi nhánh không tồn tại", HttpStatus.NOT_FOUND);
        }
        if (workShiftRepository.existsByBranchId(branchId)) {
            throw new BusinessException("BRANCH_SHIFTS_EXIST", "Chi nhánh đã có lịch ca riêng");
        }
        if (!shiftSessionRepository.findByBranchIdAndStatus(branchId, ShiftStatus.OPEN).isEmpty()) {
            throw new BusinessException("SHIFT_ALREADY_OPEN", "Chi nhánh đang có ca mở, đóng ca trước khi đổi lịch ca");
        }
        for (WorkShift template : workShiftRepository.findByBranchIdIsNullOrderBySortOrderAsc()) {
            WorkShift copy = workShiftRepository.save(WorkShift.builder()
                    .branchId(branchId)
                    .name(template.getName())
                    .startTime(template.getStartTime())
                    .endTime(template.getEndTime())
                    .sortOrder(template.getSortOrder())
                    .active(template.getActive())
                    .build());
            assignmentRepository.findByBranchIdAndWorkShiftId(branchId, template.getId()).stream()
                    .filter(a -> !a.getShiftDate().isBefore(LocalDate.now()))
                    .forEach(a -> a.setWorkShiftId(copy.getId()));
        }
        return workShiftRepository.findByBranchIdOrderBySortOrderAsc(branchId);
    }

    /** Xoá ca chưa từng được dùng; ca đã có lịch sử mở ca hoặc lịch xếp ca thì chỉ ngưng được */
    @Transactional
    public void delete(Long id) {
        WorkShift shift = find(id);
        if (shiftSessionRepository.existsByWorkShiftId(id) || assignmentRepository.existsByWorkShiftId(id)) {
            throw new BusinessException("WORK_SHIFT_IN_USE",
                    shift.getName() + " đã có lịch sử mở ca hoặc lịch xếp ca, chỉ có thể ngưng (bỏ chọn Đang dùng)");
        }
        workShiftRepository.delete(shift);
    }

    private WorkShift find(Long id) {
        return workShiftRepository.findById(id)
                .orElseThrow(() -> new BusinessException("WORK_SHIFT_NOT_FOUND", "Không tìm thấy ca làm việc", HttpStatus.NOT_FOUND));
    }

    private void apply(WorkShift shift, WorkShiftRequest req) {
        if (req.getStartTime().equals(req.getEndTime())) {
            throw new BusinessException("INVALID_WORK_SHIFT", "Giờ bắt đầu và kết thúc không được trùng nhau");
        }
        String name = req.getName().trim();
        boolean duplicate = (shift.getBranchId() == null ? workShiftRepository.findByBranchIdIsNullOrderBySortOrderAsc()
                : workShiftRepository.findByBranchIdOrderBySortOrderAsc(shift.getBranchId())).stream()
                .anyMatch(ws -> !Objects.equals(ws.getId(), shift.getId()) && ws.getName().equalsIgnoreCase(name));
        if (duplicate) {
            throw new BusinessException("DUPLICATE_WORK_SHIFT", "Đã có ca tên \"" + name + "\" trong lịch ca này");
        }
        shift.setName(name);
        shift.setStartTime(req.getStartTime());
        shift.setEndTime(req.getEndTime());
        if (req.getActive() != null) {
            shift.setActive(req.getActive());
        }
    }
}
