package com.pos.scanner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScannerNetworkGuardTest {

    private final ScannerNetworkGuard guard = new ScannerNetworkGuard(true, 24, 64, false);

    @Test
    @DisplayName("Cùng dải LAN /24 hoặc trùng IP công cộng (cùng router) là cùng mạng")
    void sameNetwork() {
        assertThat(guard.sameNetwork("192.168.1.20", "192.168.1.5")).isTrue();
        assertThat(guard.sameNetwork("10.10.3.200", "10.10.3.1")).isTrue();
        assertThat(guard.sameNetwork("113.161.5.6", "113.161.5.6")).isTrue();
        assertThat(guard.sameNetwork("::ffff:192.168.1.20", "192.168.1.5")).isTrue();
        assertThat(guard.sameNetwork("2001:db8:1:2::10", "2001:db8:1:2::99")).isTrue();
    }

    @Test
    @DisplayName("Khác dải LAN, 4G hay IP công cộng gần nhau đều là khác mạng")
    void differentNetwork() {
        assertThat(guard.sameNetwork("192.168.2.20", "192.168.1.5")).isFalse();
        assertThat(guard.sameNetwork("10.0.0.5", "192.168.1.5")).isFalse();
        assertThat(guard.sameNetwork("113.161.5.7", "113.161.5.6")).isFalse();
        assertThat(guard.sameNetwork("113.161.5.6", "192.168.1.5")).isFalse();
        assertThat(guard.sameNetwork("2001:db8:1:3::10", "2001:db8:1:2::99")).isFalse();
    }

    @Test
    @DisplayName("Chuỗi không phải IP (tên miền, rỗng) không bao giờ khớp và không tra DNS")
    void rejectsNonIp() {
        assertThat(guard.sameNetwork("localhost", "127.0.0.1")).isFalse();
        assertThat(guard.sameNetwork(null, "127.0.0.1")).isFalse();
        assertThat(guard.sameNetwork("", "")).isFalse();
    }

    @Test
    @DisplayName("Máy POS chạy chung máy với backend (127.0.0.1): so với địa chỉ LAN của máy chủ")
    void posOnServerHost() {
        guard.serverLanAddresses().stream()
                .filter(a -> a instanceof java.net.Inet4Address && a.isSiteLocalAddress())
                .findFirst()
                .ifPresent(lan -> {
                    byte[] b = lan.getAddress();
                    String neighbour = (b[0] & 0xFF) + "." + (b[1] & 0xFF) + "." + (b[2] & 0xFF) + "." + (((b[3] & 0xFF) + 1) % 255);
                    assertThat(guard.sameNetwork(neighbour, "127.0.0.1")).isTrue();
                });
        assertThat(guard.sameNetwork("127.0.0.1", "127.0.0.1")).isTrue();
        assertThat(guard.sameNetwork("113.161.5.6", "127.0.0.1")).isFalse();
    }

    @Test
    @DisplayName("Máy POS chưa có địa chỉ nào: không có quầy để so")
    void unknownTerminal() {
        assertThat(guard.terminalAddresses("chua-ket-noi")).isEmpty();
        guard.recordSubscription("s1", "sub-1", "quay-1", "192.168.1.5");
        assertThat(guard.terminalAddresses("quay-1")).containsExactly("192.168.1.5");
        guard.removeSession("s1");
        assertThat(guard.terminalAddresses("quay-1")).isEmpty();
    }
}
