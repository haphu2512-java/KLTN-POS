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
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final BranchRepository branchRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;

    public Page<Invoice> getInvoices(Long branchId, Pageable pageable) {
        if (branchId != null) {
            return invoiceRepository.findByBranchIdOrderByCreatedAtDesc(branchId, pageable);
        }
        return invoiceRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

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

    /**
     * Tạo hóa đơn bán hàng.
     * Giá bán, nhân viên và chi nhánh do server quyết định dựa trên tài khoản đang đăng nhập (actor),
     * không tin giá trị client gửi lên.
     */
    @Transactional
    public Invoice createInvoice(InvoiceRequest req, User actor) {
        String clientRequestId = req.getClientRequestId() != null && !req.getClientRequestId().isBlank()
                ? req.getClientRequestId().trim() : null;
        if (clientRequestId != null) {
            // POS gửi lại đơn sau khi mất phản hồi: trả hóa đơn đã tạo, không trừ kho lần nữa
            Invoice existing = invoiceRepository.findByClientRequestId(clientRequestId).orElse(null);
            if (existing != null) {
                if (!actor.getId().equals(existing.getEmployeeId())) {
                    throw new BusinessException("DUPLICATE_REQUEST",
                            "Mã yêu cầu đã được dùng cho hóa đơn khác", HttpStatus.CONFLICT);
                }
                return existing;
            }
        }

        Long branchId = resolveBranchId(req.getBranchId(), actor);
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new BusinessException("BRANCH_NOT_FOUND", "Chi nhánh không tồn tại"));

        BigDecimal discount = req.getDiscount() != null ? req.getDiscount() : BigDecimal.ZERO;
        if (discount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("INVALID_DISCOUNT", "Giảm giá không được âm");
        }

        Customer customer = null;
        if (req.getCustomerId() != null) {
            customer = customerRepository.findById(req.getCustomerId()).orElse(null);
        }

        Invoice invoice = Invoice.builder()
                .branch(branch)
                .customer(customer)
                .employeeId(actor.getId())
                .paymentMethod(req.getPaymentMethod())
                .discount(discount)
                .note(req.getNote())
                .clientRequestId(clientRequestId)
                .details(new ArrayList<>())
                .build();

        BigDecimal subtotal = BigDecimal.ZERO;

        // 1. Process each item & Deduct Inventory atomically
        for (InvoiceRequest.Item itemReq : req.getItems()) {
            Product product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Sản phẩm không tồn tại: " + itemReq.getProductId()));

            BigDecimal unitPrice = resolveUnitPrice(product, itemReq.getUnitPrice(), actor);
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(itemReq.getQuantity()));

            // ATOMIC STOCK DEDUCTION WITH CONCURRENCY CHECK
            inventoryService.deductStockAtomic(product.getId(), branch.getId(), itemReq.getQuantity());

            InvoiceDetail detail = InvoiceDetail.builder()
                    .invoice(invoice)
                    .product(product)
                    .quantity(itemReq.getQuantity())
                    .unitPrice(unitPrice)
                    .costPrice(product.getCostPrice())
                    .totalPrice(lineTotal)
                    .build();

            invoice.getDetails().add(detail);
            subtotal = subtotal.add(lineTotal);
        }

        invoice.setSubtotal(subtotal);
        if (discount.compareTo(subtotal) > 0) {
            throw new BusinessException("INVALID_DISCOUNT", "Giảm giá không được lớn hơn tổng tiền hàng");
        }
        BigDecimal totalAmount = subtotal.subtract(discount);
        invoice.setTotalAmount(totalAmount);

        // 2. Handle Cash Tendered & Change
        BigDecimal received = req.getReceivedAmount() != null ? req.getReceivedAmount() : totalAmount;
        invoice.setReceivedAmount(received);
        if (req.getPaymentMethod() == PaymentMethod.CASH) {
            if (received.compareTo(totalAmount) < 0) {
                throw new BusinessException("INSUFFICIENT_PAYMENT", "Tiền khách đưa ít hơn số tiền phải thanh toán");
            }
            invoice.setChangeAmount(received.subtract(totalAmount));
        } else {
            invoice.setChangeAmount(BigDecimal.ZERO);
        }

        // 3. Award Customer Points atomically (1 point per 10,000 VND)
        if (customer != null && totalAmount.compareTo(BigDecimal.ZERO) > 0) {
            int earnedPoints = totalAmount.divide(BigDecimal.valueOf(10000), java.math.RoundingMode.FLOOR).intValue();
            if (earnedPoints > 0) {
                customerRepository.addPoints(customer.getId(), earnedPoints);
                customer.setPoints(customer.getPoints() + earnedPoints);
            }
        }

        return invoiceRepository.save(invoice);
    }

    @Transactional
    public Invoice refundInvoice(Long id, String reason) {
        // Khóa dòng hóa đơn để 2 yêu cầu hoàn trả đồng thời không cùng cộng kho
        Invoice invoice = invoiceRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException("INVOICE_NOT_FOUND", "Không tìm thấy hóa đơn với ID: " + id));

        if (invoice.getStatus() == InvoiceStatus.REFUNDED) {
            throw new BusinessException("ALREADY_REFUNDED", "Hóa đơn này đã được hoàn trả trước đó");
        }

        // 1. Hoàn tồn kho nguyên tử cho từng sản phẩm trong hóa đơn
        if (invoice.getDetails() != null) {
            for (InvoiceDetail detail : invoice.getDetails()) {
                inventoryService.addStockAtomic(
                        detail.getProduct().getId(),
                        invoice.getBranch().getId(),
                        detail.getQuantity()
                );
            }
        }

        // 2. Thu hồi điểm tích lũy khách hàng (nếu có)
        if (invoice.getCustomer() != null && invoice.getTotalAmount() != null && invoice.getTotalAmount().compareTo(BigDecimal.ZERO) > 0) {
            int earnedPoints = invoice.getTotalAmount().divide(BigDecimal.valueOf(10000), java.math.RoundingMode.FLOOR).intValue();
            if (earnedPoints > 0) {
                int currentPoints = invoice.getCustomer().getPoints();
                int deductPoints = Math.min(currentPoints, earnedPoints);
                if (deductPoints > 0) {
                    customerRepository.addPoints(invoice.getCustomer().getId(), -deductPoints);
                    invoice.getCustomer().setPoints(currentPoints - deductPoints);
                }
            }
        }

        // 3. Cập nhật trạng thái hóa đơn
        invoice.setStatus(InvoiceStatus.REFUNDED);
        String noteReason = (reason != null && !reason.trim().isEmpty()) ? " | Lý do trả: " + reason.trim() : "";
        String existingNote = invoice.getNote() != null ? invoice.getNote() : "";
        invoice.setNote((existingNote + " [ĐÃ HOÀN TRẢ" + noteReason + "]").trim());

        return invoiceRepository.save(invoice);
    }

    /**
     * ADMIN được chọn chi nhánh bất kỳ; các vai trò khác chỉ bán tại chi nhánh của mình.
     */
    private Long resolveBranchId(Long requestedBranchId, User actor) {
        if (actor.getRole() == Role.ADMIN || actor.getBranchId() == null) {
            if (requestedBranchId == null) {
                throw new BusinessException("BRANCH_REQUIRED", "Chi nhánh không được để trống");
            }
            return requestedBranchId;
        }
        if (requestedBranchId != null && !requestedBranchId.equals(actor.getBranchId())) {
            throw new BusinessException("BRANCH_FORBIDDEN",
                    "Bạn chỉ được bán hàng tại chi nhánh của mình", HttpStatus.FORBIDDEN);
        }
        return actor.getBranchId();
    }

    /**
     * Giá bán lấy từ danh mục sản phẩm. Chỉ ADMIN/MANAGER được bán khác giá niêm yết.
     */
    private BigDecimal resolveUnitPrice(Product product, BigDecimal requestedPrice, User actor) {
        BigDecimal listPrice = product.getSalePrice();
        if (requestedPrice == null || (listPrice != null && requestedPrice.compareTo(listPrice) == 0)) {
            if (listPrice == null) {
                throw new BusinessException("PRODUCT_NO_PRICE", "Sản phẩm chưa có giá bán: " + product.getName());
            }
            return listPrice;
        }
        if (actor.getRole() != Role.ADMIN && actor.getRole() != Role.MANAGER) {
            throw new BusinessException("PRICE_OVERRIDE_FORBIDDEN",
                    "Giá bán của \"" + product.getName() + "\" khác giá niêm yết. Vui lòng tải lại danh sách sản phẩm.",
                    HttpStatus.FORBIDDEN);
        }
        if (requestedPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("INVALID_PRICE", "Giá bán không được âm");
        }
        return requestedPrice;
    }
}
