package com.pos.scanner;

import com.pos.user.User;
import com.pos.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Quy tắc ai được dùng quầy nào: tài khoản không gắn chi nhánh (quản trị) dùng được mọi quầy,
 * còn lại chỉ quầy cùng chi nhánh. Tách riêng (chỉ phụ thuộc repository) để WebSocketConfig dùng được.
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

    public static boolean canUse(User user, PosTerminal terminal) {
        return user.getBranchId() == null || Objects.equals(user.getBranchId(), terminal.getBranchId());
    }

    /** POS chỉ được nghe kênh của quầy tồn tại và cùng chi nhánh với tài khoản đang đăng nhập */
    public boolean canSubscribe(String username, String destination) {
        if (username == null || !destination.startsWith(TOPIC_PREFIX) || !destination.endsWith(TOPIC_SUFFIX)) {
            return false;
        }
        String terminalId = destination.substring(TOPIC_PREFIX.length(), destination.length() - TOPIC_SUFFIX.length());
        if (terminalId.isEmpty() || terminalId.contains("/")) {
            return false;
        }
        return userRepository.findByUsername(username)
                .flatMap(user -> terminalRepository.findById(terminalId).map(t -> canUse(user, t)))
                .orElse(false);
    }
}
