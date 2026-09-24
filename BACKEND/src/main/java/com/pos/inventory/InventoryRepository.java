package com.pos.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByProductIdAndBranchId(Long productId, Long branchId);

    List<Inventory> findByBranchId(Long branchId);

    @Query("SELECT i FROM Inventory i WHERE i.branch.id = :branchId AND i.quantity <= i.minThreshold")
    List<Inventory> findLowStockByBranchId(@Param("branchId") Long branchId);

    /**
     * Atomic safe deduction of inventory quantity
     * Returns 1 if successful, 0 if insufficient stock or item not found
     */
    @Modifying
    @Query("UPDATE Inventory i SET i.quantity = i.quantity - :qty " +
           "WHERE i.product.id = :productId AND i.branch.id = :branchId AND i.quantity >= :qty")
    int decrementStockAtomic(@Param("productId") Long productId,
                             @Param("branchId") Long branchId,
                             @Param("qty") int qty);

    /**
     * Atomic increment of inventory quantity
     */
    @Modifying
    @Query("UPDATE Inventory i SET i.quantity = i.quantity + :qty " +
           "WHERE i.product.id = :productId AND i.branch.id = :branchId")
    int incrementStockAtomic(@Param("productId") Long productId,
                             @Param("branchId") Long branchId,
                             @Param("qty") int qty);
}
