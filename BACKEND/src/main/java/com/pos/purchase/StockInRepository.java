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

    /**
     * Nhà cung cấp của các lần nhập gần đây theo sản phẩm (mới nhất trước), để gợi ý NCC khi lập đơn đặt hàng.
     * Mỗi dòng: [productId, supplierId, supplierName].
     */
    @org.springframework.data.jpa.repository.Query("SELECT d.product.id, s.supplier.id, s.supplier.name FROM StockInDetail d JOIN d.stockIn s "
            + "WHERE s.supplier IS NOT NULL AND s.branch.id = :branchId ORDER BY s.createdAt DESC, s.id DESC")
    List<Object[]> recentSuppliersByProduct(@org.springframework.data.repository.query.Param("branchId") Long branchId);

    /**
     * Các lô có hạn sử dụng đã nhập vào chi nhánh. Mỗi dòng:
     * [productId, productName, barcode, lotNumber, expiryDate, quantity, unitCost, stockInId, receivedAt].
     */
    @org.springframework.data.jpa.repository.Query("SELECT d.product.id, d.product.name, d.product.barcode, d.lotNumber, d.expiryDate, "
            + "d.quantity, d.unitCost, s.id, s.createdAt FROM StockInDetail d JOIN d.stockIn s "
            + "WHERE s.branch.id = :branchId AND d.expiryDate IS NOT NULL")
    List<Object[]> lotsWithExpiry(@org.springframework.data.repository.query.Param("branchId") Long branchId);
}
