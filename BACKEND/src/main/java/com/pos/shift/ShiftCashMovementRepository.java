package com.pos.shift;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShiftCashMovementRepository extends JpaRepository<ShiftCashMovement, Long> {

    /** Cả khoản đã xóa (để xem vết), mới nhất trước */
    List<ShiftCashMovement> findByShiftSessionIdOrderByCreatedAtDesc(Long shiftSessionId);

    /** Tổng tiền các khoản còn hiệu lực theo loại. Mỗi dòng: [loại, tổng tiền] */
    @Query("SELECT m.type, SUM(m.amount) FROM ShiftCashMovement m " +
           "WHERE m.shiftSessionId = :shiftId AND m.deletedAt IS NULL GROUP BY m.type")
    List<Object[]> sumByType(@Param("shiftId") Long shiftId);
}
