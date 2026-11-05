package com.pos.promotion;

import org.springframework.beans.factory.annotation.Autowired;
import com.pos.audit.AuditService;
import com.pos.audit.AuditAction;
import com.pos.branch.BranchRepository;
import com.pos.common.BusinessException;
import com.pos.customer.Customer;
import com.pos.customer.CustomerRepository;
import com.pos.product.Product;
import com.pos.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Quản lý chương trình khuyến mãi (ADMIN) và tính số tiền được giảm cho một đơn hàng.
 * Tiền giảm luôn do server tính từ giỏ hàng; POS chỉ gửi id chương trình được chọn.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PromotionService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final PromotionRepository promotionRepository;
    private final BranchRepository branchRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;

    /** Nhật ký thao tác; để trống trong unit test dựng service bằng tay */
    @Autowired(required = false)
    private AuditService auditService;

    private void audit(AuditAction action, String entityType, Object entityId, Long branchId, String summary) {
        if (auditService != null) auditService.log(action, entityType, entityId, branchId, summary);
    }

    /** Một dòng hàng của đơn: danh mục sản phẩm và thành tiền (đơn giá x số lượng) */
    public record Line(String category, BigDecimal lineTotal) {}

    /** Khuyến mãi áp được cho đơn kèm số tiền giảm */
    public record Quote(Long promotionId, String name, String description, PromotionType type,
                        BigDecimal value, String category, Boolean memberOnly, BigDecimal discountAmount) {}

    // ------------------------------------------------------------------ CRUD

    public List<Promotion> getAll() {
        return promotionRepository.findAllByOrderByCreatedAtDesc();
    }

    public Promotion getById(Long id) {
        return promotionRepository.findById(id)
                .orElseThrow(() -> new BusinessException("PROMOTION_NOT_FOUND",
                        "Không tìm thấy chương trình khuyến mãi", HttpStatus.NOT_FOUND));
    }

    @Transactional
    public Promotion create(PromotionRequest req, Long actorId) {
        Promotion p = new Promotion();
        apply(p, req);
        p.setActive(req.getActive() == null || req.getActive());
        p.setCreatedBy(actorId);
        p.setCreatedAt(LocalDateTime.now());
        Promotion saved = promotionRepository.save(p);
        audit(AuditAction.PROMOTION_CHANGE, "PROMOTION", saved.getId(), null, "Tạo khuyến mãi: " + saved.getName());
        return saved;
    }

    @Transactional
    public Promotion update(Long id, PromotionRequest req, Long actorId) {
        Promotion p = getById(id);
        apply(p, req);
        if (req.getActive() != null) {
            p.setActive(req.getActive());
        }
        p.setUpdatedBy(actorId);
        p.setUpdatedAt(LocalDateTime.now());
        audit(AuditAction.PROMOTION_CHANGE, "PROMOTION", id, null, "Sửa khuyến mãi: " + p.getName());
        return promotionRepository.save(p);
    }

    @Transactional
    public Promotion setActive(Long id, boolean active, Long actorId) {
        Promotion p = getById(id);
        p.setActive(active);
        audit(AuditAction.PROMOTION_CHANGE, "PROMOTION", id, null, (active ? "Bật" : "Tắt") + " khuyến mãi: " + p.getName());
        p.setUpdatedBy(actorId);
        p.setUpdatedAt(LocalDateTime.now());
        return promotionRepository.save(p);
    }

    /** Xóa hẳn chương trình. Hóa đơn cũ vẫn giữ tên và số tiền khuyến mãi đã lưu lúc bán. */
    @Transactional
    public void delete(Long id) {
        Promotion p = getById(id);
        promotionRepository.delete(p);
        audit(AuditAction.PROMOTION_CHANGE, "PROMOTION", id, null, "Xóa khuyến mãi: " + p.getName());
    }

    private void apply(Promotion p, PromotionRequest req) {
        BigDecimal value = req.getValue();
        if (req.getType() == PromotionType.PERCENT && value.compareTo(HUNDRED) > 0) {
            throw new BusinessException("INVALID_PROMOTION", "Giảm theo % không được vượt 100%");
        }
        if (req.getStartAt() != null && req.getEndAt() != null && !req.getEndAt().isAfter(req.getStartAt())) {
            throw new BusinessException("INVALID_PROMOTION", "Thời gian kết thúc phải sau thời gian bắt đầu");
        }
        if (req.getBranchId() != null && !branchRepository.existsById(req.getBranchId())) {
            throw new BusinessException("BRANCH_NOT_FOUND", "Chi nhánh không tồn tại");
        }
        p.setName(req.getName().trim());
        p.setDescription(blankToNull(req.getDescription()));
        p.setType(req.getType());
        p.setValue(value);
        // Trần giảm chỉ có nghĩa với loại %
        p.setMaxDiscountAmount(req.getType() == PromotionType.PERCENT ? req.getMaxDiscountAmount() : null);
        p.setMinOrderAmount(req.getMinOrderAmount() != null && req.getMinOrderAmount().signum() > 0
                ? req.getMinOrderAmount() : null);
        p.setCategory(blankToNull(req.getCategory()));
        boolean memberOnly = Boolean.TRUE.equals(req.getMemberOnly());
        Integer minPoints = req.getMinCustomerPoints() != null && req.getMinCustomerPoints() > 0
                ? req.getMinCustomerPoints() : null;
        // Yêu cầu điểm tối thiểu thì đương nhiên chỉ dành cho thành viên
        p.setMemberOnly(memberOnly || minPoints != null);
        p.setMinCustomerPoints(minPoints);
        p.setBranchId(req.getBranchId());
        p.setStartAt(req.getStartAt());
        p.setEndAt(req.getEndAt());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    // ------------------------------------------------------------------ Tính khuyến mãi

    /** Các khuyến mãi áp được cho đơn, số tiền giảm lớn nhất trước (POS chọn sẵn dòng đầu) */
    public List<Quote> applicable(Long branchId, Customer customer, List<Line> lines) {
        LocalDateTime now = LocalDateTime.now();
        return promotionRepository.findByActiveTrue().stream()
                .filter(p -> ineligibleReason(p, branchId, customer, lines, now) == null)
                .map(p -> toQuote(p, discountFor(p, lines)))
                .filter(q -> q.discountAmount().signum() > 0)
                .sorted(Comparator.comparing(Quote::discountAmount).reversed().thenComparing(Quote::promotionId))
                .toList();
    }

    /**
     * Xem trước khuyến mãi cho giỏ hàng đang bán trên POS (giá niêm yết, hoặc giá ADMIN/MANAGER đã sửa).
     * Hóa đơn vẫn được tính lại khi tạo nên số liệu gửi lên ở đây chỉ để hiển thị.
     */
    public List<Quote> preview(Long branchId, Long customerId, List<PreviewItem> items) {
        Customer customer = customerId != null ? customerRepository.findById(customerId).orElse(null) : null;
        List<Line> lines = (items != null ? items : List.<PreviewItem>of()).stream()
                .filter(i -> i.productId() != null && i.quantity() != null && i.quantity() > 0)
                .map(i -> {
                    Product product = productRepository.findById(i.productId()).orElse(null);
                    if (product == null) return null;
                    BigDecimal price = i.unitPrice() != null ? i.unitPrice() : product.getSalePrice();
                    if (price == null) return null;
                    return new Line(product.getCategory(), price.multiply(BigDecimal.valueOf(i.quantity())));
                })
                .filter(java.util.Objects::nonNull)
                .toList();
        return applicable(branchId, customer, lines);
    }

    public record PreviewItem(Long productId, Integer quantity, BigDecimal unitPrice) {}

    /**
     * Số tiền giảm của chương trình được chọn cho hóa đơn đang tạo.
     * Chương trình không còn áp được (hết hạn, bị tắt, đơn đổi...) thì từ chối để POS tải lại.
     */
    public Applied applyToInvoice(Long promotionId, Long branchId, Customer customer, List<Line> lines) {
        Promotion p = promotionRepository.findById(promotionId)
                .orElseThrow(() -> new BusinessException("PROMOTION_NOT_APPLICABLE",
                        "Chương trình khuyến mãi không còn tồn tại. Hãy chọn lại khuyến mãi."));
        String reason = ineligibleReason(p, branchId, customer, lines, LocalDateTime.now());
        if (reason != null) {
            throw new BusinessException("PROMOTION_NOT_APPLICABLE",
                    "Không áp được khuyến mãi \"" + p.getName() + "\": " + reason);
        }
        BigDecimal amount = discountFor(p, lines);
        if (amount.signum() <= 0) {
            throw new BusinessException("PROMOTION_NOT_APPLICABLE",
                    "Không áp được khuyến mãi \"" + p.getName() + "\": đơn không có mặt hàng được giảm");
        }
        return new Applied(p.getId(), p.getName(), amount);
    }

    /** Khuyến mãi đã áp vào hóa đơn */
    public record Applied(Long promotionId, String name, BigDecimal discountAmount) {}

    /** Lý do không áp được, null nếu áp được */
    String ineligibleReason(Promotion p, Long branchId, Customer customer, List<Line> lines, LocalDateTime now) {
        if (!Boolean.TRUE.equals(p.getActive())) return "chương trình đang tắt";
        if (p.getStartAt() != null && now.isBefore(p.getStartAt())) return "chương trình chưa bắt đầu";
        if (p.getEndAt() != null && !now.isBefore(p.getEndAt())) return "chương trình đã kết thúc";
        if (p.getBranchId() != null && !p.getBranchId().equals(branchId)) return "không áp dụng tại chi nhánh này";
        if (Boolean.TRUE.equals(p.getMemberOnly()) && customer == null) return "chỉ dành cho khách thành viên";
        if (p.getMinCustomerPoints() != null) {
            int points = customer != null && customer.getPoints() != null ? customer.getPoints() : 0;
            if (points < p.getMinCustomerPoints()) {
                return "cần khách có từ " + p.getMinCustomerPoints() + " điểm";
            }
        }
        BigDecimal base = eligibleAmount(p, lines);
        if (p.getMinOrderAmount() != null && base.compareTo(p.getMinOrderAmount()) < 0) {
            return "tiền hàng được áp dụng chưa đạt " + p.getMinOrderAmount().stripTrailingZeros().toPlainString() + "đ";
        }
        return null;
    }

    /** Tiền hàng thuộc phạm vi khuyến mãi (cả đơn hoặc chỉ danh mục đã chọn) */
    static BigDecimal eligibleAmount(Promotion p, List<Line> lines) {
        String category = p.getCategory() != null ? normalize(p.getCategory()) : null;
        return lines.stream()
                .filter(l -> category == null || category.equals(normalize(l.category())))
                .map(Line::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Số tiền giảm (làm tròn xuống tới đồng), không vượt tiền hàng được áp dụng */
    static BigDecimal discountFor(Promotion p, List<Line> lines) {
        BigDecimal base = eligibleAmount(p, lines);
        if (base.signum() <= 0) return BigDecimal.ZERO;
        BigDecimal amount;
        if (p.getType() == PromotionType.PERCENT) {
            amount = base.multiply(p.getValue()).divide(HUNDRED, 0, RoundingMode.FLOOR);
            if (p.getMaxDiscountAmount() != null) {
                amount = amount.min(p.getMaxDiscountAmount());
            }
        } else {
            amount = p.getValue();
        }
        return amount.min(base).max(BigDecimal.ZERO).setScale(0, RoundingMode.FLOOR);
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private static Quote toQuote(Promotion p, BigDecimal amount) {
        return new Quote(p.getId(), p.getName(), p.getDescription(), p.getType(), p.getValue(),
                p.getCategory(), p.getMemberOnly(), amount);
    }
}
