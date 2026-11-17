package com.pos.purchaseorder;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    @Query("SELECT o FROM PurchaseOrder o WHERE (:branchId IS NULL OR o.branchId = :branchId) ORDER BY o.createdAt DESC")
    List<PurchaseOrder> findRecent(@Param("branchId") Long branchId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM PurchaseOrder o WHERE o.id = :id")
    Optional<PurchaseOrder> findByIdForUpdate(@Param("id") Long id);
}
