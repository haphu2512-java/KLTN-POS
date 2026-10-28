package com.pos.auth;

import com.pos.common.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Khóa tạm tài khoản sau nhiều lần nhập sai mật khẩu liên tiếp, chống dò mật khẩu.
 * Lưu trong bộ nhớ (một backend cho cả cửa hàng nên đủ dùng); khởi động lại backend thì xóa hết.
 * login.max-attempts = 0 thì tắt (tiện khi dev).
 */
@Service
public class LoginAttemptService {

    private record Attempts(int failures, Instant lockedUntil) {}

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();
    private final int maxAttempts;
    private final Duration lockDuration;
    private final Clock clock;

    @Autowired
    public LoginAttemptService(@Value("${login.max-attempts:5}") int maxAttempts,
                               @Value("${login.lock-minutes:15}") long lockMinutes) {
        this(maxAttempts, Duration.ofMinutes(lockMinutes), Clock.systemUTC());
    }

    LoginAttemptService(int maxAttempts, Duration lockDuration, Clock clock) {
        this.maxAttempts = maxAttempts;
        this.lockDuration = lockDuration;
        this.clock = clock;
    }

    /** Gọi trước khi kiểm tra mật khẩu: tài khoản đang bị khóa thì trả 429, không thử mật khẩu */
    public void checkNotLocked(String username) {
        if (maxAttempts <= 0 || username == null) return;
        Attempts a = attempts.get(key(username));
        if (a == null || a.lockedUntil() == null) return;
        Instant now = clock.instant();
        if (now.isBefore(a.lockedUntil())) {
            long minutes = Math.max(1, Duration.between(now, a.lockedUntil()).toSeconds() / 60 + 1);
            throw new BusinessException("AUTH_TOO_MANY_ATTEMPTS",
                    "Đăng nhập sai quá " + maxAttempts + " lần. Vui lòng thử lại sau " + minutes + " phút.",
                    HttpStatus.TOO_MANY_REQUESTS);
        }
        // Hết thời gian khóa: cho thử lại từ đầu
        attempts.remove(key(username));
    }

    public void recordFailure(String username) {
        if (maxAttempts <= 0 || username == null) return;
        attempts.compute(key(username), (k, a) -> {
            int failures = (a == null ? 0 : a.failures()) + 1;
            return failures >= maxAttempts
                    ? new Attempts(0, clock.instant().plus(lockDuration))
                    : new Attempts(failures, null);
        });
    }

    public void recordSuccess(String username) {
        if (username != null) attempts.remove(key(username));
    }

    private static String key(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
