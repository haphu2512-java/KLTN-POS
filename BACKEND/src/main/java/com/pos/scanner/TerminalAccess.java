package com.pos.scanner;

import com.pos.user.Role;
import com.pos.user.User;
import com.pos.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Quy tắc ai được dùng quầy nào: quản trị dùng được mọi quầy, còn lại chỉ quầy thuộc chi nhánh
 * của phiên đăng nhập. Tách riêng (chỉ phụ thuộc repository) để WebSocketConfig dùng được.
 */
@Component
@RequiredArgsConstructor
public class TerminalAccess {

    public static final String TOPIC_PREFIX = "/topic/terminals/";
    public static final String TOPIC_SUFFIX = "/scans";

    private final PosTerminalRepository terminalRepository;
    private final UserRepository userRepository;

    public static String topicOf(String terminalId) {
        return TOPIC_PREFIX + terminalId + TOPIC_SUFFIX;
    }

    /** id quầy trong "/topic/terminals/{id}/scans", null nếu không đúng dạng */
    public static String terminalIdOf(String destination) {
        if (destination == null || !destination.startsWith(TOPIC_PREFIX) || !destination.endsWith(TOPIC_SUFFIX)
                || destination.length() < TOPIC_PREFIX.length() + TOPIC_SUFFIX.length()) {
            return null;
        }
        String terminalId = destination.substring(TOPIC_PREFIX.length(), destination.length() - TOPIC_SUFFIX.length());
        return terminalId.isEmpty() || terminalId.contains("/") ? null : terminalId;
    }

    public static boolean canUse(User user, PosTerminal terminal) {
        return canUse(user.getRole(), user.getCurrentBranchId(), terminal);
    }

    static boolean canUse(Role role, Long sessionBranchId, PosTerminal terminal) {
        return role == Role.ADMIN || Objects.equals(sessionBranchId, terminal.getBranchId());
    }

    /** POS chỉ được nghe kênh của quầy tồn tại và cùng chi nhánh với phiên đăng nhập */
    public boolean canSubscribe(String username, Long sessionBranchId, String destination) {
        String terminalId = terminalIdOf(destination);
        if (username == null || terminalId == null) {
            return false;
        }
        return userRepository.findByUsername(username)
                .flatMap(user -> terminalRepository.findById(terminalId)
                        .map(t -> canUse(user.getRole(), sessionBranchId, t)))
                .orElse(false);
    }
}
