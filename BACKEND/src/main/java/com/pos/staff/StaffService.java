package com.pos.staff;

import com.pos.branch.BranchRepository;
import com.pos.common.BusinessException;
import com.pos.user.Role;
import com.pos.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Danh sách nhân viên trực ca. ADMIN quản lý mọi chi nhánh; MANAGER chỉ chi nhánh đang đăng nhập;
 * vai trò khác chỉ xem nhân viên đang làm ở chi nhánh của mình (để mở ca).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StaffService {

    private final StaffMemberRepository staffRepository;
    private final BranchRepository branchRepository;

    public List<StaffMember> list(Long branchId, User actor) {
        if (actor.getRole() == Role.ADMIN) {
            return branchId == null ? staffRepository.findAllByOrderByBranchIdAscFullNameAsc()
                    : staffRepository.findByBranchIdOrderByActiveDescFullNameAsc(branchId);
        }
        List<StaffMember> list = staffRepository.findByBranchIdOrderByActiveDescFullNameAsc(actor.getCurrentBranchId());
        return actor.getRole() == Role.MANAGER ? list : list.stream().filter(s -> Boolean.TRUE.equals(s.getActive())).toList();
    }

    @Transactional
    public StaffMember create(StaffRequest req, User actor) {
        StaffMember s = StaffMember.builder().branchId(branchFor(req, actor)).build();
        apply(s, req);
        return staffRepository.save(s);
    }

    @Transactional
    public StaffMember update(Long id, StaffRequest req, User actor) {
        StaffMember s = find(id);
        checkScope(s, actor);
        s.setBranchId(actor.getRole() == Role.ADMIN && req.getBranchId() != null ? branchFor(req, actor) : s.getBranchId());
        apply(s, req);
        return staffRepository.save(s);
    }

    /** Nhân viên đang làm ở chi nhánh, dùng khi xếp ca và mở ca */
    public StaffMember activeIn(Long branchId, Long staffId) {
        StaffMember s = find(staffId);
        if (!s.getBranchId().equals(branchId)) {
            throw new BusinessException("STAFF_NOT_IN_BRANCH", s.getFullName() + " không thuộc chi nhánh này");
        }
        if (!Boolean.TRUE.equals(s.getActive())) {
            throw new BusinessException("STAFF_INACTIVE", s.getFullName() + " đã ngưng làm việc");
        }
        return s;
    }

    public StaffMember find(Long id) {
        return staffRepository.findById(id)
                .orElseThrow(() -> new BusinessException("STAFF_NOT_FOUND", "Không tìm thấy nhân viên", HttpStatus.NOT_FOUND));
    }

    private Long branchFor(StaffRequest req, User actor) {
        Long branchId = actor.getRole() == Role.ADMIN ? req.getBranchId() : actor.getCurrentBranchId();
        if (branchId == null || !branchRepository.existsById(branchId)) {
            throw new BusinessException("BRANCH_REQUIRED", "Chọn chi nhánh hợp lệ cho nhân viên");
        }
        return branchId;
    }

    private void checkScope(StaffMember s, User actor) {
        if (actor.getRole() == Role.ADMIN) return;
        if (actor.getRole() == Role.MANAGER && s.getBranchId().equals(actor.getCurrentBranchId())) return;
        throw new BusinessException("STAFF_FORBIDDEN", "Bạn không có quyền với nhân viên này", HttpStatus.FORBIDDEN);
    }

    private static void apply(StaffMember s, StaffRequest req) {
        s.setFullName(req.getFullName().trim());
        s.setPhone(req.getPhone() == null || req.getPhone().isBlank() ? null : req.getPhone().trim());
        s.setPosition(req.getPosition() == null || req.getPosition().isBlank() ? null : req.getPosition().trim());
        if (req.getActive() != null) {
            s.setActive(req.getActive());
        }
    }
}
