package com.pos.shift;

import com.jayway.jsonpath.JsonPath;
import com.pos.branch.Branch;
import com.pos.branch.BranchRepository;
import com.pos.inventory.Inventory;
import com.pos.inventory.InventoryRepository;
import com.pos.product.ProductRepository;
import com.pos.security.CustomUserDetailsService;
import com.pos.security.JwtTokenProvider;
import com.pos.staff.StaffMemberRepository;
import com.pos.support.ShiftTestData;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tab "Ca làm": xếp ca, mở ca cho nhân viên được xếp, thu/chi két, đối soát lúc đóng ca, duyệt ca lệch,
 * lọc lịch sử và tiền hoàn trả trừ vào két ca đang mở. Mỗi test dùng chi nhánh riêng để ca đang mở của
 * test khác không ảnh hưởng.
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

    @Autowired
    private StaffMemberRepository staffRepository;

    @Autowired
    private ShiftAssignmentRepository assignmentRepository;

    @Autowired
    private ShiftService shiftService;

    @Autowired
    private WorkShiftService workShiftService;

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

    private static String json(String body) {
        return body.replace('\'', '"');
    }

    /** Xếp nhân viên mới vào ca 2 hôm nay rồi mở ca đó từ tài khoản quầy */
    private long open(String token, Long branchId, String staffName, long openingCash) throws Exception {
        ShiftTestData.assignNewStaff(staffRepository, assignmentRepository, branchId, 2, staffName);
        String body = mvc.perform(post("/api/shifts/open").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'workShiftId':2,'openingCash':" + openingCash + ",'handoverReason':'test'}")))
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'closingCash':" + counted + ",'differenceReason':'test'}")))
                .andExpect(status().isOk());
    }

    private long staffId(String token, String name) throws Exception {
        String body = mvc.perform(post("/api/staff").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(json("{'fullName':'" + name + "','phone':'0901234567'}")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.parse(body).read("$.data.id", Long.class);
    }

    @Test
    @DisplayName("Quản lý xếp ca; quầy chỉ mở được ca cho người được xếp, quản lý mở trực thay phải ghi lý do")
    void openShiftFollowsAssignment() throws Exception {
        Long branch = newBranch("CN xếp ca");
        String counter = newUser("assign_counter", Role.CASHIER, branch);
        String manager = newUser("assign_manager", Role.MANAGER, branch);
        long lan = staffId(manager, "Lan");
        long minh = staffId(manager, "Minh");

        // Thu ngân không thêm được nhân viên, không xếp ca được
        mvc.perform(post("/api/staff").header("Authorization", counter)
                .contentType(MediaType.APPLICATION_JSON).content(json("{'fullName':'X'}"))).andExpect(status().isForbidden());

        String open = json("{'workShiftId':3,'openingCash':0}");
        mvc.perform(post("/api/shifts/open").header("Authorization", counter).contentType(MediaType.APPLICATION_JSON).content(open))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SHIFT_NOT_ASSIGNED"));

        String today = LocalDate.now().toString();
        mvc.perform(put("/api/shifts/assignments").header("Authorization", counter).contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'workShiftId':3,'shiftDate':'" + today + "','staffMemberId':" + lan + "}")))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/shifts/assignments").header("Authorization", manager).contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'workShiftId':3,'shiftDate':'" + today + "','staffMemberId':" + lan + "}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.staffName").value("Lan"));
        mvc.perform(get("/api/shifts/assignments").param("from", today).param("to", today).header("Authorization", counter))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].staffMemberId").value(lan));

        // Quầy không đổi được người trực
        mvc.perform(post("/api/shifts/open").header("Authorization", counter).contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'workShiftId':3,'openingCash':0,'staffMemberId':" + minh + "}")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SHIFT_ASSIGNED_TO_OTHER"));
        // Quản lý cho Minh trực thay thì phải ghi lý do
        mvc.perform(post("/api/shifts/open").header("Authorization", manager).contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'workShiftId':3,'openingCash':0,'staffMemberId':" + minh + "}")))
                .andExpect(jsonPath("$.code").value("OVERRIDE_REASON_REQUIRED"));

        mvc.perform(post("/api/shifts/open").header("Authorization", counter).contentType(MediaType.APPLICATION_JSON).content(open))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.staffMemberId").value(lan))
                .andExpect(jsonPath("$.data.employeeName").value("Lan"))
                .andExpect(jsonPath("$.data.openedByName").value("assign_counter"));
        // Mỗi chi nhánh một ca mở, kể cả khi quản lý mở từ máy khác
        mvc.perform(post("/api/shifts/open").header("Authorization", manager).contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'workShiftId':3,'openingCash':0,'staffMemberId':" + minh + ",'overrideReason':'Lan về sớm'}")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SHIFT_ALREADY_OPEN"));
        // Quản lý cùng chi nhánh bán hàng thì hóa đơn vẫn vào ca đang mở
        mvc.perform(post("/api/invoices").header("Authorization", manager).contentType(MediaType.APPLICATION_JSON).content(CASH_SALE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shiftSessionId").isNumber());
    }

    @Test
    @DisplayName("Ngoài khung giờ: thu ngân bị chặn, quản lý mở được khi ghi lý do")
    void openWindowEnforced() throws Exception {
        Long branch = newBranch("CN khung giờ");
        String counter = newUser("window_counter", Role.CASHIER, branch);
        String manager = newUser("window_manager", Role.MANAGER, branch);
        // Ca không mở được vào giờ chạy test (5 ca mặc định không trùng giờ nên luôn có)
        long closedSlot = workShiftService.activeShiftsOf(branch).stream()
                .filter(ws -> !ShiftService.canOpenAt(ws, LocalTime.now(), 15) && !ShiftService.canOpenAt(ws, LocalTime.now().plusMinutes(1), 15))
                .findFirst().orElseThrow().getId();
        ShiftTestData.assignNewStaff(staffRepository, assignmentRepository, branch, closedSlot, "Ngoài giờ");
        String open = json("{'workShiftId':" + closedSlot + ",'openingCash':0}");

        ReflectionTestUtils.setField(shiftService, "enforceOpenWindow", true);
        try {
            mvc.perform(post("/api/shifts/open").header("Authorization", counter).contentType(MediaType.APPLICATION_JSON).content(open))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("OUTSIDE_SHIFT_WINDOW"));
            mvc.perform(post("/api/shifts/open").header("Authorization", manager).contentType(MediaType.APPLICATION_JSON).content(open))
                    .andExpect(jsonPath("$.code").value("OVERRIDE_REASON_REQUIRED"));
            mvc.perform(post("/api/shifts/open").header("Authorization", manager).contentType(MediaType.APPLICATION_JSON)
                            .content(json("{'workShiftId':" + closedSlot + ",'openingCash':0,'overrideReason':'Tăng ca kiểm kho'}")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.openOverrideReason").value("Tăng ca kiểm kho"));
        } finally {
            ReflectionTestUtils.setField(shiftService, "enforceOpenWindow", false);
        }
    }

    @Test
    @DisplayName("Thu/chi két làm đổi tiền dự kiến; đối soát VietQR, thẻ; ca lệch chờ duyệt; ca sau nhận két")
    void cashMovementsReconcileAndReview() throws Exception {
        Long branch = newBranch("CN đối soát");
        String counter = newUser("recon_counter", Role.CASHIER, branch);
        String manager = newUser("recon_manager", Role.MANAGER, branch);
        long shift = open(counter, branch, "Hoa", 500000);
        sell(counter); // +36.000 tiền mặt
        mvc.perform(post("/api/invoices").header("Authorization", counter).contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentMethod\":\"CARD\",\"items\":[{\"productId\":1,\"quantity\":1}]}")).andExpect(status().isOk());

        String expense = mvc.perform(post("/api/shifts/current/cash-movements").header("Authorization", counter)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'type':'EXPENSE','category':'Vận chuyển','amount':10000,'reason':'Ship hàng'}")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long expenseId = JsonPath.parse(expense).read("$.data.id", Long.class);
        mvc.perform(post("/api/shifts/current/cash-movements").header("Authorization", counter)
                        .contentType(MediaType.APPLICATION_JSON).content(json("{'type':'DROP','amount':100000,'reason':'Két đầy'}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COUNTERPART_REQUIRED"));
        mvc.perform(post("/api/shifts/current/cash-movements").header("Authorization", counter)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'type':'DROP','amount':100000,'reason':'Két đầy','counterpart':'Chị quản lý'}")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/shifts/current/cash-movements").header("Authorization", counter)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'type':'FLOAT_IN','amount':50000,'reason':'Đổi tiền lẻ','counterpart':'Chị quản lý'}")))
                .andExpect(status().isOk());
        // Khoản bị xóa không còn tính vào két
        String extra = mvc.perform(post("/api/shifts/current/cash-movements").header("Authorization", counter)
                        .contentType(MediaType.APPLICATION_JSON).content(json("{'type':'EXPENSE','amount':99000,'reason':'Nhập nhầm'}")))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(delete("/api/shifts/cash-movements/" + JsonPath.parse(extra).read("$.data.id", Long.class))
                .header("Authorization", counter)).andExpect(status().isOk());

        // 500.000 + 36.000 - 10.000 - 100.000 + 50.000
        mvc.perform(get("/api/shifts/current").header("Authorization", counter))
                .andExpect(jsonPath("$.data.session.expectedCash").value(476000.0))
                .andExpect(jsonPath("$.data.session.expenses").value(10000.0))
                .andExpect(jsonPath("$.data.session.expectedCard").value(36000.0))
                .andExpect(jsonPath("$.data.session.expectedVietqr").value(0.0));
        mvc.perform(get("/api/shifts/current/cash-movements").header("Authorization", counter))
                .andExpect(jsonPath("$.data.length()").value(4));

        // Đếm theo mệnh giá phải khớp tổng
        mvc.perform(post("/api/shifts/current/close").header("Authorization", counter).contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'closingCash':476000,'denominations':{'500000':1}}")))
                .andExpect(jsonPath("$.code").value("DENOMINATION_MISMATCH"));
        // Phiếu máy POS thẻ thiếu 1.000đ → phải ghi lý do
        mvc.perform(post("/api/shifts/current/close").header("Authorization", counter).contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'closingCash':476000,'countedCard':35000}")))
                .andExpect(jsonPath("$.code").value("DIFFERENCE_REASON_REQUIRED"));
        mvc.perform(post("/api/shifts/current/close").header("Authorization", counter).contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'closingCash':476000,'countedCard':35000,'differenceReason':'Quẹt thẻ lỗi 1 giao dịch',"
                                + "'leftInDrawer':300000,'denominations':{'200000':2,'50000':1,'20000':1,'5000':1,'1000':1}}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.data.cashDifference").value(0.0))
                .andExpect(jsonPath("$.data.cardDifference").value(-1000.0))
                .andExpect(jsonPath("$.data.finalDrop").value(176000.0))
                .andExpect(jsonPath("$.data.denominations.200000").value(2));

        // Ca đã chốt thì không sửa thu/chi được
        mvc.perform(delete("/api/shifts/cash-movements/" + expenseId).header("Authorization", counter))
                .andExpect(status().isConflict());

        // Quầy không duyệt được; quản lý duyệt thì khóa sổ
        String review = json("{'note':'Đã gọi ngân hàng kiểm tra'}");
        mvc.perform(post("/api/shifts/" + shift + "/review").header("Authorization", counter)
                .contentType(MediaType.APPLICATION_JSON).content(review)).andExpect(status().isForbidden());
        mvc.perform(get("/api/shifts").header("Authorization", manager))
                .andExpect(jsonPath("$.data.pendingReviewShifts").value(1));
        mvc.perform(post("/api/shifts/" + shift + "/review").header("Authorization", manager)
                        .contentType(MediaType.APPLICATION_JSON).content(review))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andExpect(jsonPath("$.data.reviewedByName").value("recon_manager"));

        // Ca sau nhận két: ca trước để lại 300.000, đếm được khác thì phải ghi lý do
        ShiftTestData.assignNewStaff(staffRepository, assignmentRepository, branch, 2, "Tuấn");
        mvc.perform(get("/api/shifts/current").header("Authorization", counter))
                .andExpect(jsonPath("$.data.handoverExpected").value(300000.0))
                .andExpect(jsonPath("$.data.suggestedOpeningCash").value(300000.0));
        mvc.perform(post("/api/shifts/open").header("Authorization", counter).contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'workShiftId':2,'openingCash':290000}")))
                .andExpect(jsonPath("$.code").value("HANDOVER_REASON_REQUIRED"));
        mvc.perform(post("/api/shifts/open").header("Authorization", counter).contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'workShiftId':2,'openingCash':290000,'handoverReason':'Thiếu 1 tờ 10.000'}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.handoverDifference").value(-10000.0))
                .andExpect(jsonPath("$.data.employeeName").value("Tuấn"));
    }

    @Test
    @DisplayName("Tài khoản quầy xem các ca của chi nhánh kèm hóa đơn; không xem được ca chi nhánh khác")
    void counterSeesBranchShiftsWithInvoices() throws Exception {
        Long branch = newBranch("CN ca riêng");
        Long other = newBranch("CN khác");
        String alice = newUser("alice_shift", Role.CASHIER, branch);
        String bob = newUser("bob_shift", Role.CASHIER, other);

        long aliceShift = open(alice, branch, "Alice", 200000);
        sell(alice);
        close(alice, 230000); // dự kiến 236.000 → thiếu 6.000

        long bobShift = open(bob, other, "Bob", 0);
        close(bob, 0);

        mvc.perform(get("/api/shifts").header("Authorization", alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalShifts").value(1))
                .andExpect(jsonPath("$.data.shifts[0].id").value(aliceShift))
                .andExpect(jsonPath("$.data.shifts[0].branchName").value("CN ca riêng"))
                .andExpect(jsonPath("$.data.totalShortage").value(6000.0));
        // Gửi chi nhánh khác cũng chỉ ra ca chi nhánh mình
        mvc.perform(get("/api/shifts").param("branchId", String.valueOf(other)).header("Authorization", alice))
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

        long c1Shift = open(c1, b1, "Nhân viên lọc", 100000);
        sell(c1);
        close(c1, 140000); // dự kiến 136.000 → dư 4.000
        open(c2, b2, "Nhân viên quên", 0); // để mở, quên đóng

        mvc.perform(get("/api/shifts").param("branchId", String.valueOf(b1)).header("Authorization", admin))
                .andExpect(jsonPath("$.data.totalShifts").value(1))
                .andExpect(jsonPath("$.data.totalOverage").value(4000.0))
                .andExpect(jsonPath("$.data.mismatchedShifts").value(1));
        String c1Detail = mvc.perform(get("/api/shifts/" + c1Shift).header("Authorization", admin))
                .andReturn().getResponse().getContentAsString();
        long staff = JsonPath.parse(c1Detail).read("$.data.staffMemberId", Long.class);
        mvc.perform(get("/api/shifts").param("staffId", String.valueOf(staff)).header("Authorization", admin))
                .andExpect(jsonPath("$.data.shifts.length()").value(1))
                .andExpect(jsonPath("$.data.shifts[0].employeeName").value("Nhân viên lọc"));
        String openList = mvc.perform(get("/api/shifts").param("branchId", String.valueOf(b2)).param("status", "OPEN")
                        .header("Authorization", admin))
                .andExpect(jsonPath("$.data.openShifts").value(1))
                .andReturn().getResponse().getContentAsString();
        long forgotten = JsonPath.parse(openList).read("$.data.shifts[0].id", Long.class);

        // Quản trị (đang đăng nhập chi nhánh 1) đóng hộ ca quên đóng ở chi nhánh 2
        mvc.perform(post("/api/shifts/" + forgotten + "/close").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json("{'closingCash':0,'handoverNote':'Đóng hộ'}")))
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
        String counter = newUser("refund_counter", Role.CASHIER, branch);
        String manager = newUser("refund_manager", Role.MANAGER, branch);

        long morningShift = open(counter, branch, "Ca sáng", 100000);
        long invoice = sell(counter);
        close(counter, 136000);

        long eveningShift = open(counter, branch, "Ca tối", 136000);
        mvc.perform(post("/api/invoices/" + invoice + "/refund").header("Authorization", manager)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Hàng lỗi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refundShiftSessionId").value(eveningShift));

        // Ca sáng đã chốt số, không đổi
        mvc.perform(get("/api/shifts/" + morningShift).header("Authorization", counter))
                .andExpect(jsonPath("$.data.cashDifference").value(0.0))
                .andExpect(jsonPath("$.data.status").value("CLOSED"));
        // Ca tối: 136.000 - 36.000 hoàn trả
        mvc.perform(get("/api/shifts/" + eveningShift).header("Authorization", counter))
                .andExpect(jsonPath("$.data.cashRefunds").value(36000.0))
                .andExpect(jsonPath("$.data.expectedCash").value(100000.0));
        mvc.perform(get("/api/shifts/" + eveningShift + "/invoices").header("Authorization", counter))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].kind").value("REFUND_OTHER_SHIFT"))
                .andExpect(jsonPath("$.data[0].refundCountedHere").value(true));

        close(counter, 100000);
        mvc.perform(get("/api/shifts/" + eveningShift).header("Authorization", counter))
                .andExpect(jsonPath("$.data.cashDifference").value(0.0))
                .andExpect(jsonPath("$.data.cashRefunds").value(36000.0));
    }

    @Test
    @DisplayName("Hoàn trả ngay trong ca đang mở: doanh số giữ nguyên, tiền mặt dự kiến trừ phần hoàn trả")
    void refundWithinSameShift() throws Exception {
        Long branch = newBranch("CN hoàn trong ca");
        String cashier = newUser("same_shift_cashier", Role.CASHIER, branch);
        String manager = newUser("same_shift_manager", Role.MANAGER, branch);

        long shift = open(cashier, branch, "Trong ca", 50000);
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

    @Test
    @DisplayName("Mỗi chi nhánh tự cấu hình số ca: chi nhánh riêng 2 ca, chi nhánh khác vẫn dùng lịch mẫu")
    void branchHasOwnSchedule() throws Exception {
        Long custom = newBranch("CN 2 ca");
        Long other = newBranch("CN lịch mẫu");
        String admin = newUser("custom_admin", Role.ADMIN, custom);
        String counter = newUser("custom_counter", Role.CASHIER, custom);
        String otherCounter = newUser("other_counter", Role.CASHIER, other);
        int templateSize = workShiftService.shiftsOf(null).size();

        // Tạo lịch riêng từ lịch mẫu rồi xoá bớt cho còn 2 ca
        String copied = mvc.perform(post("/api/shifts/work-shifts/customize").param("branchId", custom.toString())
                        .header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(templateSize))
                .andReturn().getResponse().getContentAsString();
        java.util.List<Integer> ids = JsonPath.parse(copied).read("$.data[*].id");
        for (int i = 2; i < ids.size(); i++) {
            mvc.perform(delete("/api/shifts/work-shifts/" + ids.get(i)).header("Authorization", admin))
                    .andExpect(status().isOk());
        }
        mvc.perform(post("/api/shifts/work-shifts/customize").param("branchId", custom.toString())
                        .header("Authorization", admin))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/shifts/work-shifts").header("Authorization", counter))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].branchId").value(custom));
        mvc.perform(get("/api/shifts/work-shifts").header("Authorization", otherCounter))
                .andExpect(jsonPath("$.data.length()").value(templateSize));

        // Ca mẫu (id 2) không thuộc lịch ca của chi nhánh đã cấu hình riêng
        mvc.perform(put("/api/shifts/assignments").header("Authorization", admin).param("branchId", custom.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'workShiftId':2,'shiftDate':'" + LocalDate.now() + "','staffMemberId':1}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WORK_SHIFT_NOT_FOUND"));

        // Mở ca bằng ca riêng của chi nhánh; ca đã dùng thì không xoá được
        long ownShift = ids.get(0);
        ShiftTestData.assignNewStaff(staffRepository, assignmentRepository, custom, ownShift, "Ca riêng");
        mvc.perform(post("/api/shifts/open").header("Authorization", counter).contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'workShiftId':" + ownShift + ",'openingCash':0}")))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/shifts/work-shifts/" + ownShift).header("Authorization", admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WORK_SHIFT_IN_USE"));
    }
}
