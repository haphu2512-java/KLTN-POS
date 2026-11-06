package com.pos.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Giới hạn số request /api/** mỗi phút theo địa chỉ IP (cửa sổ cố định 60 giây), chống spam và dò quét API.
 * Đếm trong bộ nhớ vì cả cửa hàng chỉ có một backend. api.rate-limit.per-minute = 0 thì tắt.
 * Đăng nhập sai còn có khóa riêng theo tài khoản (LoginAttemptService).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ApiRateLimitFilter extends OncePerRequestFilter {

    private record Window(long minute, AtomicInteger count) {}

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final int perMinute;
    private final Clock clock;

    @Autowired
    public ApiRateLimitFilter(@Value("${api.rate-limit.per-minute:600}") int perMinute) {
        this(perMinute, Clock.systemUTC());
    }

    ApiRateLimitFilter(int perMinute, Clock clock) {
        this.perMinute = perMinute;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return perMinute <= 0 || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long minute = clock.millis() / 60_000;
        String ip = request.getRemoteAddr();
        Window w = windows.compute(ip, (k, old) ->
                old == null || old.minute() != minute ? new Window(minute, new AtomicInteger()) : old);
        int used = w.count().incrementAndGet();

        if (used > perMinute) {
            long retryAfter = 60 - (clock.millis() / 1000) % 60;
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"success\":false,\"code\":\"RATE_LIMITED\","
                    + "\"message\":\"Quá nhiều yêu cầu, vui lòng thử lại sau " + retryAfter + " giây.\"}");
            return;
        }
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(e -> e.getValue().minute() != minute);
        }
        chain.doFilter(request, response);
    }
}
