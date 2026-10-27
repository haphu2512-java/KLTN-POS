package com.pos.auth;

import com.jayway.jsonpath.JsonPath;
import com.pos.security.CustomUserDetailsService;
import com.pos.security.JwtTokenProvider;
import com.pos.support.TestAuth;
import com.pos.user.Role;
import com.pos.user.User;
import com.pos.user.UserRepository;
import com.pos.shift.ShiftAssignmentRepository;
import com.pos.staff.StaffMemberRepository;
import com.pos.support.ShiftTestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Chọn chi nhánh lúc đăng nhập: chỉ chi nhánh được phân công (ADMIN mọi chi nhánh), chi nhánh nằm trong
 * token và mọi thao tác ghi dùng chi nhánh đó; có ca mở ở chi nhánh khác thì không vào được.
 * Dữ liệu mẫu có 2 chi nhánh (id 1 và 2).
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:branchlogindb;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
@AutoConfigureMockMvc
class BranchLoginIntegrationTest {

    private static final String PASSWORD = "matkhau123";

    @Autowired
    private StaffMemberRepository staffRepository;

    @Autowired
    private ShiftAssignmentRepository assignmentRepository;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    private User newUser(String username, Role role, Long... branchIds) {
        return userRepository.save(User.builder().username(username).password(passwordEncoder.encode(PASSWORD))
                .fullName(username).role(role).branchIds(TestAuth.branches(branchIds)).active(true).build());
    }

    private ResultActions login(String username, String password, Long branchId) throws Exception {
        String body = branchId == null
                ? "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"
                : "{\"username\":\"" + username + "\",\"password\":\"" + password + "\",\"branchId\":" + branchId + "}";
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String tokenOf(ResultActions result) throws Exception {
        return "Bearer " + JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.data.token");
    }

    @Test
    @DisplayName("Chỉ được gán một chi nhánh: vào thẳng chi nhánh đó, không cần chọn")
    void singleBranchLogsInDirectly() throws Exception {
        newUser("one_branch", Role.CASHIER, 2L);
        ResultActions res = login("one_branch", PASSWORD, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.branchId").value(2))
                .andExpect(jsonPath("$.data.branchName").isString());

        mvc.perform(get("/api/auth/me").header("Authorization", tokenOf(res)))
                .andExpect(jsonPath("$.data.branchId").value(2));
    }

    @Test
    @DisplayName("Nhiều chi nhánh: phải chọn, chỉ chọn được chi nhánh được gán; ADMIN chọn được mọi chi nhánh")
    void multipleBranchesMustChoose() throws Exception {
        newUser("two_branches", Role.CASHIER, 1L, 2L);
        login("two_branches", PASSWORD, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BRANCH_SELECTION_REQUIRED"))
                .andExpect(jsonPath("$.data.branches.length()").value(2))
                .andExpect(jsonPath("$.data.token").doesNotExist());
        login("two_branches", PASSWORD, 2L)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.branchId").value(2));

        newUser("only_first", Role.WAREHOUSE, 1L);
        login("only_first", PASSWORD, 2L)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BRANCH_NOT_ALLOWED"));

        login("admin", "test-seed-password", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BRANCH_SELECTION_REQUIRED"));
        login("admin", "test-seed-password", 2L)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.branchId").value(2));
    }

    @Test
    @DisplayName("Sai mật khẩu thì không lộ danh sách chi nhánh; chưa gán chi nhánh thì không đăng nhập được")
    void wrongPasswordAndNoBranch() throws Exception {
        newUser("secret_branches", Role.CASHIER, 1L, 2L);
        login("secret_branches", "sai-mat-khau", null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.data").doesNotExist());

        newUser("unassigned", Role.CASHIER);
        login("unassigned", PASSWORD, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NO_BRANCH_ASSIGNED"));
    }

    @Test
    @DisplayName("Đang có ca mở ở chi nhánh 1 thì không đăng nhập vào chi nhánh 2 được")
    void openShiftPinsLoginToItsBranch() throws Exception {
        newUser("busy_cashier", Role.CASHIER, 1L, 2L);
        String atFirst = tokenOf(login("busy_cashier", PASSWORD, 1L).andExpect(status().isOk()));
        ShiftTestData.assignNewStaff(staffRepository, assignmentRepository, 1L, 1, "NV ca 1");
        mvc.perform(post("/api/shifts/open").header("Authorization", atFirst)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workShiftId\":1,\"openingCash\":100000,\"handoverReason\":\"test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.branchId").value(1));

        login("busy_cashier", PASSWORD, 2L)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SHIFT_OPEN_AT_OTHER_BRANCH"))
                .andExpect(jsonPath("$.data.openShiftBranchId").value(1));
        // Màn chọn chi nhánh được biết chi nhánh đang có ca để chọn sẵn
        login("busy_cashier", PASSWORD, null)
                .andExpect(jsonPath("$.data.openShiftBranchId").value(1));
        login("busy_cashier", PASSWORD, 1L).andExpect(status().isOk());

        // Đóng ca xong thì vào chi nhánh 2 được
        mvc.perform(post("/api/shifts/current/close").header("Authorization", atFirst)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"closingCash\":100000}"))
                .andExpect(status().isOk());
        login("busy_cashier", PASSWORD, 2L).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Bán hàng, nhập kho, xem tồn kho dùng chi nhánh trong token, không dùng chi nhánh client gửi")
    void writesUseTokenBranch() throws Exception {
        newUser("seller_b2", Role.MANAGER, 1L, 2L);
        String token = tokenOf(login("seller_b2", PASSWORD, 2L).andExpect(status().isOk()));

        mvc.perform(post("/api/invoices").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"CARD\",\"items\":[{\"productId\":1,\"quantity\":1}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.branch.id").value(2));
        mvc.perform(post("/api/invoices").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":1,\"paymentMethod\":\"CARD\",\"items\":[{\"productId\":1,\"quantity\":1}]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BRANCH_MISMATCH"));

        mvc.perform(post("/api/stock-in").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":1,\"items\":[{\"productId\":1,\"quantity\":5}]}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/stock-in").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":1,\"quantity\":5}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.branch.id").value(2));

        mvc.perform(get("/api/inventory").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].branch.id").value(2));
        mvc.perform(get("/api/inventory").param("branchId", "1").header("Authorization", token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BRANCH_FORBIDDEN"));
        mvc.perform(get("/api/reports/revenue").param("branchId", "1").header("Authorization", token))
                .andExpect(status().isForbidden());

        // ADMIN đăng nhập ở chi nhánh 1 vẫn xem được chi nhánh 2
        String admin = tokenOf(login("admin", "test-seed-password", 1L));
        mvc.perform(get("/api/inventory").param("branchId", "2").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].branch.id").value(2));
    }

    @Test
    @DisplayName("Token ghi chi nhánh không được gán, hoặc chi nhánh vừa bị bỏ phân công, đều bị từ chối")
    void tokenBranchMustStillBeAllowed() throws Exception {
        User user = newUser("moved_cashier", Role.CASHIER, 1L, 2L);
        String token = tokenOf(login("moved_cashier", PASSWORD, 2L).andExpect(status().isOk()));
        mvc.perform(get("/api/products").header("Authorization", token)).andExpect(status().isOk());

        user.setBranchIds(TestAuth.branches(1L));
        userRepository.save(user);
        mvc.perform(get("/api/products").header("Authorization", token)).andExpect(status().isUnauthorized());

        // Token tự ký với chi nhánh không được gán (hoặc token cũ không có chi nhánh)
        newUser("forger", Role.CASHIER, 1L);
        String forged = "Bearer " + tokenProvider.generateToken(userDetailsService.loadUserByUsername("forger"),
                2L, "CASHIER", "forger");
        mvc.perform(get("/api/products").header("Authorization", forged)).andExpect(status().isUnauthorized());
        String noBranch = "Bearer " + tokenProvider.generateToken(userDetailsService.loadUserByUsername("forger"),
                null, "CASHIER", "forger");
        mvc.perform(get("/api/products").header("Authorization", noBranch)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Quản trị phân công nhiều chi nhánh qua API nhân viên")
    void adminAssignsBranches() throws Exception {
        String admin = tokenOf(login("admin", "test-seed-password", 1L));
        mvc.perform(post("/api/users").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"multi_staff\",\"password\":\"" + PASSWORD + "\",\"fullName\":\"Nhân viên 2 chi nhánh\","
                                + "\"role\":\"CASHIER\",\"branchIds\":[1,2]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.branchIds.length()").value(2));
        org.junit.jupiter.api.Assertions.assertEquals(Set.of(1L, 2L),
                userRepository.findByUsername("multi_staff").orElseThrow().getBranchIds());
    }
}
