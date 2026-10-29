package com.pos.approval;

import com.pos.auth.LoginAttemptService;
import com.pos.common.BusinessException;
import com.pos.user.Role;
import com.pos.user.User;
import com.pos.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ApprovalServiceTest {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final UserRepository userRepository = mock(UserRepository.class);
    private ApprovalService service;

    private final User cashier = User.builder().id(7L).username("cashier").role(Role.CASHIER)
            .currentBranchId(1L).active(true).build();
    private User manager;

    @BeforeEach
    void setUp() {
        LoginAttemptService attempts = new LoginAttemptService(3, 15);
        service = new ApprovalService(userRepository, encoder, attempts, BigDecimal.valueOf(5));
        manager = User.builder().id(8L).username("manager").fullName("Chị Quản Lý").role(Role.MANAGER)
                .branchIds(Set.of(1L)).active(true).password(encoder.encode("secret"))
                .approvalPinHash(encoder.encode("2468")).build();
        when(userRepository.findByUsername("manager")).thenReturn(Optional.of(manager));
        when(userRepository.findById(8L)).thenReturn(Optional.of(manager));
    }

    @Test
    @DisplayName("Hạn mức 5%: chỉ thu ngân vượt mức mới cần duyệt")
    void limitAppliesToCashierOnly() {
        BigDecimal subtotal = BigDecimal.valueOf(100000);
        assertEquals(BigDecimal.valueOf(5000), service.cashierDiscountLimit(subtotal));
        assertFalse(service.requiresDiscountApproval(cashier, BigDecimal.valueOf(5000), subtotal));
        assertTrue(service.requiresDiscountApproval(cashier, BigDecimal.valueOf(5001), subtotal));
        assertFalse(service.requiresDiscountApproval(manager, BigDecimal.valueOf(90000), subtotal));
    }

    @Test
    @DisplayName("PIN đúng cấp mã duyệt dùng một lần, gắn với thu ngân, chi nhánh và số tiền")
    void grantIsSingleUseAndScoped() {
        ApprovalService.GrantResult grant = service.approveDiscount(cashier, "manager", "2468", BigDecimal.valueOf(20000));
        assertEquals("Chị Quản Lý", grant.approverName());

        assertEquals(8L, service.checkDiscountApproval(grant.token(), cashier, 1L, BigDecimal.valueOf(20000)));
        assertThrows(BusinessException.class,
                () -> service.checkDiscountApproval(grant.token(), cashier, 1L, BigDecimal.valueOf(20001)), "Vượt số đã duyệt");
        assertThrows(BusinessException.class,
                () -> service.checkDiscountApproval(grant.token(), cashier, 2L, BigDecimal.valueOf(100)), "Sai chi nhánh");
        User other = User.builder().id(9L).role(Role.CASHIER).currentBranchId(1L).build();
        assertThrows(BusinessException.class,
                () -> service.checkDiscountApproval(grant.token(), other, 1L, BigDecimal.valueOf(100)), "Thu ngân khác");

        service.markUsed(grant.token());
        assertThrows(BusinessException.class,
                () -> service.checkDiscountApproval(grant.token(), cashier, 1L, BigDecimal.valueOf(100)), "Đã dùng");
    }

    @Test
    @DisplayName("PIN sai bị từ chối và sai nhiều lần thì khóa tạm")
    void wrongPinRejectedThenLocked() {
        for (int i = 0; i < 3; i++) {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.approveDiscount(cashier, "manager", "0000", BigDecimal.TEN));
            assertEquals("APPROVAL_PIN_INVALID", ex.getCode());
        }
        BusinessException locked = assertThrows(BusinessException.class,
                () -> service.approveDiscount(cashier, "manager", "2468", BigDecimal.TEN));
        assertEquals("AUTH_TOO_MANY_ATTEMPTS", locked.getCode());
    }

    @Test
    @DisplayName("Quản lý chi nhánh khác hoặc thu ngân không duyệt được")
    void onlyBranchManagersApprove() {
        User otherBranch = User.builder().id(10L).username("m2").role(Role.MANAGER).branchIds(Set.of(2L))
                .active(true).approvalPinHash(encoder.encode("1357")).build();
        when(userRepository.findByUsername("m2")).thenReturn(Optional.of(otherBranch));
        assertEquals("APPROVAL_BRANCH_FORBIDDEN", assertThrows(BusinessException.class,
                () -> service.approveDiscount(cashier, "m2", "1357", BigDecimal.TEN)).getCode());

        User cashier2 = User.builder().id(11L).username("c2").role(Role.CASHIER).active(true)
                .approvalPinHash(encoder.encode("1111")).build();
        when(userRepository.findByUsername("c2")).thenReturn(Optional.of(cashier2));
        assertEquals("APPROVAL_PIN_INVALID", assertThrows(BusinessException.class,
                () -> service.approveDiscount(cashier, "c2", "1111", BigDecimal.TEN)).getCode());
    }

    @Test
    @DisplayName("Đặt PIN: cần đúng mật khẩu, PIN 4-6 chữ số, chỉ quản lý")
    void setPinValidates() {
        assertEquals("AUTH_INVALID_CREDENTIALS", assertThrows(BusinessException.class,
                () -> service.setPin(manager, "wrong", "1234")).getCode());
        assertEquals("INVALID_PIN", assertThrows(BusinessException.class,
                () -> service.setPin(manager, "secret", "12ab")).getCode());
        assertEquals("APPROVAL_FORBIDDEN", assertThrows(BusinessException.class,
                () -> service.setPin(cashier, "x", "1234")).getCode());

        service.setPin(manager, "secret", "123456");
        verify(userRepository).save(any(User.class));
        assertTrue(encoder.matches("123456", manager.getApprovalPinHash()));
    }
}
