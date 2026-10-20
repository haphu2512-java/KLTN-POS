package com.pos.security;

import com.pos.common.BusinessException;
import com.pos.user.User;
import com.pos.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Lấy tài khoản đang đăng nhập từ SecurityContext (đã được JwtAuthFilter xác thực).
 * Dùng để server tự quyết định nhân viên / chi nhánh thay vì tin dữ liệu client gửi lên.
 */
@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final UserRepository userRepository;

    public User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
            throw new BusinessException("UNAUTHORIZED", "Chưa đăng nhập", HttpStatus.UNAUTHORIZED);
        }
        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new BusinessException("UNAUTHORIZED", "Tài khoản không tồn tại", HttpStatus.UNAUTHORIZED));
    }
}
