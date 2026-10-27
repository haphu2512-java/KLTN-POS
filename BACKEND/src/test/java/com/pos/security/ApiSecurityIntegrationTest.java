package com.pos.security;

import com.pos.support.TestAuth;
import com.pos.user.Role;
import com.pos.user.User;
import com.pos.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import com.pos.shift.ShiftAssignmentRepository;
import com.pos.staff.StaffMemberRepository;
import com.pos.support.ShiftTestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Khởi động toàn bộ ứng dụng trên H2 (dữ liệu mẫu từ DatabaseInitializer) và kiểm tra phân quyền API.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:posdb;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
@AutoConfigureMockMvc
class ApiSecurityIntegrationTest {

    @Autowired
    private StaffMemberRepository staffRepository;

    @Autowired
    private ShiftAssignmentRepository assignmentRepository;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private UserRepository userRepository;

    private String cashierToken;

    @BeforeEach
    void setUp() {
        cashierToken = tokenFor("cashier");
    }

    private String tokenFor(String username) {
        User user = userRepository.findByUsername(username).orElseThrow();
        UserDetails details = userDetailsService.loadUserByUsername(username);
        return "Bearer " + tokenProvider.generateToken(details, TestAuth.branchOf(user), user.getRole().name(), user.getFullName());
    }

    @Test
    @DisplayName("Không có token → 401")
    void unauthenticatedGets401() throws Exception {
        mvc.perform(get("/api/products")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Thu ngân không được xem báo cáo, hoàn trả, xóa sản phẩm hay điều chuyển kho")
    void cashierBlockedFromManagementEndpoints() throws Exception {
        mvc.perform(get("/api/reports/revenue").header("Authorization", cashierToken))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/invoices/1/refund").header("Authorization", cashierToken))
                .andExpect(status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/products/1").header("Authorization", cashierToken))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/inventory/transfer").header("Authorization", cashierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1,\"fromBranchId\":1,\"toBranchId\":2,\"quantity\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin xem được báo cáo")
    void adminCanReadReports() throws Exception {
        mvc.perform(get("/api/reports/revenue").header("Authorization", tokenFor("admin")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Thu ngân bán sai giá niêm yết bị từ chối; đúng giá thì thành công và ghi đúng nhân viên")
    void cashierSaleUsesListPrice() throws Exception {
        // Chi nhánh phải có ca mở trước khi thu ngân bán (409 nếu ca đã mở từ test khác)
        ShiftTestData.assignNewStaff(staffRepository, assignmentRepository, 1L, 1, "NV bán hàng");
        mvc.perform(post("/api/shifts/open").header("Authorization", cashierToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"workShiftId\":1,\"openingCash\":0,\"handoverReason\":\"test\"}"));

        String tampered = "{\"branchId\":1,\"paymentMethod\":\"CARD\",\"items\":[{\"productId\":1,\"quantity\":1,\"unitPrice\":1}]}";
        mvc.perform(post("/api/invoices").header("Authorization", cashierToken)
                        .contentType(MediaType.APPLICATION_JSON).content(tampered))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRICE_OVERRIDE_FORBIDDEN"));

        Long cashierId = userRepository.findByUsername("cashier").orElseThrow().getId();
        String honest = "{\"branchId\":1,\"employeeId\":999,\"paymentMethod\":\"CARD\",\"items\":[{\"productId\":1,\"quantity\":1}]}";
        mvc.perform(post("/api/invoices").header("Authorization", cashierToken)
                        .contentType(MediaType.APPLICATION_JSON).content(honest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAmount").value(36000.0))
                .andExpect(jsonPath("$.data.employeeId").value(cashierId));
    }

    @Test
    @DisplayName("Tài khoản bị khóa không dùng được token cũ")
    void disabledUserTokenRejected() throws Exception {
        User user = userRepository.save(User.builder().username("tmp_cashier").password("x")
                .fullName("Tạm").role(Role.CASHIER).branchIds(TestAuth.branches(1L)).active(true).build());
        String token = tokenFor("tmp_cashier");

        user.setActive(false);
        userRepository.save(user);

        mvc.perform(get("/api/products").header("Authorization", token))
                .andExpect(status().isUnauthorized());
    }
}
