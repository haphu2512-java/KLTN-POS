package com.pos.user;

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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ShiftSessionRepository shiftSessionRepository;

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
                .branchId(1L)
                .build();

        when(userRepository.existsByUsername("cashier2")).thenReturn(false);
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
        verify(passwordEncoder).encode("plain123");
        verify(userRepository).save(any(User.class));
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

    private UpdateUserRequest moveTo(Long branchId) {
        UpdateUserRequest req = new UpdateUserRequest();
        req.setFullName("Thu Ngân");
        req.setRole(Role.CASHIER);
        req.setBranchId(branchId);
        return req;
    }

    @Test
    @DisplayName("Không cho chuyển chi nhánh khi nhân viên còn ca đang mở")
    void branchChangeBlockedWhileShiftOpen() {
        User user = User.builder().id(5L).username("c5").fullName("Thu Ngân").role(Role.CASHIER).branchId(1L).active(true).build();
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(shiftSessionRepository.findByEmployeeIdAndStatus(5L, ShiftStatus.OPEN))
                .thenReturn(Optional.of(ShiftSession.builder().id(9L).branchId(1L).build()));

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.updateUser(5L, moveTo(2L)));

        assertEquals("SHIFT_OPEN_BRANCH_CHANGE", ex.getCode());
        assertEquals(1L, user.getBranchId());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Chuyển chi nhánh được khi không còn ca mở; sửa thông tin khác không cần kiểm tra ca")
    void branchChangeAllowedWithoutOpenShift() {
        User user = User.builder().id(6L).username("c6").fullName("Thu Ngân").role(Role.CASHIER).branchId(1L).active(true).build();
        when(userRepository.findById(6L)).thenReturn(Optional.of(user));
        when(shiftSessionRepository.findByEmployeeIdAndStatus(6L, ShiftStatus.OPEN)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        assertEquals(2L, userService.updateUser(6L, moveTo(2L)).getBranchId());

        // Giữ nguyên chi nhánh: không hỏi đến ca
        userService.updateUser(6L, moveTo(2L));
        verify(shiftSessionRepository, times(1)).findByEmployeeIdAndStatus(6L, ShiftStatus.OPEN);
    }
}
