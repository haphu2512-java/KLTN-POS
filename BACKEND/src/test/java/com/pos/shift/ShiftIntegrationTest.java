package com.pos.shift;

import com.pos.staff.StaffMemberRepository;
import com.pos.support.ShiftTestData;
import com.pos.support.TestAuth;
import com.pos.security.CustomUserDetailsService;
import com.pos.security.JwtTokenProvider;
import com.pos.user.Role;
import com.pos.user.User;
import com.pos.user.UserRepository;
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
 * Luồng ca làm việc trên H2 riêng: chưa mở ca thì không bán được, mở ca cho nhân viên được xếp → bán →
 * đóng ca tính đúng tiền mặt dự kiến và chênh lệch, ca sau nhận gợi ý bàn giao.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:shiftdb;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
@AutoConfigureMockMvc
class ShiftIntegrationTest {

    private static final String CASH_SALE =
            "{\"branchId\":1,\"paymentMethod\":\"CASH\",\"receivedAmount\":50000,\"items\":[{\"productId\":1,\"quantity\":1}]}";
    private static final String CARD_SALE =
            "{\"branchId\":1,\"paymentMethod\":\"CARD\",\"items\":[{\"productId\":1,\"quantity\":1}]}";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StaffMemberRepository staffRepository;

    @Autowired
    private ShiftAssignmentRepository assignmentRepository;

    private String tokenFor(String username) {
        User user = userRepository.findByUsername(username).orElseThrow();
        UserDetails details = userDetailsService.loadUserByUsername(username);
        return "Bearer " + tokenProvider.generateToken(details, TestAuth.branchOf(user), user.getRole().name(), user.getFullName());
    }

    private String newUser(String username, Role role, Long branchId) {
        userRepository.save(User.builder().username(username).password("x")
                .fullName(username).role(role).branchIds(TestAuth.branches(branchId)).active(true).build());
        return tokenFor(username);
    }

    @Test
    @DisplayName("Có 5 ca cố định mặc định")
    void seedsFiveWorkShifts() throws Exception {
        mvc.perform(get("/api/shifts/work-shifts").header("Authorization", tokenFor("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(5))
                .andExpect(jsonPath("$.data[0].startTime").value("06:00:00"))
                .andExpect(jsonPath("$.data[4].endTime").value("22:00:00"));
    }

    @Test
    @DisplayName("Mở ca → bán → đóng ca: tiền mặt dự kiến, chênh lệch và gợi ý bàn giao cho ca sau")
    void fullShiftCycle() throws Exception {
        String cashier = newUser("shift_cashier", Role.CASHIER, 1L);

        // Chưa mở ca thì không bán được
        mvc.perform(post("/api/invoices").header("Authorization", cashier)
                        .contentType(MediaType.APPLICATION_JSON).content(CASH_SALE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SHIFT_NOT_OPEN"));

        ShiftTestData.assignNewStaff(staffRepository, assignmentRepository, 1L, 2, "Nguyễn Văn Trực");
        mvc.perform(post("/api/shifts/open").header("Authorization", cashier)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workShiftId\":2,\"openingCash\":500000,\"note\":\"Nhận két từ ca 1\",\"handoverReason\":\"test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.workShiftName").value("Ca 2 (sáng)"))
                .andExpect(jsonPath("$.data.employeeName").value("Nguyễn Văn Trực"))
                .andExpect(jsonPath("$.data.openedByName").value("shift_cashier"));

        // Mở ca lần hai bị chặn
        mvc.perform(post("/api/shifts/open").header("Authorization", cashier)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"workShiftId\":2,\"openingCash\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SHIFT_ALREADY_OPEN"));

        mvc.perform(post("/api/invoices").header("Authorization", cashier)
                        .contentType(MediaType.APPLICATION_JSON).content(CASH_SALE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shiftSessionId").isNumber());
        mvc.perform(post("/api/invoices").header("Authorization", cashier)
                        .contentType(MediaType.APPLICATION_JSON).content(CARD_SALE))
                .andExpect(status().isOk());

        // Số tạm tính khi ca còn mở: 500.000 + 36.000 tiền mặt
        mvc.perform(get("/api/shifts/current").header("Authorization", cashier))
                .andExpect(jsonPath("$.data.session.cashSales").value(36000.0))
                .andExpect(jsonPath("$.data.session.nonCashSales").value(36000.0))
                .andExpect(jsonPath("$.data.session.invoiceCount").value(2))
                .andExpect(jsonPath("$.data.session.expectedCash").value(536000.0));

        // Đếm được thiếu 1.000đ: phải ghi lý do, ca chờ quản lý duyệt
        mvc.perform(post("/api/shifts/current/close").header("Authorization", cashier)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"closingCash\":535000,\"handoverNote\":\"Hết túi nilon\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DIFFERENCE_REASON_REQUIRED"));
        mvc.perform(post("/api/shifts/current/close").header("Authorization", cashier)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"closingCash\":535000,\"differenceReason\":\"Thối nhầm\",\"handoverNote\":\"Hết túi nilon\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.data.expectedCash").value(536000.0))
                .andExpect(jsonPath("$.data.cashDifference").value(-1000.0));

        // Đã đóng ca thì lại không bán được
        mvc.perform(post("/api/invoices").header("Authorization", cashier)
                        .contentType(MediaType.APPLICATION_JSON).content(CASH_SALE))
                .andExpect(status().isConflict());

        // Ca sau ở cùng chi nhánh nhận tiền và ghi chú bàn giao của ca trước
        String next = newUser("shift_next", Role.CASHIER, 1L);
        mvc.perform(get("/api/shifts/current").header("Authorization", next))
                .andExpect(jsonPath("$.data.session").doesNotExist())
                .andExpect(jsonPath("$.data.shiftRequired").value(true))
                .andExpect(jsonPath("$.data.suggestedOpeningCash").value(535000.0))
                .andExpect(jsonPath("$.data.previousShift.handoverNote").value("Hết túi nilon"));
    }

    @Test
    @DisplayName("Quản lý chỉ đóng hộ ca trong chi nhánh của mình; tài khoản quầy không xem lịch sử ca")
    void managerScopedToBranch() throws Exception {
        String cashier = newUser("forgetful_cashier", Role.CASHIER, 1L);
        ShiftTestData.assignNewStaff(staffRepository, assignmentRepository, 1L, 3, "Nhân viên quên đóng ca");
        String body = mvc.perform(post("/api/shifts/open").header("Authorization", cashier)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workShiftId\":3,\"openingCash\":0,\"handoverReason\":\"test\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long shiftId = com.jayway.jsonpath.JsonPath.parse(body).read("$.data.id", Long.class);

        mvc.perform(get("/api/shifts").param("status", "OPEN").header("Authorization", cashier))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/shifts/" + shiftId + "/close").header("Authorization", cashier)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"closingCash\":0}"))
                .andExpect(status().isForbidden());

        String otherManager = newUser("manager_b2", Role.MANAGER, 2L);
        mvc.perform(post("/api/shifts/" + shiftId + "/close").header("Authorization", otherManager)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"closingCash\":0}"))
                .andExpect(status().isForbidden());

        String manager = newUser("manager_b1", Role.MANAGER, 1L);
        mvc.perform(get("/api/shifts").param("status", "OPEN").header("Authorization", manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shifts.length()").value(1))
                .andExpect(jsonPath("$.data.shifts[0].id").value(shiftId));
        mvc.perform(post("/api/shifts/" + shiftId + "/close").header("Authorization", manager)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"closingCash\":0,\"handoverNote\":\"Quản lý đóng hộ\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andExpect(jsonPath("$.data.cashDifference").value(0.0));
    }
}
