package com.pos.user;

import com.pos.common.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(UserResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "Không tìm thấy người dùng với ID: " + id));
        return UserResponse.fromEntity(user);
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest req) {
        if (userRepository.existsByUsername(req.getUsername().trim())) {
            throw new BusinessException("USERNAME_EXISTS", "Tên đăng nhập '" + req.getUsername() + "' đã tồn tại");
        }

        User user = User.builder()
                .username(req.getUsername().trim())
                .password(passwordEncoder.encode(req.getPassword()))
                .fullName(req.getFullName().trim())
                .role(req.getRole())
                .branchId(req.getBranchId())
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();

        User saved = userRepository.save(user);
        log.info("Tạo mới nhân viên thành công: username={}, role={}", saved.getUsername(), saved.getRole());
        return UserResponse.fromEntity(saved);
    }

    @Transactional
    public UserResponse updateUser(Long id, UpdateUserRequest req) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "Không tìm thấy người dùng với ID: " + id));

        user.setFullName(req.getFullName().trim());
        user.setRole(req.getRole());
        user.setBranchId(req.getBranchId());

        if (req.getActive() != null) {
            user.setActive(req.getActive());
        }

        if (req.getNewPassword() != null && !req.getNewPassword().trim().isEmpty()) {
            if (req.getNewPassword().trim().length() < 6) {
                throw new BusinessException("PASSWORD_TOO_SHORT", "Mật khẩu mới phải từ 6 ký tự trở lên");
            }
            user.setPassword(passwordEncoder.encode(req.getNewPassword().trim()));
        }

        User updated = userRepository.save(user);
        log.info("Cập nhật thông tin nhân viên ID={}: role={}, active={}", id, updated.getRole(), updated.getActive());
        return UserResponse.fromEntity(updated);
    }

    @Transactional
    public UserResponse toggleUserStatus(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "Không tìm thấy người dùng với ID: " + id));

        user.setActive(!Boolean.TRUE.equals(user.getActive()));
        User updated = userRepository.save(user);
        log.info("Thay đổi trạng thái tài khoản ID={}: active={}", id, updated.getActive());
        return UserResponse.fromEntity(updated);
    }

    @Transactional
    public void changePassword(Long id, String newPassword) {
        if (newPassword == null || newPassword.trim().length() < 6) {
            throw new BusinessException("PASSWORD_TOO_SHORT", "Mật khẩu mới phải từ 6 ký tự trở lên");
        }

        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "Không tìm thấy người dùng với ID: " + id));

        user.setPassword(passwordEncoder.encode(newPassword.trim()));
        userRepository.save(user);
        log.info("Đổi mật khẩu thành công cho tài khoản ID={}", id);
    }
}
