package com.pos.shift;

import com.jayway.jsonpath.JsonPath;
import com.pos.branch.Branch;
import com.pos.branch.BranchRepository;
import com.pos.inventory.Inventory;
import com.pos.inventory.InventoryRepository;
import com.pos.product.ProductRepository;
import com.pos.security.CustomUserDetailsService;
import com.pos.security.JwtTokenProvider;
import com.pos.support.TestAuth;
import com.pos.user.Role;
import com.pos.user.User;
import com.pos.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tab "Ca làm": quản trị xem/lọc mọi ca và tổng thiếu/dư, thu ngân chỉ xem ca của mình kèm hóa đơn để dò
 * nguyên nhân, tiền hoàn trả trừ vào két của ca đang mở lúc hoàn trả. Mỗi test dùng chi nhánh riêng để
 * ca đang mở của test khác không ảnh hưởng.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:shiftmgmtdb;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
@AutoConfigureMockMvc
class ShiftManagementIntegrationTest {

    /** Sản phẩm mẫu id 1 giá 36.000đ */
    private static final String CASH_SALE =
            "{\"paymentMethod\":\"CASH\",\"receivedAmount\":50000,\"items\":[{\"productId\":1,\"quantity\":1}]}";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private ProductRepository productRepository;

    /** Chi nhánh mới có sẵn tồn kho sản phẩm 1 */
    private Long newBranch(String name) {
        Branch branch = branchRepository.save(Branch.builder().name(name).active(true).build());
        inventoryRepository.save(Inventory.builder().branch(branch)
                .product(productRepository.findById(1L).orElseThrow()).quantity(100).minThreshold(1).build());
        return branch.getId();
    }

    private String token(String username, Long branchId) {
        User user = userRepository.findByUsername(username).orElseThrow();
        return "Bearer " + tokenProvider.generateToken(userDetailsService.loadUserByUsername(username),
                branchId, user.getRole().name(), user.getFullName());
    }

    private String newUser(String username, Role role, Long branchId) {
        userRepository.save(User.builder().username(username).password("x").fullName(username).role(role)
                .branchIds(role == Role.ADMIN ? TestAuth.branches() : TestAuth.branches(branchId)).active(true).build());
        return token(username, branchId);
    }

    private long open(String token, long openingCash) throws Exception {
        String body = mvc.perform(post("/api/shifts/open").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workShiftId\":2,\"openingCash\":" + openingCash + "}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.parse(body).read("$.data.id", Long.class);
    }

    private long sell(String token) throws Exception {
        String body = mvc.perform(post("/api/invoices").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(CASH_SALE))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.parse(body).read("$.data.id", Long.class);
    }

    private void close(String token, long counted) throws Exception {
        mvc.perform(post("/api/shifts/current/close").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"closingCash\":" + counted + "}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Thu ngân chỉ xem ca của mình, kèm hóa đơn của ca; không xem được ca người khác")
    void cashierSeesOnlyOwnShiftsWithInvoices() throws Exception {
        Long branch = newBranch("CN ca riêng");
        String alice = newUser("alice_shift", Role.CASHIER, branch);
        String bob = newUser("bob_shift", Role.CASHIER, branch);

        long aliceShift = open(alice, 200000);
        sell(alice);
        close(alice, 230000); // dự kiến 236.000 → thiếu 6.000

        long bobShift = open(bob, 0);
        close(bob, 0);

        mvc.perform(get("/api/shifts").header("Authorization", alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalShifts").value(1))
                .andExpect(jsonPath("$.data.shifts[0].id").value(aliceShift))
                .andExpect(jsonPath("$.data.shifts[0].branchName").value("CN ca riêng"))
                .andExpect(jsonPath("$.data.totalShortage").value(6000.0));
        // Gửi employeeId người khác cũng chỉ ra ca của mình
        mvc.perform(get("/api/shifts").param("employeeId", "999").header("Authorization", alice))
                .andExpect(jsonPath("$.data.shifts[0].id").value(aliceShift));

        mvc.perform(get("/api/shifts/" + aliceShift + "/invoices").header("Authorization", alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].kind").value("SALE"))
                .andExpect(jsonPath("$.data[0].totalAmount").value(36000.0))
                .andExpect(jsonPath("$.data[0].receivedAmount").value(50000.0))
                .andExpect(jsonPath("$.data[0].changeAmount").value(14000.0));

        mvc.perform(get("/api/shifts/" + bobShift).header("Authorization", alice)).andExpect(status().isForbidden());
        mvc.perform(get("/api/shifts/" + bobShift + "/invoices").header("Authorization", alice)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Quản trị lọc ca theo chi nhánh, nhân viên, trạng thái và thấy tổng thiếu/dư")
    void adminFiltersAcrossBranches() throws Exception {
        Long b1 = newBranch("CN lọc 1");
        Long b2 = newBranch("CN lọc 2");
        String c1 = newUser("filter_c1", Role.CASHIER, b1);
        String c2 = newUser("filter_c2", Role.CASHIER, b2);
        String admin = newUser("filter_admin", Role.ADMIN, b1);

        open(c1, 100000);
        sell(c1);
        close(c1, 140000); // dự kiến 136.000 → dư 4.000
        open(c2, 0);       // để mở, quên đóng

        Long c1Id = userRepository.findByUsername("filter_c1").orElseThrow().getId();
        mvc.perform(get("/api/shifts").param("branchId", String.valueOf(b1)).header("Authorization", admin))
                .andExpect(jsonPath("$.data.totalShifts").value(1))
                .andExpect(jsonPath("$.data.totalOverage").value(4000.0))
                .andExpect(jsonPath("$.data.mismatchedShifts").value(1));
        mvc.perform(get("/api/shifts").param("employeeId", String.valueOf(c1Id)).header("Authorization", admin))
                .andExpect(jsonPath("$.data.shifts.length()").value(1))
                .andExpect(jsonPath("$.data.shifts[0].employeeName").value("filter_c1"));
        String openList = mvc.perform(get("/api/shifts").param("branchId", String.valueOf(b2)).param("status", "OPEN")
                        .header("Authorization", admin))
                .andExpect(jsonPath("$.data.openShifts").value(1))
                .andReturn().getResponse().getContentAsString();
        long forgotten = JsonPath.parse(openList).read("$.data.shifts[0].id", Long.class);

        // Quản trị (đang đăng nhập chi nhánh 1) đóng hộ ca quên đóng ở chi nhánh 2
        mvc.perform(post("/api/shifts/" + forgotten + "/close").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"closingCash\":0,\"handoverNote\":\"Đóng hộ\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.closedByOther").value(true))
                .andExpect(jsonPath("$.data.closedByName").value("filter_admin"));

        mvc.perform(get("/api/shifts").param("from", "2026-01-10").param("to", "2026-01-01").header("Authorization", admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Hoàn trả khi ca bán đã đóng: tiền trừ vào két ca đang mở, hiện trong hóa đơn của ca đó")
    void refundCountsAgainstShiftOpenAtRefundTime() throws Exception {
        Long branch = newBranch("CN hoàn trả");
        String morning = newUser("refund_morning", Role.CASHIER, branch);
        String evening = newUser("refund_evening", Role.CASHIER, branch);
        String manager = newUser("refund_manager", Role.MANAGER, branch);

        long morningShift = open(morning, 100000);
        long invoice = sell(morning);
        close(morning, 136000);

        long eveningShift = open(evening, 136000);
        mvc.perform(post("/api/invoices/" + invoice + "/refund").header("Authorization", manager)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Hàng lỗi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refundShiftSessionId").value(eveningShift));

        // Ca sáng đã chốt số, không đổi
        mvc.perform(get("/api/shifts/" + morningShift).header("Authorization", morning))
                .andExpect(jsonPath("$.data.cashDifference").value(0.0));
        // Ca tối: 136.000 - 36.000 hoàn trả
        mvc.perform(get("/api/shifts/" + eveningShift).header("Authorization", evening))
                .andExpect(jsonPath("$.data.cashRefunds").value(36000.0))
                .andExpect(jsonPath("$.data.expectedCash").value(100000.0));
        mvc.perform(get("/api/shifts/" + eveningShift + "/invoices").header("Authorization", evening))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].kind").value("REFUND_OTHER_SHIFT"))
                .andExpect(jsonPath("$.data[0].refundCountedHere").value(true));

        close(evening, 100000);
        mvc.perform(get("/api/shifts/" + eveningShift).header("Authorization", evening))
                .andExpect(jsonPath("$.data.cashDifference").value(0.0))
                .andExpect(jsonPath("$.data.cashRefunds").value(36000.0));
    }

    @Test
    @DisplayName("Hoàn trả ngay trong ca đang mở: doanh số giữ nguyên, tiền mặt dự kiến trừ phần hoàn trả")
    void refundWithinSameShift() throws Exception {
        Long branch = newBranch("CN hoàn trong ca");
        String cashier = newUser("same_shift_cashier", Role.CASHIER, branch);
        String manager = newUser("same_shift_manager", Role.MANAGER, branch);

        long shift = open(cashier, 50000);
        long invoice = sell(cashier);
        sell(cashier);
        mvc.perform(post("/api/invoices/" + invoice + "/refund").header("Authorization", manager))
                .andExpect(status().isOk());

        mvc.perform(get("/api/shifts/current").header("Authorization", cashier))
                .andExpect(jsonPath("$.data.session.id").value(shift))
                .andExpect(jsonPath("$.data.session.cashSales").value(72000.0))
                .andExpect(jsonPath("$.data.session.cashRefunds").value(36000.0))
                .andExpect(jsonPath("$.data.session.refundedCount").value(1))
                .andExpect(jsonPath("$.data.session.expectedCash").value(86000.0));
    }

    @Test
    @DisplayName("Chỉ quản trị thêm/sửa lịch ca cố định")
    void onlyAdminEditsSchedule() throws Exception {
        Long branch = newBranch("CN lịch ca");
        String cashier = newUser("schedule_cashier", Role.CASHIER, branch);
        String admin = newUser("schedule_admin", Role.ADMIN, branch);
        String body = "{\"name\":\"Ca đêm\",\"startTime\":\"22:00\",\"endTime\":\"06:00\"}";

        mvc.perform(post("/api/shifts/work-shifts").header("Authorization", cashier)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        String created = mvc.perform(post("/api/shifts/work-shifts").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Ca đêm"))
                .andReturn().getResponse().getContentAsString();
        long id = JsonPath.parse(created).read("$.data.id", Long.class);

        mvc.perform(put("/api/shifts/work-shifts/" + id).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ca đêm\",\"startTime\":\"22:00\",\"endTime\":\"06:00\",\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));
    }
}
