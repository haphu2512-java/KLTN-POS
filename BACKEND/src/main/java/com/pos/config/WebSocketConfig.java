package com.pos.config;

import com.pos.security.CustomUserDetailsService;
import com.pos.security.JwtTokenProvider;
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
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtTokenProvider tokenProvider;
    private final CustomUserDetailsService userDetailsService;

    // Dùng chung danh sách origin với CORS thay vì cho phép mọi origin ("*")
    @Value("${cors.allowed-origins:http://localhost:*,http://127.0.0.1:*,http://192.168.*.*:*,http://10.*.*.*:*,http://172.*.*.*:*,file://*}")
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
                    checkSubscribeDestination(accessor.getDestination());
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
     */
    static void checkSubscribeDestination(String destination) {
        if (destination == null
                || !(destination.startsWith("/topic/") || destination.startsWith("/user/queue/"))) {
            throw new MessagingException("Không được đăng ký nhận tin từ " + destination);
        }
    }

    private UsernamePasswordAuthenticationToken authenticate(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new MessagingException("Thiếu token xác thực WebSocket");
        }
        String token = authHeader.substring(7);
        if (!tokenProvider.validateToken(token)) {
            throw new MessagingException("Token WebSocket không hợp lệ hoặc đã hết hạn");
        }
        try {
            UserDetails user = userDetailsService.loadUserByUsername(tokenProvider.getUsernameFromToken(token));
            if (!user.isEnabled()) {
                throw new MessagingException("Tài khoản đã bị khóa");
            }
            return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        } catch (UsernameNotFoundException e) {
            throw new MessagingException("Tài khoản không tồn tại");
        }
    }
}
