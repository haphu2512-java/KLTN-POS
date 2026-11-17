package com.pos.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ApiRateLimitFilterTest {

    private static MockHttpServletResponse call(ApiRateLimitFilter f, String uri, String ip) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", uri);
        req.setRemoteAddr(ip);
        MockHttpServletResponse res = new MockHttpServletResponse();
        f.doFilter(req, res, new MockFilterChain());
        return res;
    }

    @Test
    @DisplayName("Vượt hạn mức trong một phút thì trả 429 kèm Retry-After; IP khác và phút sau không bị ảnh hưởng")
    void limitsPerIpPerMinute() throws Exception {
        Clock t0 = Clock.fixed(Instant.parse("2026-11-05T10:00:05Z"), ZoneOffset.UTC);
        ApiRateLimitFilter f = new ApiRateLimitFilter(3, t0);
        for (int i = 0; i < 3; i++) assertThat(call(f, "/api/products", "10.0.0.1").getStatus()).isEqualTo(200);

        MockHttpServletResponse blocked = call(f, "/api/products", "10.0.0.1");
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("55");
        assertThat(blocked.getContentAsString()).contains("RATE_LIMITED");

        assertThat(call(f, "/api/products", "10.0.0.2").getStatus()).isEqualTo(200);
        assertThat(call(f, "/actuator/health", "10.0.0.1").getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Sang phút mới thì đếm lại; per-minute = 0 thì tắt")
    void resetsAndCanBeDisabled() throws Exception {
        ApiRateLimitFilter f1 = new ApiRateLimitFilter(1, Clock.fixed(Instant.parse("2026-11-05T10:00:59Z"), ZoneOffset.UTC));
        call(f1, "/api/x", "1.1.1.1");
        assertThat(call(f1, "/api/x", "1.1.1.1").getStatus()).isEqualTo(429);

        ApiRateLimitFilter off = new ApiRateLimitFilter(0, Clock.systemUTC());
        for (int i = 0; i < 50; i++) assertThat(call(off, "/api/x", "1.1.1.1").getStatus()).isEqualTo(200);
    }
}
