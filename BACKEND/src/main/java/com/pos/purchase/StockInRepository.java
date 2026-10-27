package com.pos.purchase;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockInRepository extends JpaRepository<StockIn, Long> {
    List<StockIn> findByBranchIdOrderByCreatedAtDesc(Long branchId);
    List<StockIn> findAllByOrderByCreatedAtDesc();
    Optional<StockIn> findByClientRequestId(String clientRequestId);
}
