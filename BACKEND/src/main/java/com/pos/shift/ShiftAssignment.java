package com.pos.shift;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Quản lý xếp một nhân viên (bảng staff_member) vào một ca của một ngày tại một chi nhánh. Khi ca đó được mở
 * (từ tài khoản quầy dùng chung), ca thuộc về nhân viên được xếp.
 */
@Entity
@Table(name = "shift_assignment",
        uniqueConstraints = @UniqueConstraint(name = "uk_assignment_slot", columnNames = {"branch_id", "work_shift_id", "shift_date"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShiftAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "work_shift_id", nullable = false)
    private Long workShiftId;

    @Column(name = "shift_date", nullable = false)
    private LocalDate shiftDate;

    @Column(name = "staff_member_id", nullable = false)
    private Long staffMemberId;

    @Column(name = "assigned_by")
    private Long assignedBy;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;
}
