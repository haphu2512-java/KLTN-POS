package com.pos.user;

import com.pos.branch.BranchRepository;
import com.pos.common.BusinessException;
import com.pos.shift.ShiftSession;
import com.pos.shift.ShiftSessionRepository;
import com.pos.shift.ShiftStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ShiftSessionRepository shiftSessionRepository;

    @Mock
    private BranchRepository branchRepository;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("Tạo nhân viên thành công: mã hóa mật khẩu và lưu DB")
    void testCreateUserSuccess() {
        CreateUserRequest req = CreateUserRequest.builder()
                .username("cashier2")
                .password("plain123")
                .fullName("Lê Thu Ngân")
                .role(Role.CASHIER)
                .branchIds(Set.of(1L, 2L))
                .build();

        when(userRepository.existsByUsername("cashier2")).thenReturn(false);
        when(branchRepository.existsById(anyLong())).thenReturn(true);
        when(passwordEncoder.encode("plain123")).thenReturn("hashed123");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(2L);
            return u;
        });

        UserResponse res = userService.createUser(req);

        assertNotNull(res);
        assertEquals("cashier2", res.getUsername());
        assertEquals("Lê Thu Ngân", res.getFullName());
        assertEquals(Role.CASHIER, res.getRole());
        assertTrue(res.getActive());
        assertEquals(List.of(1L, 2L), res.getBranchIds());
        verify(passwordEncoder).encode("plain123");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Tạo tài khoản không cần họ tên: tên hiển thị lấy theo tên đăng nhập")
    void testCreateUserWithoutFullName() {
        CreateUserRequest req = CreateUserRequest.builder()
                .username("quay1")
                .password("plain123")
                .role(Role.CASHIER)
                .branchIds(Set.of(1L))
                .build();

        when(userRepository.existsByUsername("quay1")).thenReturn(false);
        when(branchRepository.existsById(anyLong())).thenReturn(true);
        when(passwordEncoder.encode("plain123")).thenReturn("hashed123");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        assertEquals("quay1", userService.createUser(req).getFullName());
    }

    @Test
    @DisplayName("Ném BusinessException khi username đã tồn tại")
    void testCreateUserDuplicateUsername() {
        CreateUserRequest req = CreateUserRequest.builder()
                .username("admin")
                .password("123456")
                .fullName("Admin User")
                .role(Role.ADMIN)
                .build();

        when(userRepository.existsByUsername("admin")).thenReturn(true);

        assertThrows(BusinessException.class, () -> userService.createUser(req));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Toggle trạng thái active của nhân viên thành công")
    void testToggleUserStatus() {
        User user = User.builder()
                .id(5L)
                .username("cashier5")
                .active(true)
                .build();

        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse res = userService.toggleUserStatus(5L);

        assertFalse(res.getActive());
        verify(userRepository).save(user);
    }

    private UpdateUserRequest assign(Long... branchIds) {
        UpdateUserRequest req = new UpdateUserRequest();
        req.setFullName("Thu Ngân");
        req.setRole(Role.CASHIER);
        req.setBranchIds(Set.of(branchIds));
        return req;
    }

    private User cashierAt(Long id, Long... branchIds) {
        return User.builder().id(id).username("c" + id).fullName("Thu Ngân").role(Role.CASHIER)
                .branchIds(new HashSet<>(Set.of(branchIds))).active(true).build();
    }

    @Test
    @DisplayName("Thu ngân phải được phân công ít nhất một chi nhánh")
    void cashierNeedsAtLeastOneBranch() {
        CreateUserRequest req = CreateUserRequest.builder()
                .username("no_branch").password("plain123").fullName("Chưa gán").role(Role.CASHIER).build();
        when(userRepository.existsByUsername("no_branch")).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.createUser(req));
        assertEquals("BRANCH_REQUIRED", ex.getCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Không cho bỏ phân công chi nhánh đang có ca mở của nhân viên")
    void removingBranchBlockedWhileShiftOpenThere() {
        User user = cashierAt(5L, 1L);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(branchRepository.existsById(anyLong())).thenReturn(true);
        when(shiftSessionRepository.findByEmployeeIdAndStatus(5L, ShiftStatus.OPEN))
                .thenReturn(Optional.of(ShiftSession.builder().id(9L).branchId(1L).build()));

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.updateUser(5L, assign(2L)));

        assertEquals("SHIFT_OPEN_BRANCH_CHANGE", ex.getCode());
        assertEquals(Set.of(1L), user.getBranchIds());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Thêm chi nhánh khi đang có ca mở vẫn được, miễn giữ chi nhánh của ca đó")
    void addingBranchAllowedWhileShiftOpen() {
        User user = cashierAt(6L, 1L);
        when(userRepository.findById(6L)).thenReturn(Optional.of(user));
        when(branchRepository.existsById(anyLong())).thenReturn(true);
        when(shiftSessionRepository.findByEmployeeIdAndStatus(6L, ShiftStatus.OPEN))
                .thenReturn(Optional.of(ShiftSession.builder().id(10L).branchId(1L).build()));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        assertEquals(List.of(1L, 2L), userService.updateUser(6L, assign(1L, 2L)).getBranchIds());
    }

    @Test
    @DisplayName("Đổi phân công tự do khi không có ca mở; ADMIN không lưu phân công")
    void reassignWithoutOpenShift() {
        User user = cashierAt(7L, 1L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(branchRepository.existsById(anyLong())).thenReturn(true);
        when(shiftSessionRepository.findByEmployeeIdAndStatus(7L, ShiftStatus.OPEN)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        assertEquals(List.of(2L), userService.updateUser(7L, assign(2L)).getBranchIds());

        UpdateUserRequest toAdmin = assign(2L);
        toAdmin.setRole(Role.ADMIN);
        assertEquals(List.of(), userService.updateUser(7L, toAdmin).getBranchIds());
    }
}
