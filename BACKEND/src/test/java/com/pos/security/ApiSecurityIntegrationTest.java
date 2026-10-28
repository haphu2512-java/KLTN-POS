package com.pos.security;

import com.pos.support.TestAuth;
import com.pos.user.Role;
import com.pos.user.User;
import com.pos.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import com.pos.branch.BranchRepository;
import com.pos.sale.Invoice;
import com.pos.sale.InvoiceRepository;
import com.pos.sale.PaymentMethod;
import com.pos.shift.ShiftAssignmentRepository;
import com.pos.shift.ShiftSession;
import com.pos.shift.ShiftSessionRepository;
import com.pos.shift.ShiftStatus;
import com.pos.shift.WorkShiftRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

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

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private ShiftSessionRepository shiftSessionRepository;

    @Autowired
    private WorkShiftRepository workShiftRepository;

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
    @DisplayName("Thu ngân không xem được lịch sử ca, lịch xếp ca, danh sách hóa đơn, nhà cung cấp, phiếu nhập")
    void cashierBlockedFromBackOfficeReads() throws Exception {
        for (String url : new String[]{"/api/shifts", "/api/invoices", "/api/suppliers", "/api/stock-in"}) {
            mvc.perform(get(url).header("Authorization", cashierToken)).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/shifts/assignments").param("from", "2026-01-01").param("to", "2026-01-01")
                        .header("Authorization", cashierToken))
                .andExpect(status().isForbidden());
        // Các API POS cần vẫn mở
        for (String url : new String[]{"/api/shifts/current", "/api/staff", "/api/customers"}) {
            mvc.perform(get(url).header("Authorization", cashierToken)).andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("Thủ kho không bán hàng, không mở ca, không xem khách hàng; vẫn xem nhà cung cấp và phiếu nhập")
    void warehouseBlockedFromSelling() throws Exception {
        userRepository.save(User.builder().username("wh_user").password("x")
                .fullName("Thủ kho").role(Role.WAREHOUSE).branchIds(TestAuth.branches(1L)).active(true).build());
        String warehouse = tokenFor("wh_user");

        mvc.perform(post("/api/invoices").header("Authorization", warehouse)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":1,\"paymentMethod\":\"CARD\",\"items\":[{\"productId\":1,\"quantity\":1}]}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/shifts/open").header("Authorization", warehouse)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"workShiftId\":1,\"openingCash\":0}"))
                .andExpect(status().isForbidden());
        for (String url : new String[]{"/api/shifts/current", "/api/staff", "/api/customers"}) {
            mvc.perform(get(url).header("Authorization", warehouse)).andExpect(status().isForbidden());
        }
        for (String url : new String[]{"/api/suppliers", "/api/stock-in"}) {
            mvc.perform(get(url).header("Authorization", warehouse)).andExpect(status().isOk());
        }
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

    @Test
    @DisplayName("Chi tiết hóa đơn: thu ngân chỉ xem hóa đơn trong ca của mình, admin xem được mọi hóa đơn")
    void invoiceDetailScopedToOwnShift() throws Exception {
        User cashier = userRepository.findByUsername("cashier").orElseThrow();
        User other = userRepository.save(User.builder().username("other_cashier").password("x")
                .fullName("Thu ngân khác").role(Role.CASHIER).branchIds(TestAuth.branches(1L)).active(true).build());

        Long ownShift = closedShift(cashier.getId());
        Long otherShift = closedShift(other.getId());
        Long ownInvoice = invoiceIn(ownShift, cashier.getId());
        Long otherInvoice = invoiceIn(otherShift, other.getId());
        Long noShiftInvoice = invoiceIn(null, other.getId());

        mvc.perform(get("/api/invoices/" + ownInvoice).header("Authorization", cashierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.employeeName").value(cashier.getFullName()));
        for (Long id : new Long[]{otherInvoice, noShiftInvoice}) {
            mvc.perform(get("/api/invoices/" + id).header("Authorization", cashierToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("INVOICE_FORBIDDEN"));
        }
        mvc.perform(get("/api/invoices/" + otherInvoice).header("Authorization", tokenFor("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.employeeName").value("Thu ngân khác"));
    }

    private Long closedShift(Long employeeId) {
        return shiftSessionRepository.save(ShiftSession.builder()
                .branchId(1L).workShift(workShiftRepository.findAll().get(0)).employeeId(employeeId)
                .shiftDate(LocalDate.now()).status(ShiftStatus.CLOSED)
                .openedAt(LocalDateTime.now()).openingCash(BigDecimal.ZERO).build()).getId();
    }

    private Long invoiceIn(Long shiftId, Long employeeId) {
        return invoiceRepository.save(Invoice.builder()
                .branch(branchRepository.findById(1L).orElseThrow()).employeeId(employeeId)
                .subtotal(BigDecimal.TEN).totalAmount(BigDecimal.TEN).paymentMethod(PaymentMethod.CARD)
                .shiftSessionId(shiftId).build()).getId();
    }
}
