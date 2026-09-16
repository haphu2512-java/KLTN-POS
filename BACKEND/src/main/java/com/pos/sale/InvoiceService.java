package com.pos.sale;

import com.pos.branch.Branch;
import com.pos.branch.BranchRepository;
import com.pos.common.BusinessException;
import com.pos.customer.Customer;
import com.pos.customer.CustomerRepository;
import com.pos.inventory.InventoryService;
import com.pos.product.Product;
import com.pos.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final BranchRepository branchRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;

    public List<Invoice> getAllInvoices(Long branchId) {
        if (branchId != null) {
            return invoiceRepository.findByBranchIdOrderByCreatedAtDesc(branchId);
        }
        return invoiceRepository.findAllByOrderByCreatedAtDesc();
    }

    public Invoice getInvoiceById(Long id) {
        return invoiceRepository.findById(id)
                .orElseThrow(() -> new BusinessException("INVOICE_NOT_FOUND", "Không tìm thấy hóa đơn với ID: " + id));
    }

    @Transactional
    public Invoice createInvoice(InvoiceRequest req) {
        Branch branch = branchRepository.findById(req.getBranchId())
                .orElseThrow(() -> new BusinessException("BRANCH_NOT_FOUND", "Chi nhánh không tồn tại"));

        Customer customer = null;
        if (req.getCustomerId() != null) {
            customer = customerRepository.findById(req.getCustomerId()).orElse(null);
        }

        Invoice invoice = Invoice.builder()
                .branch(branch)
                .customer(customer)
                .employeeId(req.getEmployeeId())
                .paymentMethod(req.getPaymentMethod())
                .discount(req.getDiscount() != null ? req.getDiscount() : BigDecimal.ZERO)
                .note(req.getNote())
                .details(new ArrayList<>())
                .build();

        BigDecimal subtotal = BigDecimal.ZERO;

        // 1. Process each item & Deduct Inventory atomically
        for (InvoiceRequest.Item itemReq : req.getItems()) {
            Product product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Sản phẩm không tồn tại: " + itemReq.getProductId()));

            BigDecimal unitPrice = itemReq.getUnitPrice() != null ? itemReq.getUnitPrice() : product.getSalePrice();
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(itemReq.getQuantity()));

            // ATOMIC STOCK DEDUCTION WITH CONCURRENCY CHECK
            inventoryService.deductStockAtomic(product.getId(), branch.getId(), itemReq.getQuantity());

            InvoiceDetail detail = InvoiceDetail.builder()
                    .invoice(invoice)
                    .product(product)
                    .quantity(itemReq.getQuantity())
                    .unitPrice(unitPrice)
                    .totalPrice(lineTotal)
                    .build();

            invoice.getDetails().add(detail);
            subtotal = subtotal.add(lineTotal);
        }

        invoice.setSubtotal(subtotal);
        BigDecimal discount = invoice.getDiscount();
        BigDecimal totalAmount = subtotal.subtract(discount);
        if (totalAmount.compareTo(BigDecimal.ZERO) < 0) {
            totalAmount = BigDecimal.ZERO;
        }
        invoice.setTotalAmount(totalAmount);

        // 2. Handle Cash Tendered & Change
        BigDecimal received = req.getReceivedAmount() != null ? req.getReceivedAmount() : totalAmount;
        invoice.setReceivedAmount(received);
        if (req.getPaymentMethod() == PaymentMethod.CASH) {
            invoice.setChangeAmount(received.subtract(totalAmount));
        } else {
            invoice.setChangeAmount(BigDecimal.ZERO);
        }

        // 3. Award Customer Points (1 point per 10,000 VND)
        if (customer != null && totalAmount.compareTo(BigDecimal.ZERO) > 0) {
            int earnedPoints = totalAmount.divide(BigDecimal.valueOf(10000), java.math.RoundingMode.FLOOR).intValue();
            customer.setPoints(customer.getPoints() + earnedPoints);
            customerRepository.save(customer);
        }

        return invoiceRepository.save(invoice);
    }
}
