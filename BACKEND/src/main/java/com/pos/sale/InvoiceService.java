package com.pos.sale;

import org.springframework.beans.factory.annotation.Autowired;
import com.pos.audit.AuditService;
import com.pos.audit.AuditAction;
import com.pos.approval.ApprovalService;
import com.pos.branch.Branch;
import com.pos.branch.BranchRepository;
import com.pos.common.BusinessException;
import com.pos.customer.Customer;
import com.pos.customer.CustomerRepository;
import com.pos.inventory.InventoryService;
import com.pos.loyalty.LoyaltyPolicy;
import com.pos.loyalty.LoyaltyService;
import com.pos.product.Product;
import com.pos.product.ProductRepository;
import com.pos.promotion.PromotionService;
import com.pos.shift.ShiftService;
import com.pos.user.Role;
import com.pos.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final BranchRepository branchRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final ShiftService shiftService;
    private final ApprovalService approvalService;
    private final LoyaltyService loyaltyService;
    private final PromotionService promotionService;
    private final InvoiceReturnRepository invoiceReturnRepository;

    /** Số ngày khách được đổi trả (in trên bill). Quá hạn chỉ quản lý hoàn trả được, phải ghi lý do. */
    @Value("${pos.return.days:3}")
    private int returnDays = 3;

    /** Nhật ký thao tác; để trống trong unit test dựng service bằng tay */
    @Autowired(required = false)
    private AuditService auditService;

    private void audit(AuditAction action, String entityType, Object entityId, Long branchId, String summary) {
        if (auditService != null) auditService.log(action, entityType, entityId, branchId, summary);
    }

    public Page<Invoice> getInvoices(Long branchId, Pageable pageable) {
        Page<Invoice> page = branchId != null
                ? invoiceRepository.findByBranchIdOrderByCreatedAtDesc(branchId, pageable)
                : invoiceRepository.findAllByOrderByCreatedAtDesc(pageable);
        fillStaffNames(page.getContent());
        return page;
    }

    public List<Invoice> getAllInvoices(Long branchId) {
        List<Invoice> invoices = branchId != null
                ? invoiceRepository.findByBranchIdOrderByCreatedAtDesc(branchId)
                : invoiceRepository.findAllByOrderByCreatedAtDesc();
        fillStaffNames(invoices);
        return invoices;
    }

    /** Gắn tên nhân viên bán và người hoàn trả để quản lý biết ai chịu trách nhiệm từng hóa đơn */
    private void fillStaffNames(List<Invoice> invoices) {
        if (invoices.isEmpty()) return;
        Map<Long, String> names = shiftService.userDisplayNames(invoices.stream()
                .flatMap(i -> Stream.of(i.getEmployeeId(), i.getRefundedBy(), i.getDiscountApprovedBy())).toList());
        for (Invoice i : invoices) {
            i.setEmployeeName(i.getEmployeeId() != null ? names.get(i.getEmployeeId()) : null);
            i.setRefundedByName(i.getRefundedBy() != null ? names.get(i.getRefundedBy()) : null);
            i.setDiscountApprovedByName(i.getDiscountApprovedBy() != null ? names.get(i.getDiscountApprovedBy()) : null);
        }
    }

    public Invoice getInvoiceById(Long id) {
        return invoiceRepository.findById(id)
                .orElseThrow(() -> new BusinessException("INVOICE_NOT_FOUND", "Không tìm thấy hóa đơn với ID: " + id));
    }

    /**
     * Xem chi tiết hóa đơn theo quyền: ADMIN mọi hóa đơn; MANAGER hóa đơn của chi nhánh đang đăng nhập;
     * CASHIER chỉ hóa đơn bán (hoặc hoàn trả) trong ca do chính mình mở.
     */
    public Invoice getInvoiceForViewer(Long id, User actor) {
        Invoice invoice = getInvoiceById(id);
        boolean allowed = switch (actor.getRole()) {
            case ADMIN -> true;
            case MANAGER -> invoice.getBranch() != null
                    && invoice.getBranch().getId().equals(actor.getCurrentBranchId());
            case CASHIER -> shiftService.isOwnShift(invoice.getShiftSessionId(), actor)
                    || shiftService.isOwnShift(invoice.getRefundShiftSessionId(), actor);
            default -> false;
        };
        if (!allowed) {
            throw new BusinessException("INVOICE_FORBIDDEN", "Bạn không có quyền xem hóa đơn này", HttpStatus.FORBIDDEN);
        }
        if (actor.getRole() == Role.CASHIER) {
            invoice.setEmployeeName(shiftService.userDisplayName(invoice.getEmployeeId()));
        } else {
            fillStaffNames(List.of(invoice));
        }
        return invoice;
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

        // Thu ngân phải đang trực ca tại chi nhánh này; hóa đơn được gắn vào ca để chốt tiền cuối ca
        Long shiftSessionId = shiftService.resolveShiftForSale(actor, branchId);

        BigDecimal discount = req.getDiscount() != null ? req.getDiscount() : BigDecimal.ZERO;
        if (discount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("INVALID_DISCOUNT", "Giảm giá không được âm");
        }

        Customer customer = null;
        if (req.getCustomerId() != null) {
            customer = customerRepository.findById(req.getCustomerId())
                    .orElseThrow(() -> new BusinessException("CUSTOMER_NOT_FOUND",
                            "Không tìm thấy khách hàng. Hãy chọn lại khách trước khi thanh toán."));
        }
        int redeemPoints = req.getRedeemPoints() != null ? req.getRedeemPoints() : 0;
        if (redeemPoints > 0 && customer == null) {
            throw new BusinessException("CUSTOMER_REQUIRED", "Chọn khách hàng thành viên để dùng điểm");
        }

        Invoice invoice = Invoice.builder()
                .branch(branch)
                .customer(customer)
                .employeeId(actor.getId())
                .paymentMethod(req.getPaymentMethod())
                .discount(discount)
                .note(req.getNote())
                .clientRequestId(clientRequestId)
                .shiftSessionId(shiftSessionId)
                .details(new ArrayList<>())
                .build();

        BigDecimal subtotal = BigDecimal.ZERO;
        List<PromotionService.Line> promotionLines = new ArrayList<>();

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
            promotionLines.add(new PromotionService.Line(product.getCategory(), lineTotal));
        }

        invoice.setSubtotal(subtotal);

        // Khuyến mãi tính trước tiên, trên tiền hàng; không tính vào hạn mức giảm tay của thu ngân
        BigDecimal promotionDiscount = BigDecimal.ZERO;
        if (req.getPromotionId() != null) {
            PromotionService.Applied applied = promotionService.applyToInvoice(
                    req.getPromotionId(), branchId, customer, promotionLines);
            promotionDiscount = applied.discountAmount();
            invoice.setPromotionId(applied.promotionId());
            invoice.setPromotionName(applied.name());
            invoice.setPromotionDiscount(promotionDiscount);
        }
        BigDecimal afterPromotion = subtotal.subtract(promotionDiscount);

        if (discount.compareTo(afterPromotion) > 0) {
            throw new BusinessException("INVALID_DISCOUNT", promotionDiscount.signum() > 0
                    ? "Giảm giá không được lớn hơn tiền hàng sau khuyến mãi"
                    : "Giảm giá không được lớn hơn tổng tiền hàng");
        }
        String discountReason = req.getDiscountReason() != null && !req.getDiscountReason().isBlank()
                ? req.getDiscountReason().trim() : null;
        invoice.setDiscountReason(discount.signum() > 0 ? discountReason : null);
        // Thu ngân giảm vượt hạn mức: phải có lý do và mã duyệt từ PIN quản lý
        String approvalToken = null;
        if (approvalService.requiresDiscountApproval(actor, discount, subtotal)) {
            if (discountReason == null) {
                throw new BusinessException("DISCOUNT_REASON_REQUIRED", "Nhập lý do giảm giá");
            }
            approvalToken = req.getDiscountApprovalToken();
            invoice.setDiscountApprovedBy(approvalService.checkDiscountApproval(approvalToken, actor, branchId, discount));
        }
        // Đổi điểm tính trên số tiền sau khuyến mãi và giảm tay; không tính vào hạn mức giảm của thu ngân
        LoyaltyPolicy loyalty = loyaltyService.getPolicy();
        BigDecimal afterDiscount = afterPromotion.subtract(discount);
        if (redeemPoints > 0) {
            if (redeemPoints < loyalty.getMinRedeemPoints()) {
                throw new BusinessException("INVALID_REDEEM",
                        "Mỗi lần đổi tối thiểu " + loyalty.getMinRedeemPoints() + " điểm");
            }
            int balance = customer.getPoints() != null ? customer.getPoints() : 0;
            int maxRedeem = loyalty.maxRedeemable(afterDiscount, balance);
            if (redeemPoints > maxRedeem) {
                throw new BusinessException("INVALID_REDEEM", "Hóa đơn này chỉ dùng được tối đa " + maxRedeem + " điểm");
            }
        }
        BigDecimal pointsDiscount = loyalty.valueOf(redeemPoints);
        BigDecimal totalAmount = afterDiscount.subtract(pointsDiscount);
        invoice.setTotalAmount(totalAmount);
        int earnedPoints = customer != null ? loyalty.pointsEarnedFor(totalAmount) : 0;
        if (customer != null) {
            invoice.setPointsRedeemed(redeemPoints);
            invoice.setPointsDiscount(pointsDiscount);
            invoice.setPointsEarned(earnedPoints);
        }

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

        Invoice saved = invoiceRepository.save(invoice);

        // 3. Trừ điểm đã dùng rồi cộng điểm mới, ghi sổ điểm theo hóa đơn (cùng transaction với hóa đơn)
        if (customer != null) {
            loyaltyService.redeem(customer, saved.getId(), redeemPoints, actor.getId());
            loyaltyService.earn(customer, saved.getId(), earnedPoints, actor.getId());
            saved.setCustomerPointsBalance(customer.getPoints());
        }
        approvalService.markUsed(approvalToken);
        if (discount.signum() > 0) {
            audit(AuditAction.INVOICE_DISCOUNT, "INVOICE", saved.getId(), branchId,
                    "Hóa đơn #" + saved.getId() + " giảm tay " + AuditService.money(discount)
                            + " / tiền hàng " + AuditService.money(subtotal)
                            + (discountReason != null ? " - lý do: " + discountReason : "")
                            + (saved.getDiscountApprovedBy() != null ? " (quản lý #" + saved.getDiscountApprovedBy() + " duyệt)" : ""));
        }
        return saved;
    }

    /** Hoàn trả cả hóa đơn (mọi món còn lại); giữ cho API cũ /refund */
    @Transactional
    public Invoice refundInvoice(Long id, String reason, User actor) {
        return createReturn(id, null, reason, null, actor).invoice();
    }

    public int getReturnDays() {
        return returnDays;
    }

    /** Hạn đổi trả của hóa đơn (hết ngày thứ returnDays kể từ ngày mua) */
    public LocalDateTime returnDeadline(Invoice invoice) {
        LocalDateTime created = invoice.getCreatedAt() != null ? invoice.getCreatedAt() : LocalDateTime.now();
        return created.toLocalDate().plusDays(returnDays + 1L).atStartOfDay();
    }

    public record ReturnLine(Long detailId, Integer quantity) {}

    public record ReturnResult(Invoice invoice, InvoiceReturn invoiceReturn) {}

    public record ReturnInfo(Invoice invoice, List<InvoiceReturn> returns, int returnDays,
                             LocalDateTime deadline, boolean overdue, boolean approvalRequired) {}

    /**
     * Thông tin để hoàn trả tại quầy: thu ngân tra hóa đơn theo số trên bill của khách (cùng chi nhánh,
     * còn trong hạn đổi trả); ADMIN/MANAGER theo quyền xem hóa đơn thông thường.
     */
    public ReturnInfo getReturnInfo(Long id, User actor) {
        Invoice invoice;
        if (actor.getRole() == Role.CASHIER) {
            invoice = getInvoiceById(id);
            if (invoice.getBranch() == null || !invoice.getBranch().getId().equals(actor.getCurrentBranchId())) {
                throw new BusinessException("INVOICE_FORBIDDEN", "Hóa đơn không thuộc chi nhánh đang đăng nhập", HttpStatus.FORBIDDEN);
            }
            if (LocalDateTime.now().isAfter(returnDeadline(invoice))) {
                throw new BusinessException("RETURN_EXPIRED", "Hóa đơn đã quá hạn đổi trả " + returnDays
                        + " ngày, cần quản lý xử lý", HttpStatus.FORBIDDEN);
            }
            invoice.setEmployeeName(shiftService.userDisplayName(invoice.getEmployeeId()));
        } else {
            invoice = getInvoiceForViewer(id, actor);
        }
        List<InvoiceReturn> returns = invoiceReturnRepository.findByInvoiceIdOrderByCreatedAtAsc(id);
        for (InvoiceReturn r : returns) {
            r.setCreatedByName(r.getCreatedBy() != null ? shiftService.userDisplayName(r.getCreatedBy()) : null);
            r.setApprovedByName(r.getApprovedBy() != null ? shiftService.userDisplayName(r.getApprovedBy()) : null);
        }
        return new ReturnInfo(invoice, returns, returnDays, returnDeadline(invoice),
                LocalDateTime.now().isAfter(returnDeadline(invoice)), actor.getRole() == Role.CASHIER);
    }

    /**
     * Hoàn trả một số món (hoặc cả hóa đơn khi lines rỗng).
     * <ul>
     *   <li>Tiền hoàn = giá trị các món trả × (khách thực trả / tạm tính), tức là chia đều khuyến mãi, giảm giá
     *       và tiền đổi điểm theo giá trị món. Lần trả cuối hoàn nốt phần còn lại để tổng hoàn đúng bằng tiền khách trả.</li>
     *   <li>Điểm: trả lại điểm đã dùng và thu hồi điểm đã cộng theo cùng tỷ lệ (làm tròn xuống, lần cuối lấy hết).</li>
     *   <li>Thu ngân cần mã duyệt PIN của quản lý; quá hạn đổi trả thì chỉ ADMIN/MANAGER làm được và phải ghi lý do.</li>
     * </ul>
     */
    @Transactional
    public ReturnResult createReturn(Long id, List<ReturnLine> lines, String reason, String approvalToken, User actor) {
        // Khóa dòng hóa đơn để 2 yêu cầu hoàn trả đồng thời không cùng cộng kho
        Invoice invoice = invoiceRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException("INVOICE_NOT_FOUND", "Không tìm thấy hóa đơn với ID: " + id));

        if (invoice.getStatus() == InvoiceStatus.REFUNDED) {
            throw new BusinessException("ALREADY_REFUNDED", "Hóa đơn này đã được hoàn trả trước đó");
        }
        Long invoiceBranchId = invoice.getBranch().getId();
        if (actor.getRole() != Role.ADMIN && !invoiceBranchId.equals(actor.getCurrentBranchId())) {
            throw new BusinessException("BRANCH_FORBIDDEN",
                    "Chỉ hoàn trả được hóa đơn của chi nhánh đang đăng nhập", HttpStatus.FORBIDDEN);
        }
        String cleanReason = reason != null && !reason.isBlank() ? reason.trim() : null;
        boolean overdue = LocalDateTime.now().isAfter(returnDeadline(invoice));
        if (overdue && actor.getRole() != Role.ADMIN && actor.getRole() != Role.MANAGER) {
            throw new BusinessException("RETURN_EXPIRED", "Hóa đơn đã quá hạn đổi trả " + returnDays
                    + " ngày, cần quản lý xử lý", HttpStatus.FORBIDDEN);
        }
        if ((overdue || actor.getRole() == Role.CASHIER) && cleanReason == null) {
            throw new BusinessException("RETURN_REASON_REQUIRED", overdue
                    ? "Hóa đơn quá hạn đổi trả, cần ghi lý do" : "Nhập lý do khách trả hàng");
        }

        // 1. Món và số lượng trả
        List<InvoiceDetail> details = invoice.getDetails() != null ? invoice.getDetails() : List.of();
        // Khóa theo vị trí dòng (không dùng InvoiceDetail làm khóa: equals/hashCode của entity đi vòng qua hóa đơn)
        Map<Integer, Integer> picked = new java.util.TreeMap<>();
        if (lines == null || lines.isEmpty()) {
            for (int i = 0; i < details.size(); i++) {
                if (details.get(i).getRemainingQuantity() > 0) picked.put(i, details.get(i).getRemainingQuantity());
            }
        } else {
            Map<Long, Integer> indexById = new HashMap<>();
            for (int i = 0; i < details.size(); i++) indexById.put(details.get(i).getId(), i);
            for (ReturnLine line : lines) {
                if (line == null || line.quantity() == null || line.quantity() <= 0) continue;
                Integer idx = line.detailId() != null ? indexById.get(line.detailId()) : null;
                if (idx == null) {
                    throw new BusinessException("RETURN_INVALID_ITEM", "Món trả không thuộc hóa đơn này");
                }
                InvoiceDetail d = details.get(idx);
                int qty = picked.getOrDefault(idx, 0) + line.quantity();
                if (qty > d.getRemainingQuantity()) {
                    throw new BusinessException("RETURN_QTY_EXCEEDED", d.getProduct().getName() + ": chỉ còn "
                            + d.getRemainingQuantity() + " có thể trả");
                }
                picked.put(idx, qty);
            }
        }
        if (picked.isEmpty() && !details.isEmpty()) {
            throw new BusinessException("RETURN_EMPTY", "Chọn ít nhất một món để trả");
        }

        // 2. Tiền hoàn: phân bổ theo tỷ lệ tiền khách thực trả trên tạm tính
        BigDecimal total = invoice.getTotalAmount() != null ? invoice.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal alreadyRefunded = invoice.getRefundedAmount() != null ? invoice.getRefundedAmount() : BigDecimal.ZERO;
        BigDecimal subtotal = details.stream().map(d -> d.getTotalPrice() != null ? d.getTotalPrice() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean finalReturn = true;
        for (int i = 0; i < details.size(); i++) {
            if (picked.getOrDefault(i, 0) != details.get(i).getRemainingQuantity()) finalReturn = false;
        }

        InvoiceReturn ret = InvoiceReturn.builder()
                .invoice(invoice)
                .branchId(invoiceBranchId)
                .paymentMethod(invoice.getPaymentMethod() != null ? invoice.getPaymentMethod() : PaymentMethod.CASH)
                .reason(cleanReason)
                .createdAt(LocalDateTime.now())
                .createdBy(actor.getId())
                .build();
        BigDecimal refund = BigDecimal.ZERO;
        for (Map.Entry<Integer, Integer> e : picked.entrySet()) {
            InvoiceDetail d = details.get(e.getKey());
            BigDecimal unit = d.getUnitPrice() != null ? d.getUnitPrice() : BigDecimal.ZERO;
            BigDecimal gross = unit.multiply(BigDecimal.valueOf(e.getValue()));
            BigDecimal lineRefund = subtotal.signum() > 0
                    ? gross.multiply(total).divide(subtotal, 0, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            refund = refund.add(lineRefund);
            ret.getDetails().add(InvoiceReturnDetail.builder()
                    .invoiceReturn(ret)
                    .invoiceDetailId(d.getId() != null ? d.getId() : 0L)
                    .productId(d.getProduct().getId())
                    .productName(d.getProduct().getName())
                    .quantity(e.getValue())
                    .unitPrice(unit)
                    .refundAmount(lineRefund)
                    .build());
        }
        BigDecimal remainingMoney = total.subtract(alreadyRefunded).max(BigDecimal.ZERO);
        if (finalReturn || refund.compareTo(remainingMoney) > 0) {
            // Dồn phần lệch do làm tròn vào dòng cuối để tổng các dòng khớp tiền hoàn
            BigDecimal adjust = remainingMoney.subtract(refund);
            refund = remainingMoney;
            if (!ret.getDetails().isEmpty()) {
                InvoiceReturnDetail last = ret.getDetails().get(ret.getDetails().size() - 1);
                last.setRefundAmount(last.getRefundAmount().add(adjust).max(BigDecimal.ZERO));
            }
        }
        ret.setRefundAmount(refund);

        // 3. Thu ngân: kiểm tra mã duyệt PIN của quản lý (đủ số tiền hoàn)
        if (actor.getRole() == Role.CASHIER) {
            ret.setApprovedBy(approvalService.checkRefundApproval(approvalToken, actor, invoiceBranchId, refund));
        }

        // 4. Cộng lại kho và đánh dấu số lượng đã trả
        for (Map.Entry<Integer, Integer> e : picked.entrySet()) {
            InvoiceDetail d = details.get(e.getKey());
            inventoryService.addStockAtomic(d.getProduct().getId(), invoiceBranchId, e.getValue());
            d.setReturnedQuantity((d.getReturnedQuantity() != null ? d.getReturnedQuantity() : 0) + e.getValue());
        }

        // 5. Điểm: trả lại điểm đã dùng và thu hồi điểm đã cộng theo tỷ lệ tiền đã hoàn (cộng dồn các lần)
        if (invoice.getCustomer() != null) {
            int redeemed = invoice.getPointsRedeemed() != null ? invoice.getPointsRedeemed() : 0;
            int earned = invoice.getPointsEarned() != null
                    ? invoice.getPointsEarned()
                    : loyaltyService.getPolicy().pointsEarnedFor(total);
            List<InvoiceReturn> previous = invoiceReturnRepository.findByInvoiceIdOrderByCreatedAtAsc(id);
            int prevReturned = previous.stream().mapToInt(r -> r.getPointsReturned() != null ? r.getPointsReturned() : 0).sum();
            int prevRevoked = previous.stream().mapToInt(r -> r.getPointsRevoked() != null ? r.getPointsRevoked() : 0).sum();
            BigDecimal cumulative = alreadyRefunded.add(refund);
            int targetReturned = finalReturn ? redeemed : proportion(redeemed, cumulative, total);
            int targetRevoked = finalReturn ? earned : proportion(earned, cumulative, total);
            int giveBack = Math.max(0, targetReturned - prevReturned);
            int revoke = Math.max(0, targetRevoked - prevRevoked);
            loyaltyService.reverseForRefund(invoice.getCustomer(), invoice.getId(), giveBack, revoke, actor.getId());
            ret.setPointsReturned(giveBack);
            ret.setPointsRevoked(revoke);
        }

        // 6. Két chi tiền: ca đang mở ở chi nhánh (không có thì tính vào ca đã bán)
        Long refundShift = shiftService.resolveShiftForRefund(actor, invoiceBranchId);
        ret.setShiftSessionId(refundShift);
        InvoiceReturn savedReturn = invoiceReturnRepository.save(ret);

        // 7. Trạng thái hóa đơn
        invoice.setRefundedAmount(alreadyRefunded.add(refund));
        String noteReason = cleanReason != null ? " | Lý do trả: " + cleanReason : "";
        String existingNote = invoice.getNote() != null ? invoice.getNote() : "";
        if (finalReturn) {
            invoice.setStatus(InvoiceStatus.REFUNDED);
            invoice.setRefundedAt(LocalDateTime.now());
            invoice.setRefundedBy(actor.getId());
            invoice.setRefundShiftSessionId(refundShift);
            invoice.setNote(truncateNote((existingNote + " [ĐÃ HOÀN TRẢ" + noteReason + "]").trim()));
        } else {
            invoice.setStatus(InvoiceStatus.PARTIALLY_REFUNDED);
            invoice.setNote(truncateNote((existingNote + " [HOÀN TRẢ MỘT PHẦN " + AuditService.money(refund) + noteReason + "]").trim()));
        }
        Invoice saved = invoiceRepository.save(invoice);
        approvalService.markUsed(approvalToken);

        String items = savedReturn.getDetails().stream()
                .map(d -> d.getQuantity() + " x " + d.getProductName())
                .collect(java.util.stream.Collectors.joining(", "));
        audit(AuditAction.REFUND, "INVOICE", invoice.getId(), invoiceBranchId,
                "Hóa đơn #" + invoice.getId() + (finalReturn ? " hoàn hết" : " hoàn một phần") + ": " + items
                        + " - hoàn " + AuditService.money(refund)
                        + (overdue ? " (quá hạn đổi trả)" : "")
                        + (cleanReason != null ? " - lý do: " + cleanReason : "")
                        + (savedReturn.getApprovedBy() != null ? " (quản lý #" + savedReturn.getApprovedBy() + " duyệt PIN)" : ""));
        return new ReturnResult(saved, savedReturn);
    }

    /** floor(points × part / whole), không vượt points */
    private static int proportion(int points, BigDecimal part, BigDecimal whole) {
        if (points <= 0 || whole.signum() <= 0) return 0;
        int v = BigDecimal.valueOf(points).multiply(part).divide(whole, 0, RoundingMode.FLOOR).intValue();
        return Math.min(points, Math.max(0, v));
    }

    private static String truncateNote(String note) {
        return note.length() <= 255 ? note : note.substring(note.length() - 255);
    }

    /**
     * Bán tại chi nhánh của phiên đăng nhập (chọn lúc đăng nhập, nằm trong token), kể cả ADMIN.
     * branchId client gửi lên chỉ dùng để phát hiện đơn cũ của phiên khác: lệch thì từ chối.
     */
    private Long resolveBranchId(Long requestedBranchId, User actor) {
        Long session = actor.getCurrentBranchId();
        if (session == null) {
            throw new BusinessException("BRANCH_REQUIRED", "Phiên đăng nhập chưa chọn chi nhánh", HttpStatus.UNAUTHORIZED);
        }
        if (requestedBranchId != null && !requestedBranchId.equals(session)) {
            throw new BusinessException("BRANCH_MISMATCH",
                    "Phiên đăng nhập đang ở chi nhánh khác. Hãy đăng nhập đúng chi nhánh để bán hàng.", HttpStatus.FORBIDDEN);
        }
        return session;
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
