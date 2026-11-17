package com.pos.stockcount;

import com.pos.audit.AuditAction;
import com.pos.audit.AuditService;
import com.pos.common.BusinessException;
import com.pos.inventory.InventoryRepository;
import com.pos.inventory.InventoryService;
import com.pos.product.Product;
import com.pos.product.ProductRepository;
import com.pos.security.CurrentUserService;
import com.pos.shift.ShiftService;
import com.pos.user.Role;
import com.pos.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Kiểm kê và điều chỉnh tồn kho.
 * Quy trình: tạo phiếu (chi nhánh của phiên) → quét mã / nhập số đếm được từng mặt hàng → gửi duyệt →
 * ADMIN/MANAGER duyệt: tồn được cộng/trừ đúng phần chênh lệch, ghi nhật ký STOCK_ADJUSTMENT.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockCountService {

    private final StockCountRepository repository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryService inventoryService;
    private final CurrentUserService currentUserService;
    private final ShiftService shiftService;

    @Autowired(required = false)
    private AuditService auditService;

    public record LineRequest(Long productId, String barcode, Integer countedQty, AdjustmentReason reason, Boolean add) {}

    public record ReasonTotal(AdjustmentReason reason, String label, long quantity, BigDecimal value) {}

    public record Shrinkage(LocalDate from, LocalDate to, List<ReasonTotal> byReason, BigDecimal totalLoss, BigDecimal totalGain) {}

    public List<StockCount> list(Long requestedBranchId) {
        Long branchId = currentUserService.branchForRead(requestedBranchId, true);
        List<StockCount> list = repository.findRecent(branchId, PageRequest.of(0, 100));
        list.forEach(this::fillNames);
        return list;
    }

    public StockCount get(Long id) {
        StockCount c = find(id);
        checkBranch(c, currentUserService.getCurrentUser());
        fillNames(c);
        return c;
    }

    @Transactional
    public StockCount create(String note) {
        User actor = currentUserService.getCurrentUser();
        StockCount c = StockCount.builder()
                .branchId(currentUserService.currentBranchId())
                .note(StringUtils.hasText(note) ? note.trim() : null)
                .createdBy(actor.getId())
                .createdAt(LocalDateTime.now())
                .build();
        return named(repository.save(c));
    }

    /**
     * Thêm hoặc cập nhật một mặt hàng theo id hoặc mã vạch. add = true thì cộng dồn số đếm (quét từng món);
     * ngược lại ghi đè. Tồn hệ thống lấy tại lần đếm đầu tiên của mặt hàng.
     */
    @Transactional
    public StockCount upsertLine(Long id, LineRequest req) {
        StockCount c = editable(id);
        Product product = resolveProduct(req);
        StockCountLine line = c.getLines().stream().filter(l -> l.getProductId().equals(product.getId()))
                .findFirst().orElse(null);
        int qty = req.countedQty() != null ? req.countedQty() : 1;
        if (line == null) {
            Integer system = inventoryRepository.findQuantity(product.getId(), c.getBranchId());
            line = StockCountLine.builder()
                    .stockCount(c)
                    .productId(product.getId())
                    .productName(product.getName())
                    .barcode(product.getBarcode())
                    .systemQty(system != null ? system : 0)
                    .countedQty(0)
                    .unitCost(product.getCostPrice())
                    .build();
            c.getLines().add(line);
        }
        int newQty = Boolean.TRUE.equals(req.add()) ? line.getCountedQty() + qty : qty;
        if (newQty < 0) {
            throw new BusinessException("INVALID_COUNT", "Số lượng đếm được không được âm");
        }
        line.setCountedQty(newQty);
        if (req.reason() != null) line.setReason(req.reason());
        return named(repository.save(c));
    }

    @Transactional
    public StockCount removeLine(Long id, Long productId) {
        StockCount c = editable(id);
        c.getLines().removeIf(l -> l.getProductId().equals(productId));
        return named(repository.save(c));
    }

    @Transactional
    public StockCount submit(Long id) {
        StockCount c = editable(id);
        if (c.getLines().isEmpty()) {
            throw new BusinessException("STOCK_COUNT_EMPTY", "Phiếu chưa có mặt hàng nào");
        }
        requireReasons(c);
        c.setStatus(StockCountStatus.SUBMITTED);
        c.setSubmittedAt(LocalDateTime.now());
        return named(repository.save(c));
    }

    /** Quản lý duyệt: điều chỉnh tồn theo chênh lệch, chốt giá trị hao hụt, ghi nhật ký */
    @Transactional
    public StockCount approve(Long id) {
        User actor = currentUserService.getCurrentUser();
        if (actor.getRole() != Role.ADMIN && actor.getRole() != Role.MANAGER) {
            throw new BusinessException("STOCK_COUNT_FORBIDDEN", "Chỉ quản lý được duyệt phiếu kiểm kê", HttpStatus.FORBIDDEN);
        }
        StockCount c = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException("STOCK_COUNT_NOT_FOUND", "Không tìm thấy phiếu kiểm kê", HttpStatus.NOT_FOUND));
        checkBranch(c, actor);
        if (c.getStatus() != StockCountStatus.SUBMITTED && c.getStatus() != StockCountStatus.DRAFT) {
            throw new BusinessException("STOCK_COUNT_CLOSED", "Phiếu đã duyệt hoặc đã hủy", HttpStatus.CONFLICT);
        }
        if (c.getLines().isEmpty()) {
            throw new BusinessException("STOCK_COUNT_EMPTY", "Phiếu chưa có mặt hàng nào");
        }
        requireReasons(c);

        BigDecimal variance = BigDecimal.ZERO;
        List<String> changes = new ArrayList<>();
        for (StockCountLine l : c.getLines()) {
            int diff = l.getDifference();
            if (diff == 0) continue;
            inventoryService.adjustStockAtomic(l.getProductId(), c.getBranchId(), diff);
            BigDecimal cost = l.getUnitCost() != null ? l.getUnitCost() : BigDecimal.ZERO;
            variance = variance.add(cost.multiply(BigDecimal.valueOf(diff)));
            changes.add(l.getProductName() + " " + (diff > 0 ? "+" : "") + diff
                    + (l.getReason() != null ? " (" + l.getReason().getLabel() + ")" : ""));
        }
        c.setStatus(StockCountStatus.APPROVED);
        c.setApprovedBy(actor.getId());
        c.setApprovedAt(LocalDateTime.now());
        c.setVarianceValue(variance);
        StockCount saved = repository.save(c);
        if (auditService != null) {
            auditService.log(AuditAction.STOCK_ADJUSTMENT, "STOCK_COUNT", c.getId(), c.getBranchId(),
                    "Duyệt phiếu kiểm kê #" + c.getId() + ": " + (changes.isEmpty() ? "không chênh lệch"
                            : String.join(", ", changes)) + " - giá trị " + AuditService.money(variance));
        }
        fillNames(saved);
        return saved;
    }

    @Transactional
    public StockCount cancel(Long id) {
        StockCount c = find(id);
        checkBranch(c, currentUserService.getCurrentUser());
        if (c.getStatus() == StockCountStatus.APPROVED) {
            throw new BusinessException("STOCK_COUNT_CLOSED", "Phiếu đã duyệt, không hủy được", HttpStatus.CONFLICT);
        }
        c.setStatus(StockCountStatus.CANCELLED);
        return named(repository.save(c));
    }

    /** Báo cáo hao hụt theo lý do trong khoảng ngày (theo ngày duyệt) */
    public Shrinkage shrinkage(Long requestedBranchId, LocalDate from, LocalDate to) {
        Long branchId = currentUserService.branchForRead(requestedBranchId, true);
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.withDayOfMonth(1);
        List<ReasonTotal> rows = repository.shrinkageByReason(branchId, start.atStartOfDay(), end.plusDays(1).atStartOfDay())
                .stream().map(r -> {
                    AdjustmentReason reason = (AdjustmentReason) r[0];
                    return new ReasonTotal(reason, reason != null ? reason.getLabel() : "Chưa ghi lý do",
                            r[1] != null ? ((Number) r[1]).longValue() : 0,
                            r[2] != null ? new BigDecimal(r[2].toString()) : BigDecimal.ZERO);
                }).collect(Collectors.toList());
        BigDecimal loss = rows.stream().map(ReasonTotal::value).filter(v -> v.signum() < 0).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal gain = rows.stream().map(ReasonTotal::value).filter(v -> v.signum() > 0).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Shrinkage(start, end, rows, loss, gain);
    }

    // ---------- nội bộ ----------

    private StockCount find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException("STOCK_COUNT_NOT_FOUND", "Không tìm thấy phiếu kiểm kê", HttpStatus.NOT_FOUND));
    }

    private StockCount editable(Long id) {
        StockCount c = find(id);
        checkBranch(c, currentUserService.getCurrentUser());
        if (c.getStatus() != StockCountStatus.DRAFT) {
            throw new BusinessException("STOCK_COUNT_LOCKED", "Phiếu đã gửi duyệt, không sửa được", HttpStatus.CONFLICT);
        }
        return c;
    }

    private void checkBranch(StockCount c, User actor) {
        if (actor.getRole() == Role.ADMIN) return;
        if (!c.getBranchId().equals(actor.getCurrentBranchId())) {
            throw new BusinessException("BRANCH_FORBIDDEN", "Phiếu kiểm kê thuộc chi nhánh khác", HttpStatus.FORBIDDEN);
        }
    }

    /** Dòng có chênh lệch phải có lý do, để báo cáo hao hụt có ý nghĩa */
    private static void requireReasons(StockCount c) {
        for (StockCountLine l : c.getLines()) {
            if (l.getDifference() != 0 && l.getReason() == null) {
                throw new BusinessException("ADJUST_REASON_REQUIRED", l.getProductName() + " lệch "
                        + l.getDifference() + ", chọn lý do chênh lệch");
            }
        }
    }

    private Product resolveProduct(LineRequest req) {
        if (req.productId() != null) {
            return productRepository.findById(req.productId())
                    .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Không tìm thấy sản phẩm"));
        }
        if (StringUtils.hasText(req.barcode())) {
            return productRepository.findByBarcode(req.barcode().trim())
                    .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Không có sản phẩm mã " + req.barcode().trim()));
        }
        throw new BusinessException("PRODUCT_REQUIRED", "Chọn sản phẩm hoặc quét mã vạch");
    }

    private StockCount named(StockCount c) {
        fillNames(c);
        return c;
    }

    private void fillNames(StockCount c) {
        c.setCreatedByName(c.getCreatedBy() != null ? shiftService.userDisplayName(c.getCreatedBy()) : null);
        c.setApprovedByName(c.getApprovedBy() != null ? shiftService.userDisplayName(c.getApprovedBy()) : null);
    }
}
