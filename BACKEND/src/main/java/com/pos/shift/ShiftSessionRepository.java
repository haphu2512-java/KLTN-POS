package com.pos.shift;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ShiftSessionRepository extends JpaRepository<ShiftSession, Long> {

    Optional<ShiftSession> findByEmployeeIdAndStatus(Long employeeId, ShiftStatus status);

    /** Ca vừa đóng gần nhất tại chi nhánh, để ca sau nhận tiền và ghi chú bàn giao */
    Optional<ShiftSession> findFirstByBranchIdAndStatusOrderByClosedAtDesc(Long branchId, ShiftStatus status);

    List<ShiftSession> findByBranchIdAndShiftDateBetweenOrderByOpenedAtDesc(Long branchId, LocalDate from, LocalDate to);

    List<ShiftSession> findByShiftDateBetweenOrderByOpenedAtDesc(LocalDate from, LocalDate to);
}
