package com.pos.auth;

import com.pos.common.ApiResponse;
import com.pos.security.JwtTokenProvider;
import com.pos.user.User;
import com.pos.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Endpoints xác thực và đăng nhập thu ngân / quản lý")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;

    @PostMapping("/login")
    @Operation(summary = "Đăng nhập hệ thống nhận JWT Token")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy người dùng"));

        String token = tokenProvider.generateToken(
                userDetails,
                user.getBranchId(),
                user.getRole().name(),
                user.getFullName()
        );

        AuthResponse authResponse = AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .userId(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .branchId(user.getBranchId())
                .build();

        return ResponseEntity.ok(ApiResponse.ok("Đăng nhập thành công", authResponse));
    }

    @GetMapping("/me")
    @Operation(summary = "Lấy thông tin tài khoản hiện tại từ Token")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getCurrentUser(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.ok(ApiResponse.error("UNAUTHORIZED", "Chưa đăng nhập"));
        }
        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy người dùng"));
        
        UserProfileResponse profile = UserProfileResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .role(user.getRole() != null ? user.getRole().name() : null)
                .branchId(user.getBranchId())
                .build();
                
        return ResponseEntity.ok(ApiResponse.ok(profile));
    }
}
