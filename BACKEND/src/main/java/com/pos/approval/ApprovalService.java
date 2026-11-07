package com.pos.approval;

import org.springframework.beans.factory.annotation.Autowired;
import com.pos.audit.AuditService;
import com.pos.audit.AuditAction;
import com.pos.auth.LoginAttemptService;
import com.pos.common.BusinessException;
import com.pos.user.Role;
import com.pos.user.User;
import com.pos.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Duyệt tại quầy bằng PIN của quản lý.
 * Thu ngân giảm giá vượt hạn mức thì quản lý nhập tài khoản + PIN ngay trên máy POS; server cấp một mã duyệt
 * dùng một lần, gắn với thu ngân, chi nhánh và số tiền tối đa được duyệt, hết hạn sau vài phút.
 */
@Service
public class ApprovalService {

    private static final Duration GRANT_TTL = Duration.ofMinutes(5);

    /** Việc cần quản lý duyệt; mã duyệt cho việc này không dùng được cho việc khác */
    public enum Purpose {
        DISCOUNT("giảm giá"), REFUND("hoàn trả");

        final String label;

        Purpose(String label) {
            this.label = label;
        }
    }

    /** Một lần duyệt chưa dùng */
    record Grant(Purpose purpose, Long approverId, Long requesterId, Long branchId, BigDecimal maxDiscount, Instant expiresAt) {}

    private final Map<String, Grant> grants = new ConcurrentHashMap<>();
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;
    private final BigDecimal cashierMaxDiscountPercent;

    /** Nhật ký thao tác; để trống trong unit test dựng service bằng tay */
    @Autowired(required = false)
    private AuditService auditService;

    private void audit(AuditAction action, String entityType, Object entityId, Long branchId, String summary) {
        if (auditService != null) auditService.log(action, entityType, entityId, branchId, summary);
    }

    public ApprovalService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                           LoginAttemptService loginAttemptService,
                           @Value("${pos.discount.cashier-max-percent:5}") BigDecimal cashierMaxDiscountPercent) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptService = loginAttemptService;
        this.cashierMaxDiscountPercent = cashierMaxDiscountPercent;
    }

    public BigDecimal getCashierMaxDiscountPercent() {
        return cashierMaxDiscountPercent;
    }

    /** Số tiền thu ngân được tự giảm trên một hóa đơn có tạm tính subtotal */
    public BigDecimal cashierDiscountLimit(BigDecimal subtotal) {
        return subtotal.multiply(cashierMaxDiscountPercent).divide(BigDecimal.valueOf(100), 0, RoundingMode.FLOOR);
    }

    /** Chỉ thu ngân bị giới hạn; ADMIN/MANAGER tự chịu trách nhiệm khoản giảm của mình */
    public boolean requiresDiscountApproval(User actor, BigDecimal discount, BigDecimal subtotal) {
        return actor.getRole() == Role.STAFF && discount.compareTo(cashierDiscountLimit(subtotal)) > 0;
    }

    /** Quản lý tự đặt hoặc đổi PIN, phải nhập lại mật khẩu đăng nhập */
    @Transactional
    public void setPin(User actor, String currentPassword, String pin) {
        if (!isApprover(actor)) {
            throw new BusinessException("APPROVAL_FORBIDDEN", "Chỉ quản lý hoặc quản trị được đặt PIN duyệt", HttpStatus.FORBIDDEN);
        }
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, actor.getPassword())) {
            throw new BusinessException("AUTH_INVALID_CREDENTIALS", "Mật khẩu hiện tại không đúng", HttpStatus.UNAUTHORIZED);
        }
        if (pin == null || !pin.matches("\\d{4,6}")) {
            throw new BusinessException("INVALID_PIN", "PIN phải gồm 4 đến 6 chữ số");
        }
        User user = userRepository.findById(actor.getId()).orElseThrow();
        user.setApprovalPinHash(passwordEncoder.encode(pin));
        userRepository.save(user);
    }

    public boolean hasPin(User user) {
        return user.getApprovalPinHash() != null;
    }

    /**
     * Quản lý nhập tài khoản + PIN trên máy của thu ngân để duyệt khoản giảm tối đa amount.
     * Sai PIN nhiều lần thì khóa như đăng nhập sai (khóa theo tài khoản quản lý).
     */
    public GrantResult approveDiscount(User requester, String approverUsername, String pin, BigDecimal amount) {
        return approve(Purpose.DISCOUNT, requester, approverUsername, pin, amount);
    }

    /** Quản lý nhập PIN duyệt cho thu ngân hoàn trả tối đa amount */
    public GrantResult approveRefund(User requester, String approverUsername, String pin, BigDecimal amount) {
        return approve(Purpose.REFUND, requester, approverUsername, pin, amount);
    }

    private GrantResult approve(Purpose purpose, User requester, String approverUsername, String pin, BigDecimal amount) {
        if (approverUsername == null || approverUsername.isBlank() || pin == null || amount == null
                || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("INVALID_APPROVAL_REQUEST", "Thiếu tài khoản quản lý, PIN hoặc số tiền cần duyệt");
        }
        String lockKey = "pin:" + approverUsername;
        loginAttemptService.checkNotLocked(lockKey);

        User approver = userRepository.findByUsername(approverUsername.trim()).orElse(null);
        boolean valid = approver != null && Boolean.TRUE.equals(approver.getActive()) && isApprover(approver)
                && approver.getApprovalPinHash() != null
                && passwordEncoder.matches(pin, approver.getApprovalPinHash());
        if (!valid) {
            loginAttemptService.recordFailure(lockKey);
            if (auditService != null) {
                auditService.logIndependent(AuditAction.DISCOUNT_PIN_FAILED, "USER", approver != null ? approver.getId() : null,
                        requester.getCurrentBranchId(), "Sai PIN duyệt " + purpose.label + ", tài khoản quản lý: " + approverUsername.trim(), null);
            }
            throw new BusinessException("APPROVAL_PIN_INVALID",
                    "Tài khoản quản lý hoặc PIN không đúng (hoặc quản lý chưa đặt PIN)", HttpStatus.FORBIDDEN);
        }
        loginAttemptService.recordSuccess(lockKey);

        Long branchId = requester.getCurrentBranchId();
        if (approver.getRole() == Role.MANAGER && (branchId == null || !approver.getBranchIds().contains(branchId))) {
            throw new BusinessException("APPROVAL_BRANCH_FORBIDDEN",
                    "Quản lý này không thuộc chi nhánh của quầy", HttpStatus.FORBIDDEN);
        }

        purgeExpired();
        String token = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plus(GRANT_TTL);
        grants.put(token, new Grant(purpose, approver.getId(), requester.getId(), branchId, amount, expiresAt));
        if (purpose == Purpose.DISCOUNT) {
            audit(AuditAction.DISCOUNT_APPROVED, "USER", requester.getId(), branchId,
                    displayName(approver) + " duyệt cho " + displayName(requester) + " giảm tối đa " + AuditService.money(amount));
        }
        return new GrantResult(token, displayName(approver), amount, expiresAt);
    }

    /**
     * Kiểm tra mã duyệt cho hóa đơn đang tạo, trả id quản lý đã duyệt. Chưa đánh dấu đã dùng:
     * gọi {@link #markUsed} sau khi hóa đơn lưu thành công, để hóa đơn lỗi (thiếu tiền, hết hàng) không làm mất mã.
     */
    public Long checkDiscountApproval(String token, User requester, Long branchId, BigDecimal discount) {
        Grant g = token == null ? null : grants.get(token);
        if (g == null || g.purpose() != Purpose.DISCOUNT || Instant.now().isAfter(g.expiresAt())) {
            throw new BusinessException("DISCOUNT_APPROVAL_REQUIRED",
                    "Giảm giá vượt hạn mức " + cashierMaxDiscountPercent.stripTrailingZeros().toPlainString()
                            + "% của thu ngân, cần quản lý nhập PIN duyệt", HttpStatus.FORBIDDEN);
        }
        if (!g.requesterId().equals(requester.getId()) || !java.util.Objects.equals(g.branchId(), branchId)) {
            throw new BusinessException("DISCOUNT_APPROVAL_REQUIRED", "Mã duyệt không dành cho quầy này", HttpStatus.FORBIDDEN);
        }
        if (discount.compareTo(g.maxDiscount()) > 0) {
            throw new BusinessException("DISCOUNT_APPROVAL_REQUIRED",
                    "Số tiền giảm lớn hơn số quản lý đã duyệt, cần duyệt lại", HttpStatus.FORBIDDEN);
        }
        return g.approverId();
    }

    /** Kiểm tra mã duyệt hoàn trả của thu ngân, trả id quản lý đã duyệt; gọi {@link #markUsed} sau khi hoàn trả xong */
    public Long checkRefundApproval(String token, User requester, Long branchId, BigDecimal refundAmount) {
        Grant g = token == null ? null : grants.get(token);
        if (g == null || g.purpose() != Purpose.REFUND || Instant.now().isAfter(g.expiresAt())) {
            throw new BusinessException("REFUND_APPROVAL_REQUIRED",
                    "Thu ngân hoàn trả cần quản lý nhập PIN duyệt", HttpStatus.FORBIDDEN);
        }
        if (!g.requesterId().equals(requester.getId()) || !java.util.Objects.equals(g.branchId(), branchId)) {
            throw new BusinessException("REFUND_APPROVAL_REQUIRED", "Mã duyệt không dành cho quầy này", HttpStatus.FORBIDDEN);
        }
        if (refundAmount.compareTo(g.maxDiscount()) > 0) {
            throw new BusinessException("REFUND_APPROVAL_REQUIRED",
                    "Số tiền hoàn lớn hơn số quản lý đã duyệt, cần duyệt lại", HttpStatus.FORBIDDEN);
        }
        return g.approverId();
    }

    public void markUsed(String token) {
        if (token != null) grants.remove(token);
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        grants.values().removeIf(g -> now.isAfter(g.expiresAt()));
    }

    private static boolean isApprover(User u) {
        return u.getRole() == Role.ADMIN || u.getRole() == Role.MANAGER;
    }

    private static String displayName(User u) {
        return u.getFullName() != null && !u.getFullName().isBlank() ? u.getFullName() : u.getUsername();
    }

    public record GrantResult(String token, String approverName, BigDecimal maxDiscount, Instant expiresAt) {}
}
