package com.pos.shift;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ShiftAssignmentRepository extends JpaRepository<ShiftAssignment, Long> {

    Optional<ShiftAssignment> findByBranchIdAndWorkShiftIdAndShiftDate(Long branchId, Long workShiftId, LocalDate shiftDate);

    List<ShiftAssignment> findByBranchIdAndShiftDateBetweenOrderByShiftDateAsc(Long branchId, LocalDate from, LocalDate to);
}
