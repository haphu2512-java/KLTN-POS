package com.pos.shift;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ShiftSessionRepository extends JpaRepository<ShiftSession, Long> {

    Optional<ShiftSession> findByEmployeeIdAndStatus(Long employeeId, ShiftStatus status);

    /** Ca vừa đóng gần nhất tại chi nhánh, để ca sau nhận tiền và ghi chú bàn giao */
    Optional<ShiftSession> findFirstByBranchIdAndStatusOrderByClosedAtDesc(Long branchId, ShiftStatus status);

    List<ShiftSession> findByBranchIdAndStatus(Long branchId, ShiftStatus status);

    /** Lọc ca theo khoảng ngày; chi nhánh, nhân viên, trạng thái để trống là không lọc */
    @Query("SELECT s FROM ShiftSession s WHERE s.shiftDate BETWEEN :from AND :to " +
           "AND (:branchId IS NULL OR s.branchId = :branchId) " +
           "AND (:employeeId IS NULL OR s.employeeId = :employeeId) " +
           "AND (:status IS NULL OR s.status = :status) ORDER BY s.openedAt DESC")
    List<ShiftSession> search(@Param("from") LocalDate from, @Param("to") LocalDate to,
                              @Param("branchId") Long branchId, @Param("employeeId") Long employeeId,
                              @Param("status") ShiftStatus status);
}
