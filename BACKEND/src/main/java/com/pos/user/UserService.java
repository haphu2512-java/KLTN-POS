package com.pos.user;

import com.pos.branch.BranchRepository;
import com.pos.common.BusinessException;
import com.pos.shift.ShiftSession;
import com.pos.shift.ShiftSessionRepository;
import com.pos.shift.ShiftStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ShiftSessionRepository shiftSessionRepository;
    private final BranchRepository branchRepository;

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
                .fullName(req.getFullName() == null || req.getFullName().isBlank()
                        ? req.getUsername().trim() : req.getFullName().trim())
                .role(req.getRole())
                .branchIds(validBranches(req.getRole(), req.getBranchIds()))
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

        Set<Long> branchIds = validBranches(req.getRole(), req.getBranchIds());

        // Bỏ phân công ở chi nhánh đang có ca mở thì nhân viên mất phiên ở đó (token hết hiệu lực)
        // và không còn đăng nhập vào để đóng ca được, nên phải đóng ca trước
        ShiftSession open = shiftSessionRepository.findByEmployeeIdAndStatus(user.getId(), ShiftStatus.OPEN).orElse(null);
        if (open != null && req.getRole() != Role.ADMIN && !branchIds.contains(open.getBranchId())) {
            throw new BusinessException("SHIFT_OPEN_BRANCH_CHANGE",
                    "Nhân viên đang có ca chưa đóng ở chi nhánh bị bỏ. Đóng ca trước khi bỏ phân công chi nhánh này.",
                    HttpStatus.CONFLICT);
        }

        if (req.getFullName() != null && !req.getFullName().isBlank()) {
            user.setFullName(req.getFullName().trim());
        }
        user.setRole(req.getRole());
        user.getBranchIds().clear();
        user.getBranchIds().addAll(branchIds);

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

    /** ADMIN làm việc được mọi chi nhánh nên không lưu phân công; vai trò khác cần ít nhất 1 chi nhánh có thật */
    private Set<Long> validBranches(Role role, Set<Long> requested) {
        if (role == Role.ADMIN) {
            return new HashSet<>();
        }
        if (requested == null || requested.isEmpty()) {
            throw new BusinessException("BRANCH_REQUIRED", "Phải phân công ít nhất một chi nhánh cho nhân viên");
        }
        Set<Long> result = new HashSet<>();
        for (Long id : requested) {
            if (id == null || !branchRepository.existsById(id)) {
                throw new BusinessException("BRANCH_NOT_FOUND", "Chi nhánh không tồn tại: " + id);
            }
            result.add(id);
        }
        return result;
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
