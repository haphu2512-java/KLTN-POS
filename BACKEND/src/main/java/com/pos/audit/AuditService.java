package com.pos.audit;

import com.pos.security.BranchSession;
import com.pos.user.User;
import com.pos.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;

/**
 * Ghi nhật ký thao tác. {@link #log} chạy trong giao dịch của nghiệp vụ: nghiệp vụ lỗi thì không có dòng nhật ký.
 * {@link #logIndependent} ghi ngay cả khi yêu cầu sau đó thất bại (đăng nhập sai, sai PIN).
 * Người thao tác lấy từ phiên đăng nhập; gọi khi chưa đăng nhập thì dùng username truyền vào.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repository;
    private final UserRepository userRepository;

    @Transactional
    public void log(AuditAction action, String entityType, Object entityId, Long branchId, String summary) {
        save(action, entityType, entityId, branchId, summary, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logIndependent(AuditAction action, String entityType, Object entityId, Long branchId,
                               String summary, String usernameIfAnonymous) {
        try {
            save(action, entityType, entityId, branchId, summary, usernameIfAnonymous);
        } catch (RuntimeException e) {
            // Nhật ký không được làm hỏng luồng chính (vd. đăng nhập)
            log.warn("Không ghi được nhật ký {}: {}", action, e.getMessage());
        }
    }

    private void save(AuditAction action, String entityType, Object entityId, Long branchId,
                      String summary, String fallbackUsername) {
        AuditLog.AuditLogBuilder b = AuditLog.builder()
                .createdAt(LocalDateTime.now())
                .action(action)
                .entityType(entityType)
                .entityId(entityId == null ? null : String.valueOf(entityId))
                .summary(truncate(summary, 500))
                .ipAddress(clientIp());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())
                ? auth.getName() : null;
        Long sessionBranch = auth != null && auth.getDetails() instanceof BranchSession s ? s.branchId() : null;
        if (username != null) {
            User u = userRepository.findByUsername(username).orElse(null);
            if (u != null) {
                b.actorId(u.getId()).actorRole(u.getRole() != null ? u.getRole().name() : null);
            }
        } else {
            username = fallbackUsername;
        }
        b.actorUsername(truncate(username, 50));
        b.branchId(branchId != null ? branchId : sessionBranch);
        repository.save(b.build());
    }

    private static String clientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest req = attrs.getRequest();
            return req.getRemoteAddr();
        }
        return null;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static final DecimalFormatSymbols VN = new DecimalFormatSymbols(Locale.forLanguageTag("vi-VN"));

    /** Định dạng tiền kiểu Việt Nam: 15000 → "15.000đ" */
    public static String money(BigDecimal v) {
        if (v == null) return "—";
        return new DecimalFormat("#,##0", VN).format(v) + "đ";
    }

    /** "Tên: cũ → mới" khi khác nhau, ngược lại null */
    public static String diff(String label, Object before, Object after) {
        if (before instanceof BigDecimal a && after instanceof BigDecimal c && a.compareTo(c) == 0) return null;
        if (Objects.equals(before, after)) return null;
        return label + ": " + fmt(before) + " → " + fmt(after);
    }

    private static String fmt(Object v) {
        if (v == null) return "—";
        if (v instanceof BigDecimal d) return money(d);
        return String.valueOf(v);
    }
}
