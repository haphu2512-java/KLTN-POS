package com.pos.auth;

import com.pos.common.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoginAttemptServiceTest {

    /** Đồng hồ chỉnh tay được để thử hết hạn khóa */
    private static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-28T10:00:00Z");
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Test
    @DisplayName("Sai đủ số lần thì bị khóa (429), hết thời gian khóa thì đăng nhập lại được")
    void locksAfterMaxFailuresThenUnlocks() {
        MutableClock clock = new MutableClock();
        LoginAttemptService service = new LoginAttemptService(3, Duration.ofMinutes(15), clock);

        service.recordFailure("cashier");
        service.recordFailure("CASHIER ");
        assertDoesNotThrow(() -> service.checkNotLocked("cashier"));

        service.recordFailure("cashier");
        BusinessException ex = assertThrows(BusinessException.class, () -> service.checkNotLocked("cashier"));
        assertEquals("AUTH_TOO_MANY_ATTEMPTS", ex.getCode());
        assertDoesNotThrow(() -> service.checkNotLocked("admin"), "Chỉ khóa tài khoản bị nhập sai");

        clock.now = clock.now.plus(Duration.ofMinutes(16));
        assertDoesNotThrow(() -> service.checkNotLocked("cashier"));
    }

    @Test
    @DisplayName("Đăng nhập đúng thì xóa số lần sai trước đó")
    void successResetsFailures() {
        LoginAttemptService service = new LoginAttemptService(2, Duration.ofMinutes(15), new MutableClock());
        service.recordFailure("cashier");
        service.recordSuccess("cashier");
        service.recordFailure("cashier");
        assertDoesNotThrow(() -> service.checkNotLocked("cashier"));
    }

    @Test
    @DisplayName("max-attempts = 0 thì tắt hẳn (dùng khi dev)")
    void disabledWhenZero() {
        LoginAttemptService service = new LoginAttemptService(0, Duration.ofMinutes(15), new MutableClock());
        for (int i = 0; i < 20; i++) service.recordFailure("cashier");
        assertDoesNotThrow(() -> service.checkNotLocked("cashier"));
    }
}
