package com.pos.auth;

import com.pos.branch.Branch;
import com.pos.branch.BranchRepository;
import com.pos.common.ApiResponse;
import com.pos.security.CurrentUserService;
import com.pos.user.User;
import com.pos.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
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
    private final UserRepository userRepository;
    private final LoginService loginService;
    private final CurrentUserService currentUserService;
    private final BranchRepository branchRepository;
    private final LoginAttemptService loginAttemptService;

    @PostMapping("/login")
    @Operation(summary = "Đăng nhập và chọn chi nhánh làm việc, nhận JWT Token",
            description = "Tài khoản có nhiều chi nhánh mà chưa gửi branchId: trả 409 BRANCH_SELECTION_REQUIRED "
                    + "kèm data.branches để chọn rồi gửi lại.")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        loginAttemptService.checkNotLocked(request.getUsername());
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
        } catch (BadCredentialsException e) {
            loginAttemptService.recordFailure(request.getUsername());
            throw e;
        }
        loginAttemptService.recordSuccess(request.getUsername());

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy người dùng"));

        AuthResponse authResponse = loginService.login(user, userDetails, request.getBranchId());
        return ResponseEntity.ok(ApiResponse.ok("Đăng nhập thành công", authResponse));
    }

    @GetMapping("/me")
    @Operation(summary = "Lấy thông tin tài khoản và chi nhánh của phiên hiện tại từ Token")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getCurrentUser(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.ok(ApiResponse.error("UNAUTHORIZED", "Chưa đăng nhập"));
        }
        User user = currentUserService.getCurrentUser();
        Long branchId = user.getCurrentBranchId();

        UserProfileResponse profile = UserProfileResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .role(user.getRole() != null ? user.getRole().name() : null)
                .branchId(branchId)
                .branchName(branchId == null ? null : branchRepository.findById(branchId).map(Branch::getName).orElse(null))
                .build();

        return ResponseEntity.ok(ApiResponse.ok(profile));
    }
}
