package com.pos.sale;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Invoice i WHERE i.id = :id")
    Optional<Invoice> findByIdForUpdate(@Param("id") Long id);

    Optional<Invoice> findByClientRequestId(String clientRequestId);

    List<Invoice> findByBranchIdOrderByCreatedAtDesc(Long branchId);

    Page<Invoice> findByBranchIdOrderByCreatedAtDesc(Long branchId, Pageable pageable);

    List<Invoice> findAllByOrderByCreatedAtDesc();

    Page<Invoice> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<Invoice> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Doanh số theo ngày của mọi sản phẩm tại 1 chi nhánh (bỏ hóa đơn đã hoàn trả), dùng cho dự báo.
     * Mỗi dòng: [productId, ngày, tổng số lượng].
     */
    @Query("SELECT d.product.id, CAST(i.createdAt AS LocalDate), SUM(d.quantity) " +
           "FROM InvoiceDetail d JOIN d.invoice i " +
           "WHERE i.branch.id = :branchId AND i.createdAt >= :since " +
           "AND i.status <> com.pos.sale.InvoiceStatus.REFUNDED " +
           "GROUP BY d.product.id, CAST(i.createdAt AS LocalDate)")
    List<Object[]> getBranchDailyProductSales(@Param("branchId") Long branchId, @Param("since") LocalDateTime since);

    @Query("SELECT FUNCTION('DATE', i.createdAt) as saleDate, SUM(i.totalAmount) as totalRevenue, COUNT(i.id) as orderCount " +
           "FROM Invoice i " +
           "WHERE i.createdAt >= :since " +
           "GROUP BY FUNCTION('DATE', i.createdAt) " +
           "ORDER BY FUNCTION('DATE', i.createdAt) ASC")
    List<Object[]> getDailyRevenue(@Param("since") LocalDateTime since);

    /**
     * Top sản phẩm bán chạy (bỏ hóa đơn đã hoàn trả).
     * Mỗi dòng: [productId, tên, mã vạch, tổng SL, doanh thu, giá vốn (giá vốn lúc bán, fallback giá vốn hiện tại)].
     */
    @Query("SELECT d.product.id, d.product.name, d.product.barcode, SUM(d.quantity), SUM(d.totalPrice), " +
           "SUM(d.quantity * COALESCE(d.costPrice, d.product.costPrice)) " +
           "FROM InvoiceDetail d JOIN d.invoice i " +
           "WHERE (:branchId IS NULL OR i.branch.id = :branchId) AND i.createdAt BETWEEN :start AND :end " +
           "AND i.status <> com.pos.sale.InvoiceStatus.REFUNDED " +
           "GROUP BY d.product.id, d.product.name, d.product.barcode " +
           "ORDER BY SUM(d.quantity) DESC")
    List<Object[]> getTopSellingProducts(@Param("branchId") Long branchId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** Hóa đơn đã thanh toán (bỏ hóa đơn hoàn trả) trong khoảng thời gian, dùng cho báo cáo doanh thu */
    @Query("SELECT DISTINCT i FROM Invoice i LEFT JOIN FETCH i.details d LEFT JOIN FETCH d.product " +
           "WHERE (:branchId IS NULL OR i.branch.id = :branchId) AND i.createdAt BETWEEN :start AND :end " +
           "AND i.status <> com.pos.sale.InvoiceStatus.REFUNDED ORDER BY i.createdAt DESC")
    List<Invoice> findInvoicesByFilter(@Param("branchId") Long branchId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * Doanh số bán ra trong một ca theo phương thức, tính cả hóa đơn sau đó bị hoàn trả
     * (tiền hoàn trả trừ riêng ở {@link #sumRefundedByShift}). Mỗi dòng: [phương thức, số hóa đơn, tổng tiền].
     */
    @Query("SELECT i.paymentMethod, COUNT(i), SUM(i.totalAmount) FROM Invoice i " +
           "WHERE i.shiftSessionId = :shiftId GROUP BY i.paymentMethod")
    List<Object[]> sumSoldByShift(@Param("shiftId") Long shiftId);

    /**
     * Tiền hoàn trả chi ra từ két của một ca, theo phương thức. Hóa đơn hoàn trả chưa ghi ca chi tiền
     * (dữ liệu cũ, hoặc lúc hoàn trả không có ca nào đang mở) được tính vào ca đã bán.
     * Mỗi dòng: [phương thức, số hóa đơn, tổng tiền].
     */
    @Query("SELECT i.paymentMethod, COUNT(i), SUM(i.totalAmount) FROM Invoice i " +
           "WHERE i.status = com.pos.sale.InvoiceStatus.REFUNDED AND (i.refundShiftSessionId = :shiftId " +
           "OR (i.refundShiftSessionId IS NULL AND i.shiftSessionId = :shiftId)) GROUP BY i.paymentMethod")
    List<Object[]> sumRefundedByShift(@Param("shiftId") Long shiftId);

    /** Hóa đơn bán trong ca và hóa đơn (của ca khác) được hoàn trả bằng tiền két của ca này */
    @Query("SELECT i FROM Invoice i WHERE i.shiftSessionId = :shiftId OR i.refundShiftSessionId = :shiftId " +
           "ORDER BY i.createdAt ASC")
    List<Invoice> findForShift(@Param("shiftId") Long shiftId);
}
