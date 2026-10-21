package com.pos.security;

import com.pos.branch.BranchAccessService;
import com.pos.user.User;
import com.pos.user.UserRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/**
 * Biến token thành phiên đăng nhập, dùng chung cho REST (JwtAuthFilter) và WebSocket.
 * Token chỉ hợp lệ khi tài khoản còn hoạt động và vẫn được phép làm việc ở chi nhánh ghi trong token:
 * quản trị bỏ gán chi nhánh hoặc ngưng chi nhánh thì phiên đó hết hiệu lực ngay.
 */
@Component
@RequiredArgsConstructor
public class SessionAuthenticator {

    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final BranchAccessService branchAccessService;

    /** @return phiên đã xác thực, hoặc null nếu token không còn dùng được */
    public UsernamePasswordAuthenticationToken authenticate(String token) {
        if (token == null || !tokenProvider.validateToken(token)) {
            return null;
        }
        Claims claims = tokenProvider.getClaims(token);
        User user = userRepository.findByUsername(claims.getSubject()).orElse(null);
        if (user == null || !Boolean.TRUE.equals(user.getActive())) {
            return null;
        }
        Long branchId = tokenProvider.getBranchId(claims);
        if (!branchAccessService.canUse(user, branchId)) {
            return null;
        }
        UserDetails details = CustomUserDetailsService.toUserDetails(user);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
        auth.setDetails(new BranchSession(branchId));
        return auth;
    }
}
