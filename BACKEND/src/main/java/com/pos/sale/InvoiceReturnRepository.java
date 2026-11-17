package com.pos.sale;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InvoiceReturnRepository extends JpaRepository<InvoiceReturn, Long> {

    List<InvoiceReturn> findByInvoiceIdOrderByCreatedAtAsc(Long invoiceId);

    /**
     * Tiền hoàn chi ra từ két của một ca theo phương thức. Phiếu chưa ghi ca (lúc hoàn không có ca nào mở)
     * tính vào ca đã bán hóa đơn. Mỗi dòng: [phương thức, số phiếu, tổng tiền].
     */
    @Query("SELECT r.paymentMethod, COUNT(r), SUM(r.refundAmount) FROM InvoiceReturn r "
            + "WHERE r.shiftSessionId = :shiftId OR (r.shiftSessionId IS NULL AND r.invoice.shiftSessionId = :shiftId) "
            + "GROUP BY r.paymentMethod")
    List<Object[]> sumByShift(@Param("shiftId") Long shiftId);

    /** Phiếu hoàn trả tính vào két của ca (cùng quy tắc với {@link #sumByShift}) */
    @Query("SELECT r FROM InvoiceReturn r "
            + "WHERE r.shiftSessionId = :shiftId OR (r.shiftSessionId IS NULL AND r.invoice.shiftSessionId = :shiftId)")
    List<InvoiceReturn> findByShift(@Param("shiftId") Long shiftId);
}
