package com.pos.stockcount;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface StockCountRepository extends JpaRepository<StockCount, Long> {

    @Query("SELECT c FROM StockCount c WHERE (:branchId IS NULL OR c.branchId = :branchId) ORDER BY c.createdAt DESC")
    List<StockCount> findRecent(@Param("branchId") Long branchId, org.springframework.data.domain.Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM StockCount c WHERE c.id = :id")
    Optional<StockCount> findByIdForUpdate(@Param("id") Long id);

    /**
     * Hao hụt / dư theo lý do trong kỳ, chỉ phiếu đã duyệt. Mỗi dòng: [lý do, tổng chênh lệch SL, tổng giá trị].
     */
    @Query("SELECT l.reason, SUM(l.countedQty - l.systemQty), SUM((l.countedQty - l.systemQty) * COALESCE(l.unitCost, 0)) "
            + "FROM StockCountLine l JOIN l.stockCount c "
            + "WHERE c.status = com.pos.stockcount.StockCountStatus.APPROVED AND c.approvedAt >= :from AND c.approvedAt < :to "
            + "AND (:branchId IS NULL OR c.branchId = :branchId) AND l.countedQty <> l.systemQty "
            + "GROUP BY l.reason")
    List<Object[]> shrinkageByReason(@Param("branchId") Long branchId, @Param("from") LocalDateTime from,
                                     @Param("to") LocalDateTime to);
}
