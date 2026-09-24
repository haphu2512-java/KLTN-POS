package com.pos.report;

import com.pos.product.Product;
import com.pos.sale.Invoice;
import com.pos.sale.InvoiceDetail;
import com.pos.sale.InvoiceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @InjectMocks
    private ReportService reportService;

    @Test
    @DisplayName("Tính toán tổng doanh thu, lợi nhuận gộp và biên lợi nhuận chính xác")
    void testGetRevenueSummary() {
        Product prod1 = Product.builder().costPrice(BigDecimal.valueOf(20000)).build();
        InvoiceDetail d1 = InvoiceDetail.builder().product(prod1).quantity(2).build();

        Invoice inv1 = Invoice.builder()
                .totalAmount(BigDecimal.valueOf(60000)) // Revenue = 60,000, Cost = 2 * 20,000 = 40,000
                .details(List.of(d1))
                .createdAt(LocalDateTime.now())
                .build();

        when(invoiceRepository.findInvoicesByFilter(eq(1L), any(), any()))
                .thenReturn(List.of(inv1));

        RevenueSummaryResponse summary = reportService.getRevenueSummary(1L, 7);

        assertNotNull(summary);
        assertEquals(BigDecimal.valueOf(60000), summary.getTotalRevenue());
        assertEquals(BigDecimal.valueOf(40000), summary.getTotalCost());
        // Gross profit = 60000 - 40000 = 20000
        assertEquals(BigDecimal.valueOf(20000), summary.getGrossProfit());
        assertEquals(1, summary.getTotalOrders());
        // Margin: 20000 / 60000 = 33.3%
        assertEquals(33.3, summary.getProfitMarginPercent(), 0.1);
    }
}
