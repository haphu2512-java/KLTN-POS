package com.pos.support;

import com.pos.shift.ShiftAssignment;
import com.pos.shift.ShiftAssignmentRepository;
import com.pos.staff.StaffMember;
import com.pos.staff.StaffMemberRepository;

import java.time.LocalDate;

/** Tiện ích test: tạo nhân viên trực và xếp vào một ca hôm nay để tài khoản quầy mở được ca đó */
public final class ShiftTestData {

    private ShiftTestData() {
    }

    /** Tạo nhân viên mới ở chi nhánh và xếp (thay người cũ nếu có) vào ca {@code workShiftId} hôm nay */
    public static Long assignNewStaff(StaffMemberRepository staffRepository, ShiftAssignmentRepository assignmentRepository,
                                      Long branchId, long workShiftId, String name) {
        StaffMember staff = staffRepository.save(StaffMember.builder().branchId(branchId).fullName(name).active(true).build());
        ShiftAssignment slot = assignmentRepository.findByBranchIdAndWorkShiftIdAndShiftDate(branchId, workShiftId, LocalDate.now())
                .orElseGet(() -> ShiftAssignment.builder().branchId(branchId).workShiftId(workShiftId).shiftDate(LocalDate.now()).build());
        slot.setStaffMemberId(staff.getId());
        assignmentRepository.save(slot);
        return staff.getId();
    }
}
