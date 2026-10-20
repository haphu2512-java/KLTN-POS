package com.pos.sale;

import com.pos.branch.Branch;
import com.pos.product.Product;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chạy các truy vấn JPQL thật trên H2 để chắc chắn cú pháp và điều kiện lọc đúng.
 */
@DataJpaTest(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.sql.init.mode=never"
})
class InvoiceRepositoryTest {

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private EntityManager em;

    private Branch branch1;
    private Branch branch2;
    private Product product;

    @BeforeEach
    void setUp() {
        branch1 = persist(Branch.builder().name("CN 1").active(true).build());
        branch2 = persist(Branch.builder().name("CN 2").active(true).build());
        product = persist(Product.builder().barcode("893000000001").name("Sữa").unit("Hộp")
                .costPrice(BigDecimal.valueOf(30000)).salePrice(BigDecimal.valueOf(36000)).active(true).build());
    }

    private <T> T persist(T entity) {
        em.persist(entity);
        return entity;
    }

    private void sale(Branch branch, int qty, InvoiceStatus status, BigDecimal costAtSale) {
        Invoice invoice = Invoice.builder().branch(branch).paymentMethod(PaymentMethod.CASH)
                .subtotal(BigDecimal.valueOf(36000L * qty)).totalAmount(BigDecimal.valueOf(36000L * qty))
                .status(status).createdAt(LocalDateTime.now()).build();
        invoice.getDetails().add(InvoiceDetail.builder().invoice(invoice).product(product).quantity(qty)
                .unitPrice(BigDecimal.valueOf(36000)).costPrice(costAtSale)
                .totalPrice(BigDecimal.valueOf(36000L * qty)).build());
        em.persist(invoice);
    }

    @Test
    @DisplayName("Doanh số dự báo chỉ tính đúng chi nhánh và bỏ hóa đơn hoàn trả")
    void branchDailySalesFiltersBranchAndRefunds() {
        sale(branch1, 2, InvoiceStatus.PAID, null);
        sale(branch1, 5, InvoiceStatus.REFUNDED, null);
        sale(branch2, 9, InvoiceStatus.PAID, null);
        em.flush();

        List<Object[]> rows = invoiceRepository.getBranchDailyProductSales(branch1.getId(), LocalDateTime.now().minusDays(1));

        assertEquals(1, rows.size());
        assertEquals(product.getId(), ((Number) rows.get(0)[0]).longValue());
        assertNotNull(rows.get(0)[1]);
        assertEquals(2L, ((Number) rows.get(0)[2]).longValue());
    }

    @Test
    @DisplayName("Top sản phẩm bỏ hóa đơn hoàn trả và dùng giá vốn lúc bán")
    void topProductsUseCostAtSale() {
        sale(branch1, 2, InvoiceStatus.PAID, BigDecimal.valueOf(20000));
        sale(branch1, 1, InvoiceStatus.PAID, null); // hóa đơn cũ chưa lưu giá vốn → dùng giá vốn hiện tại
        sale(branch1, 4, InvoiceStatus.REFUNDED, BigDecimal.valueOf(20000));
        em.flush();

        List<Object[]> rows = invoiceRepository.getTopSellingProducts(branch1.getId(),
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusMinutes(1));

        assertEquals(1, rows.size());
        assertEquals(3L, ((Number) rows.get(0)[3]).longValue());
        assertEquals(0, new BigDecimal("70000").compareTo(new BigDecimal(rows.get(0)[5].toString())));
    }

    @Test
    @DisplayName("Báo cáo doanh thu bỏ hóa đơn hoàn trả")
    void revenueFilterExcludesRefunds() {
        sale(branch1, 1, InvoiceStatus.PAID, null);
        sale(branch1, 1, InvoiceStatus.REFUNDED, null);
        em.flush();

        List<Invoice> invoices = invoiceRepository.findInvoicesByFilter(branch1.getId(),
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusMinutes(1));

        assertEquals(1, invoices.size());
        assertEquals(InvoiceStatus.PAID, invoices.get(0).getStatus());
    }
}
