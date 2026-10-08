package com.pos.sale;

import com.pos.branch.Branch;
import com.pos.branch.BranchRepository;
import com.pos.common.BusinessException;
import com.pos.customer.Customer;
import com.pos.customer.CustomerRepository;
import com.pos.inventory.InventoryService;
import com.pos.product.Product;
import com.pos.product.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private InventoryService inventoryService;

    @InjectMocks
    private InvoiceService invoiceService;

    @Test
    @DisplayName("Tạo hóa đơn thành công: tính đúng tổng tiền, trừ kho nguyên tử và tích điểm khách hàng")
    void testCreateInvoiceSuccess() {
        Branch branch = Branch.builder().id(1L).name("Chi nhánh 1").build();
        Customer customer = Customer.builder().id(1L).name("Anh Nam").points(10).build();
        Product product = Product.builder()
                .id(1L)
                .name("Sữa tươi Vinamilk")
                .salePrice(BigDecimal.valueOf(36000))
                .build();

        when(branchRepository.findById(1L)).thenReturn(Optional.of(branch));
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        doNothing().when(inventoryService).deductStockAtomic(1L, 1L, 2);

        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InvoiceRequest.Item item = new InvoiceRequest.Item();
        item.setProductId(1L);
        item.setQuantity(2);
        item.setUnitPrice(BigDecimal.valueOf(36000));

        InvoiceRequest req = new InvoiceRequest();
        req.setBranchId(1L);
        req.setCustomerId(1L);
        req.setEmployeeId(1L);
        req.setDiscount(BigDecimal.valueOf(2000));
        req.setPaymentMethod(PaymentMethod.CASH);
        req.setReceivedAmount(BigDecimal.valueOf(100000));
        req.setItems(List.of(item));

        Invoice invoice = invoiceService.createInvoice(req);

        assertNotNull(invoice);
        // Subtotal = 36000 * 2 = 72000. Total = 72000 - 2000 = 70000.
        assertEquals(BigDecimal.valueOf(72000), invoice.getSubtotal());
        assertEquals(BigDecimal.valueOf(70000), invoice.getTotalAmount());
        // Change = 100000 - 70000 = 30000
        assertEquals(BigDecimal.valueOf(30000), invoice.getChangeAmount());

        // Verify atomic deduction called for 2 items
        verify(inventoryService).deductStockAtomic(1L, 1L, 2);
        // Verify atomic customer points updated (+7 points for 70,000 VND)
        verify(customerRepository).addPoints(1L, 7);
    }

    @Test
    @DisplayName("Ném BusinessException khi chi nhánh không tồn tại")
    void testCreateInvoiceBranchNotFound() {
        when(branchRepository.findById(99L)).thenReturn(Optional.empty());

        InvoiceRequest req = new InvoiceRequest();
        req.setBranchId(99L);
        req.setItems(List.of());

        assertThrows(BusinessException.class, () -> invoiceService.createInvoice(req));
    }

    @Test
    @DisplayName("Hoàn trả hàng thành công: hoàn kho nguyên tử, thu hồi điểm và đổi trạng thái REFUNDED")
    void testRefundInvoiceSuccess() {
        Branch branch = Branch.builder().id(1L).name("Chi nhánh 1").build();
        Customer customer = Customer.builder().id(1L).name("Anh Nam").points(20).build();
        Product product = Product.builder().id(1L).name("Sữa tươi").build();

        InvoiceDetail detail = InvoiceDetail.builder()
                .product(product)
                .quantity(3)
                .build();

        Invoice invoice = Invoice.builder()
                .id(10L)
                .branch(branch)
                .customer(customer)
                .status(InvoiceStatus.PAID)
                .totalAmount(BigDecimal.valueOf(50000))
                .details(List.of(detail))
                .build();

        when(invoiceRepository.findById(10L)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        Invoice refunded = invoiceService.refundInvoice(10L, "Khách đổi ý");

        assertEquals(InvoiceStatus.REFUNDED, refunded.getStatus());
        assertTrue(refunded.getNote().contains("Khách đổi ý"));
        verify(inventoryService).addStockAtomic(1L, 1L, 3);
        verify(customerRepository).addPoints(1L, -5); // 50,000 VND = 5 points
    }

    @Test
    @DisplayName("Ném lỗi khi hoàn trả hóa đơn đã được hoàn trả trước đó")
    void testRefundAlreadyRefunded() {
        Invoice invoice = Invoice.builder()
                .id(11L)
                .status(InvoiceStatus.REFUNDED)
                .build();

        when(invoiceRepository.findById(11L)).thenReturn(Optional.of(invoice));

        assertThrows(BusinessException.class, () -> invoiceService.refundInvoice(11L, "Lỗi"));
    }
}
