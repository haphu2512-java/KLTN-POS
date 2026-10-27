package com.pos.scanner;

import com.pos.common.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chỉ cho điện thoại bắn mã vào quầy POS khi điện thoại đang ở cùng mạng (LAN) với máy POS đó.
 * <p>
 * Server không thấy được mạng Wi-Fi của thiết bị, chỉ thấy địa chỉ IP gửi request tới. Địa chỉ của máy POS lấy
 * từ kết nối WebSocket đang nghe kênh của quầy (dự phòng: lần gọi API gần nhất của máy POS với quầy đó).
 * Hai địa chỉ được coi là cùng mạng khi:
 * <ul>
 *   <li>trùng nhau (vd. backend đặt trên cloud, cửa hàng ra Internet qua cùng một IP công cộng của router);</li>
 *   <li>cùng là IPv4 nội bộ (10.x, 172.16–31.x, 192.168.x) và cùng dải /{@code ipv4-prefix} (mặc định /24);</li>
 *   <li>cùng dải IPv6 /{@code ipv6-prefix} (mặc định /64, tức cùng một mạng LAN).</li>
 * </ul>
 * Máy POS chạy chung máy với backend sẽ hiện là 127.0.0.1; khi đó so với các địa chỉ LAN của chính máy chủ.
 */
@Component
@Slf4j
public class ScannerNetworkGuard {

    /** Khóa lưu IP của máy POS trong thuộc tính phiên WebSocket (ghi lúc bắt tay) */
    public static final String SESSION_IP_ATTRIBUTE = "pos.clientIp";

    /** Máy POS không giữ WebSocket thì dùng IP của lần gọi API gần nhất, nhưng không quá lâu */
    static final Duration HTTP_ADDRESS_TTL = Duration.ofMinutes(15);

    private final boolean enabled;
    private final int ipv4Prefix;
    private final int ipv6Prefix;
    private final boolean trustForwardedFor;

    /** sessionId WebSocket → (subscriptionId → quầy và IP) */
    private final Map<String, Map<String, Presence>> liveSubscriptions = new ConcurrentHashMap<>();
    /** quầy → IP của lần gọi API gần nhất từ máy POS */
    private final Map<String, Seen> lastHttpAddress = new ConcurrentHashMap<>();

    record Presence(String terminalId, String ip) {
    }

    record Seen(String ip, Instant at) {
    }

    public ScannerNetworkGuard(@Value("${scanner.same-network.enabled:true}") boolean enabled,
                               @Value("${scanner.same-network.ipv4-prefix:24}") int ipv4Prefix,
                               @Value("${scanner.same-network.ipv6-prefix:64}") int ipv6Prefix,
                               @Value("${scanner.same-network.trust-forwarded-for:false}") boolean trustForwardedFor) {
        this.enabled = enabled;
        this.ipv4Prefix = Math.max(0, Math.min(32, ipv4Prefix));
        this.ipv6Prefix = Math.max(0, Math.min(128, ipv6Prefix));
        this.trustForwardedFor = trustForwardedFor;
        if (!enabled) {
            log.warn("Đã TẮT kiểm tra cùng mạng: điện thoại ở bất kỳ đâu cũng bắn mã được vào quầy POS");
        }
    }

    // ===== Địa chỉ IP của request =====

    /** IP của request HTTP đang xử lý (null nếu không ở trong request) */
    public String currentRequestIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return clientIp(attrs.getRequest());
        }
        return null;
    }

    String clientIp(HttpServletRequest request) {
        return pickIp(request.getHeader("X-Forwarded-For"), request.getRemoteAddr());
    }

    /** IP của máy POS lúc bắt tay WebSocket */
    public String clientIp(ServerHttpRequest request) {
        InetSocketAddress remote = request.getRemoteAddress();
        String remoteIp = remote == null || remote.getAddress() == null ? null : remote.getAddress().getHostAddress();
        return pickIp(request.getHeaders().getFirst("X-Forwarded-For"), remoteIp);
    }

    /**
     * Chỉ đọc X-Forwarded-For khi backend đứng sau reverse proxy tin cậy; nếu không, ai cũng tự khai được IP LAN.
     * Lấy phần tử đầu tiên (IP thật của thiết bị theo proxy).
     */
    private String pickIp(String forwardedFor, String remoteAddr) {
        if (trustForwardedFor && forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return remoteAddr;
    }

    // ===== Ghi nhận địa chỉ của máy POS =====

    /** Máy POS gọi API với quầy của nó (đăng ký, lấy thông tin, tạo mã ghép cặp...) */
    public void recordTerminalHttp(String terminalId) {
        String ip = currentRequestIp();
        if (terminalId != null && ip != null) {
            lastHttpAddress.put(terminalId, new Seen(ip, Instant.now()));
        }
    }

    /** Máy POS subscribe kênh /topic/terminals/{id}/scans */
    public void recordSubscription(String sessionId, String subscriptionId, String terminalId, String ip) {
        if (sessionId == null || subscriptionId == null || terminalId == null || ip == null) {
            return;
        }
        liveSubscriptions.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>())
                .put(subscriptionId, new Presence(terminalId, ip));
    }

    public void removeSubscription(String sessionId, String subscriptionId) {
        if (sessionId == null || subscriptionId == null) {
            return;
        }
        Map<String, Presence> subs = liveSubscriptions.get(sessionId);
        if (subs != null) {
            subs.remove(subscriptionId);
        }
    }

    public void removeSession(String sessionId) {
        if (sessionId != null) {
            liveSubscriptions.remove(sessionId);
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        removeSession(event.getSessionId());
    }

    /** IP của các phiên WebSocket đang nghe kênh quầy */
    Set<String> liveAddresses(String terminalId) {
        Set<String> ips = new LinkedHashSet<>();
        liveSubscriptions.values().forEach(subs -> subs.values().stream()
                .filter(p -> p.terminalId().equals(terminalId))
                .forEach(p -> ips.add(p.ip())));
        return ips;
    }

    /** Các IP hiện biết của máy POS ở quầy này: WebSocket đang mở, hoặc lần gọi API gần đây */
    Set<String> terminalAddresses(String terminalId) {
        Set<String> ips = liveAddresses(terminalId);
        if (ips.isEmpty()) {
            Seen seen = lastHttpAddress.get(terminalId);
            if (seen != null && seen.at().isAfter(Instant.now().minus(HTTP_ADDRESS_TTL))) {
                ips.add(seen.ip());
            }
        }
        return ips;
    }

    // ===== Kiểm tra =====

    /** Điện thoại (request hiện tại) phải cùng mạng với máy POS của quầy; nếu không ném lỗi và không gửi gì */
    public void requireSameNetwork(PosTerminal terminal) {
        if (!enabled) {
            return;
        }
        Set<String> posIps = terminalAddresses(terminal.getId());
        if (posIps.isEmpty()) {
            throw new BusinessException("POS_NOT_CONNECTED",
                    "Máy POS \"" + terminal.getName() + "\" chưa kết nối. Hãy mở ứng dụng POS rồi quét lại.",
                    HttpStatus.CONFLICT);
        }
        String phoneIp = currentRequestIp();
        boolean same = phoneIp != null && posIps.stream().anyMatch(posIp -> sameNetwork(phoneIp, posIp));
        if (!same) {
            log.warn("Từ chối mã quét cho quầy {} từ {}: khác mạng với máy POS {}", terminal.getId(), phoneIp, posIps);
            throw new BusinessException("NOT_SAME_NETWORK",
                    "Điện thoại không cùng mạng với máy POS \"" + terminal.getName()
                            + "\". Hãy kết nối điện thoại vào Wi-Fi của cửa hàng (cùng mạng với máy POS) rồi quét lại.",
                    HttpStatus.FORBIDDEN);
        }
    }

    boolean sameNetwork(String phoneIp, String posIp) {
        InetAddress phone = parse(phoneIp);
        InetAddress pos = parse(posIp);
        if (phone == null || pos == null) {
            return false;
        }
        if (phone.equals(pos)) {
            return true;
        }
        // POS (hoặc điện thoại) chạy ngay trên máy chủ: thay bằng các địa chỉ LAN của máy chủ
        List<InetAddress> phones = expandLocal(phone);
        List<InetAddress> poses = expandLocal(pos);
        for (InetAddress a : phones) {
            for (InetAddress b : poses) {
                if (a.equals(b) || sameLan(a, b)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean sameLan(InetAddress a, InetAddress b) {
        if (a instanceof Inet4Address && b instanceof Inet4Address) {
            // IP công cộng chỉ tính là cùng mạng khi trùng hẳn (cùng router NAT)
            return isPrivate(a) && isPrivate(b) && samePrefix(a.getAddress(), b.getAddress(), ipv4Prefix);
        }
        if (a instanceof Inet6Address && b instanceof Inet6Address) {
            return !a.isLinkLocalAddress() && !b.isLinkLocalAddress()
                    && samePrefix(a.getAddress(), b.getAddress(), ipv6Prefix);
        }
        return false;
    }

    private static boolean isPrivate(InetAddress a) {
        return a.isSiteLocalAddress();
    }

    static boolean samePrefix(byte[] a, byte[] b, int prefix) {
        if (a.length != b.length) {
            return false;
        }
        int fullBytes = prefix / 8;
        for (int i = 0; i < fullBytes; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        int rest = prefix % 8;
        if (rest == 0) {
            return true;
        }
        int mask = (0xFF << (8 - rest)) & 0xFF;
        return (a[fullBytes] & mask) == (b[fullBytes] & mask);
    }

    private List<InetAddress> expandLocal(InetAddress address) {
        if (!isServerLocal(address)) {
            return List.of(address);
        }
        List<InetAddress> result = new ArrayList<>(serverLanAddresses());
        result.add(address);
        return result;
    }

    private boolean isServerLocal(InetAddress address) {
        return address.isLoopbackAddress() || serverLanAddresses().contains(address);
    }

    /** Địa chỉ của các card mạng đang bật trên máy chủ (bỏ loopback) */
    List<InetAddress> serverLanAddresses() {
        List<InetAddress> result = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface nic = interfaces.nextElement();
                if (!nic.isUp() || nic.isLoopback()) {
                    continue;
                }
                Collections.list(nic.getInetAddresses()).stream()
                        .filter(a -> !a.isLoopbackAddress() && !a.isLinkLocalAddress())
                        .forEach(result::add);
            }
        } catch (Exception e) {
            log.debug("Không đọc được card mạng của máy chủ: {}", e.getMessage());
        }
        return result;
    }

    /** Chỉ nhận chuỗi IP dạng số (không tra DNS); IPv4 nhúng trong IPv6 (::ffff:a.b.c.d) được Java đổi về IPv4 */
    static InetAddress parse(String ip) {
        if (ip == null) {
            return null;
        }
        String clean = ip.trim();
        int zone = clean.indexOf('%');
        if (zone >= 0) {
            clean = clean.substring(0, zone);
        }
        if (clean.startsWith("[") && clean.endsWith("]")) {
            clean = clean.substring(1, clean.length() - 1);
        }
        if (clean.isEmpty() || !clean.matches("[0-9a-fA-F:.]+")) {
            return null;
        }
        try {
            return InetAddress.getByName(clean);
        } catch (Exception e) {
            return null;
        }
    }
}
