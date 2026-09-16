package com.pos.sale;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    List<Invoice> findByBranchIdOrderByCreatedAtDesc(Long branchId);

    List<Invoice> findAllByOrderByCreatedAtDesc();

    List<Invoice> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    @Query("SELECT FUNCTION('DATE', i.createdAt) as saleDate, SUM(d.quantity) as totalQty " +
           "FROM InvoiceDetail d JOIN d.invoice i " +
           "WHERE d.product.id = :productId AND i.createdAt >= :since " +
           "GROUP BY FUNCTION('DATE', i.createdAt) " +
           "ORDER BY FUNCTION('DATE', i.createdAt) ASC")
    List<Object[]> getDailyProductSales(@Param("productId") Long productId, @Param("since") LocalDateTime since);

    @Query("SELECT FUNCTION('DATE', i.createdAt) as saleDate, SUM(i.totalAmount) as totalRevenue, COUNT(i.id) as orderCount " +
           "FROM Invoice i " +
           "WHERE i.createdAt >= :since " +
           "GROUP BY FUNCTION('DATE', i.createdAt) " +
           "ORDER BY FUNCTION('DATE', i.createdAt) ASC")
    List<Object[]> getDailyRevenue(@Param("since") LocalDateTime since);
}
