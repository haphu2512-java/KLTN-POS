package com.pos.shift;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ShiftSessionRepository extends JpaRepository<ShiftSession, Long> {

    Optional<ShiftSession> findByEmployeeIdAndStatus(Long employeeId, ShiftStatus status);

    /** Ca vừa đóng gần nhất tại chi nhánh (đã khóa sổ hoặc chờ duyệt), để ca sau nhận két và ghi chú bàn giao */
    Optional<ShiftSession> findFirstByBranchIdAndStatusInOrderByClosedAtDesc(Long branchId, Collection<ShiftStatus> statuses);

    /** Các ca của chi nhánh trong khoảng ngày ca, để gợi ý ca chưa trực */
    List<ShiftSession> findByBranchIdAndShiftDateBetween(Long branchId, LocalDate from, LocalDate to);

    boolean existsByWorkShiftId(Long workShiftId);

    List<ShiftSession> findByBranchIdAndStatus(Long branchId, ShiftStatus status);

    /** Lọc ca theo khoảng ngày; chi nhánh, nhân viên, trạng thái để trống là không lọc */
    @Query("SELECT s FROM ShiftSession s WHERE s.shiftDate BETWEEN :from AND :to " +
           "AND (:branchId IS NULL OR s.branchId = :branchId) " +
           "AND (:staffId IS NULL OR s.staffMemberId = :staffId) " +
           "AND (:status IS NULL OR s.status = :status) ORDER BY s.openedAt DESC")
    List<ShiftSession> search(@Param("from") LocalDate from, @Param("to") LocalDate to,
                              @Param("branchId") Long branchId, @Param("staffId") Long staffId,
                              @Param("status") ShiftStatus status);
}
