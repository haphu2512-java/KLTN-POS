package com.pos.config;

import com.pos.scanner.TerminalAccess;
import com.pos.security.BranchSession;
import com.pos.security.SessionAuthenticator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.security.Principal;
import java.util.Arrays;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final SessionAuthenticator sessionAuthenticator;
    private final TerminalAccess terminalAccess;

    // Dùng chung danh sách origin với CORS thay vì cho phép mọi origin ("*")
    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);

        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(origins)
                .withSockJS();

        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(origins);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Topic for broadcasting realtime updates to all connected cashiers and branches
        // /queue dùng cho tin riêng từng tài khoản (/user/queue/...), ví dụ mã quét từ điện thoại
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
    }

    /**
     * Bắt buộc gửi JWT trong header "Authorization: Bearer ..." của frame STOMP CONNECT.
     * Kết nối không có token hợp lệ (hoặc tài khoản bị khóa) sẽ bị từ chối.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null) {
                    return message;
                }
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    accessor.setUser(authenticate(accessor.getFirstNativeHeader("Authorization")));
                } else if (StompCommand.SEND.equals(accessor.getCommand())) {
                    checkSendDestination(accessor.getDestination());
                } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    checkSubscribeDestination(accessor.getDestination(), accessor.getUser());
                }
                return message;
            }
        });
    }

    /**
     * Client chỉ được gửi tới /app/** (hiện chưa có @MessageMapping nào). Không cho client gửi thẳng vào
     * /topic, /queue hay /user/{username}/..., vì broker sẽ chuyển tiếp nguyên văn: một tài khoản bất kỳ
     * có thể chèn mã quét vào giỏ POS của tài khoản khác hoặc phát tin tồn kho giả.
     */
    static void checkSendDestination(String destination) {
        if (destination == null || !destination.startsWith("/app/")) {
            throw new MessagingException("Không được gửi tin tới " + destination);
        }
    }

    /**
     * Chỉ cho subscribe /topic/** và hàng đợi riêng của chính mình qua /user/queue/**.
     * Subscribe thẳng /queue/... (tên hàng đợi đã gắn session của người khác) bị chặn.
     * Không cho dùng ký tự đại diện (broker so khớp kiểu Ant: "/topic/**" sẽ nghe trộm mọi quầy).
     * Kênh mã quét của quầy (/topic/terminals/{id}/scans) chỉ cho phiên đăng nhập cùng chi nhánh với quầy.
     */
    void checkSubscribeDestination(String destination, Principal user) {
        if (destination == null
                || !(destination.startsWith("/topic/") || destination.startsWith("/user/queue/"))
                || destination.contains("*") || destination.contains("?") || destination.contains("{")) {
            throw new MessagingException("Không được đăng ký nhận tin từ " + destination);
        }
        String username = user == null ? null : user.getName();
        Long branchId = user instanceof UsernamePasswordAuthenticationToken auth
                && auth.getDetails() instanceof BranchSession session ? session.branchId() : null;
        if (destination.startsWith(TerminalAccess.TOPIC_PREFIX)
                && !terminalAccess.canSubscribe(username, branchId, destination)) {
            throw new MessagingException("Không được nghe mã quét của quầy này");
        }
    }

    private UsernamePasswordAuthenticationToken authenticate(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new MessagingException("Thiếu token xác thực WebSocket");
        }
        UsernamePasswordAuthenticationToken auth = sessionAuthenticator.authenticate(authHeader.substring(7));
        if (auth == null) {
            throw new MessagingException("Token WebSocket không hợp lệ, đã hết hạn hoặc tài khoản không còn quyền ở chi nhánh này");
        }
        return auth;
    }
}
