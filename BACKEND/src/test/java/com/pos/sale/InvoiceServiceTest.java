package com.pos.sale;

import com.pos.branch.Branch;
import com.pos.branch.BranchRepository;
import com.pos.common.BusinessException;
import com.pos.customer.Customer;
import com.pos.customer.CustomerRepository;
import com.pos.inventory.InventoryService;
import com.pos.product.Product;
import com.pos.product.ProductRepository;
import com.pos.user.Role;
import com.pos.user.User;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
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

    private final User cashier = User.builder().id(7L).username("cashier").role(Role.CASHIER).branchId(1L).build();
    private final User manager = User.builder().id(8L).username("manager").role(Role.MANAGER).branchId(1L).build();

    private Product milk() {
        return Product.builder().id(1L).name("Sữa tươi Vinamilk")
                .costPrice(BigDecimal.valueOf(28000)).salePrice(BigDecimal.valueOf(36000)).build();
    }

    private InvoiceRequest cashRequest(BigDecimal unitPrice, BigDecimal discount, BigDecimal received) {
        InvoiceRequest.Item item = new InvoiceRequest.Item();
        item.setProductId(1L);
        item.setQuantity(2);
        item.setUnitPrice(unitPrice);

        InvoiceRequest req = new InvoiceRequest();
        req.setBranchId(1L);
        req.setDiscount(discount);
        req.setPaymentMethod(PaymentMethod.CASH);
        req.setReceivedAmount(received);
        req.setItems(List.of(item));
        return req;
    }

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

        Invoice invoice = invoiceService.createInvoice(req, cashier);

        assertNotNull(invoice);
        // Nhân viên lấy từ tài khoản đăng nhập, không lấy employeeId client gửi lên
        assertEquals(7L, invoice.getEmployeeId());
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

        User admin = User.builder().id(1L).role(Role.ADMIN).build();
        assertThrows(BusinessException.class, () -> invoiceService.createInvoice(req, admin));
    }

    @Test
    @DisplayName("Thu ngân không được bán khác giá niêm yết")
    void testCashierCannotOverridePrice() {
        when(branchRepository.findById(1L)).thenReturn(Optional.of(Branch.builder().id(1L).build()));
        when(productRepository.findById(1L)).thenReturn(Optional.of(milk()));

        InvoiceRequest req = cashRequest(BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.valueOf(100000));

        BusinessException ex = assertThrows(BusinessException.class, () -> invoiceService.createInvoice(req, cashier));
        assertEquals("PRICE_OVERRIDE_FORBIDDEN", ex.getCode());
        verify(inventoryService, never()).deductStockAtomic(anyLong(), anyLong(), anyInt());
    }

    @Test
    @DisplayName("Quản lý được phép bán khác giá niêm yết")
    void testManagerCanOverridePrice() {
        when(branchRepository.findById(1L)).thenReturn(Optional.of(Branch.builder().id(1L).build()));
        when(productRepository.findById(1L)).thenReturn(Optional.of(milk()));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Invoice invoice = invoiceService.createInvoice(
                cashRequest(BigDecimal.valueOf(30000), BigDecimal.ZERO, BigDecimal.valueOf(60000)), manager);

        assertEquals(BigDecimal.valueOf(60000), invoice.getTotalAmount());
        // Giá vốn lúc bán được lưu lại cho báo cáo lợi nhuận
        assertEquals(BigDecimal.valueOf(28000), invoice.getDetails().get(0).getCostPrice());
    }

    @Test
    @DisplayName("Không gửi giá thì dùng giá niêm yết")
    void testMissingPriceUsesListPrice() {
        when(branchRepository.findById(1L)).thenReturn(Optional.of(Branch.builder().id(1L).build()));
        when(productRepository.findById(1L)).thenReturn(Optional.of(milk()));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Invoice invoice = invoiceService.createInvoice(cashRequest(null, null, null), cashier);

        assertEquals(BigDecimal.valueOf(72000), invoice.getTotalAmount());
    }

    @Test
    @DisplayName("Giảm giá âm hoặc lớn hơn tổng tiền bị từ chối")
    void testInvalidDiscountRejected() {
        when(branchRepository.findById(1L)).thenReturn(Optional.of(Branch.builder().id(1L).build()));

        BusinessException negative = assertThrows(BusinessException.class, () -> invoiceService.createInvoice(
                cashRequest(null, BigDecimal.valueOf(-1000), BigDecimal.valueOf(100000)), cashier));
        assertEquals("INVALID_DISCOUNT", negative.getCode());

        when(productRepository.findById(1L)).thenReturn(Optional.of(milk()));
        BusinessException tooLarge = assertThrows(BusinessException.class, () -> invoiceService.createInvoice(
                cashRequest(null, BigDecimal.valueOf(80000), BigDecimal.valueOf(100000)), cashier));
        assertEquals("INVALID_DISCOUNT", tooLarge.getCode());
    }

    @Test
    @DisplayName("Tiền mặt khách đưa thiếu bị từ chối")
    void testCashUnderpaymentRejected() {
        when(branchRepository.findById(1L)).thenReturn(Optional.of(Branch.builder().id(1L).build()));
        when(productRepository.findById(1L)).thenReturn(Optional.of(milk()));

        BusinessException ex = assertThrows(BusinessException.class, () -> invoiceService.createInvoice(
                cashRequest(null, BigDecimal.ZERO, BigDecimal.valueOf(50000)), cashier));
        assertEquals("INSUFFICIENT_PAYMENT", ex.getCode());
    }

    @Test
    @DisplayName("Thu ngân không được bán cho chi nhánh khác")
    void testCashierCannotSellForOtherBranch() {
        InvoiceRequest req = cashRequest(null, BigDecimal.ZERO, BigDecimal.valueOf(100000));
        req.setBranchId(2L);

        BusinessException ex = assertThrows(BusinessException.class, () -> invoiceService.createInvoice(req, cashier));
        assertEquals("BRANCH_FORBIDDEN", ex.getCode());
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

        when(invoiceRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(invoice));
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

        when(invoiceRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(invoice));

        assertThrows(BusinessException.class, () -> invoiceService.refundInvoice(11L, "Lỗi"));
    }

    @Test
    @DisplayName("Gửi lại cùng clientRequestId: trả hóa đơn cũ, không trừ kho lần nữa")
    void testRetryWithSameClientRequestIdReturnsExistingInvoice() {
        Invoice existing = Invoice.builder().id(55L).employeeId(cashier.getId()).clientRequestId("req-1").build();
        when(invoiceRepository.findByClientRequestId("req-1")).thenReturn(Optional.of(existing));

        InvoiceRequest req = cashRequest(null, null, null);
        req.setClientRequestId("req-1");

        assertSame(existing, invoiceService.createInvoice(req, cashier));
        verify(inventoryService, never()).deductStockAtomic(anyLong(), anyLong(), anyInt());
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    @DisplayName("clientRequestId của nhân viên khác: từ chối, không trả hóa đơn của người khác")
    void testClientRequestIdOfAnotherEmployeeIsRejected() {
        Invoice existing = Invoice.builder().id(55L).employeeId(manager.getId()).clientRequestId("req-2").build();
        when(invoiceRepository.findByClientRequestId("req-2")).thenReturn(Optional.of(existing));

        InvoiceRequest req = cashRequest(null, null, null);
        req.setClientRequestId("req-2");

        BusinessException ex = assertThrows(BusinessException.class, () -> invoiceService.createInvoice(req, cashier));
        assertEquals("DUPLICATE_REQUEST", ex.getCode());
        verify(inventoryService, never()).deductStockAtomic(anyLong(), anyLong(), anyInt());
    }

    @Test
    @DisplayName("Đơn mới có clientRequestId: lưu mã vào hóa đơn")
    void testNewInvoiceStoresClientRequestId() {
        when(invoiceRepository.findByClientRequestId("req-3")).thenReturn(Optional.empty());
        when(branchRepository.findById(1L)).thenReturn(Optional.of(Branch.builder().id(1L).build()));
        when(productRepository.findById(1L)).thenReturn(Optional.of(milk()));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        InvoiceRequest req = cashRequest(null, null, null);
        req.setClientRequestId(" req-3 ");

        Invoice saved = invoiceService.createInvoice(req, cashier);
        assertEquals("req-3", saved.getClientRequestId());
        verify(inventoryService).deductStockAtomic(1L, 1L, 2);
    }
}
