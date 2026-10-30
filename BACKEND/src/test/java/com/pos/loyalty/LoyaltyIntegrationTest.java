package com.pos.loyalty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pos.customer.CustomerRepository;
import com.pos.security.CustomUserDetailsService;
import com.pos.security.JwtTokenProvider;
import com.pos.support.TestAuth;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Luồng điểm thành viên trên H2: tạo khách (không tự đặt điểm được) → mua được cộng điểm →
 * dùng điểm trả một phần → hoàn trả trả lại điểm đã dùng và thu hồi điểm đã cộng, mọi bước có trong sổ điểm.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:loyaltydb;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "pos.shift.require-open-shift=false"
})
@AutoConfigureMockMvc
class LoyaltyIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomerRepository customerRepository;

    private String tokenFor(String username) {
        User user = userRepository.findByUsername(username).orElseThrow();
        UserDetails details = userDetailsService.loadUserByUsername(username);
        return "Bearer " + tokenProvider.generateToken(details, TestAuth.branchOf(user), user.getRole().name(), user.getFullName());
    }

    private JsonNode sell(String token, long customerId, int quantity, int redeemPoints) throws Exception {
        String body = "{\"paymentMethod\":\"CARD\",\"customerId\":" + customerId + ",\"redeemPoints\":" + redeemPoints
                + ",\"items\":[{\"productId\":1,\"quantity\":" + quantity + "}]}";
        String json = mvc.perform(post("/api/invoices").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("data");
    }

    private int balance(long customerId) {
        return customerRepository.findPointsById(customerId);
    }

    @Test
    @DisplayName("Tích điểm, đổi điểm, hoàn trả và sổ điểm")
    void earnRedeemRefund() throws Exception {
        String admin = tokenFor("admin");

        // Client gửi kèm points nhưng bị bỏ qua; SĐT được chuẩn hóa
        String created = mvc.perform(post("/api/customers").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Chị Lan\",\"phone\":\"+84 912 345 678\",\"points\":999}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phone").value("0912345678"))
                .andExpect(jsonPath("$.data.points").value(0))
                .andReturn().getResponse().getContentAsString();
        long customerId = objectMapper.readTree(created).get("data").get("id").asLong();

        mvc.perform(post("/api/customers").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Trùng\",\"phone\":\"0912.345.678\"}"))
                .andExpect(jsonPath("$.code").value("PHONE_ALREADY_EXISTS"));

        // 10 x 36.000 = 360.000đ → 36 điểm
        JsonNode first = sell(admin, customerId, 10, 0);
        assertEquals(36, first.get("pointsEarned").asInt());
        assertEquals(36, first.get("customerPointsBalance").asInt());
        assertEquals(36, balance(customerId));

        // Hóa đơn 36.000đ dùng tối đa 50% = 18 điểm (18.000đ); còn trả 18.000đ → được 1 điểm
        JsonNode second = sell(admin, customerId, 1, 18);
        assertEquals(18000.0, second.get("pointsDiscount").asDouble());
        assertEquals(18000.0, second.get("totalAmount").asDouble());
        assertEquals(1, second.get("pointsEarned").asInt());
        assertEquals(19, balance(customerId));

        // Vượt trần 50% hoặc dưới mức tối thiểu bị từ chối
        mvc.perform(post("/api/invoices").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"CARD\",\"customerId\":" + customerId
                                + ",\"redeemPoints\":19,\"items\":[{\"productId\":1,\"quantity\":1}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REDEEM"));
        mvc.perform(post("/api/invoices").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"CARD\",\"customerId\":" + customerId
                                + ",\"redeemPoints\":5,\"items\":[{\"productId\":1,\"quantity\":1}]}"))
                .andExpect(jsonPath("$.code").value("INVALID_REDEEM"));
        assertEquals(19, balance(customerId));

        // Hoàn hóa đơn thứ hai: trả lại 18 điểm đã dùng, thu hồi 1 điểm đã cộng
        mvc.perform(post("/api/invoices/" + second.get("id").asLong() + "/refund").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Khách đổi ý\"}"))
                .andExpect(status().isOk());
        assertEquals(36, balance(customerId));

        mvc.perform(get("/api/loyalty/customers/" + customerId + "/transactions").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(5))
                .andExpect(jsonPath("$.data[0].type").value("REFUND_EARN"))
                .andExpect(jsonPath("$.data[0].balanceAfter").value(36))
                .andExpect(jsonPath("$.data[1].type").value("REFUND_REDEEM"))
                .andExpect(jsonPath("$.data[4].type").value("EARN"));

        // Điều chỉnh tay: cần lý do, không trừ quá số dư
        mvc.perform(post("/api/loyalty/customers/" + customerId + "/adjust").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"points\":-100,\"reason\":\"Nhập sai\"}"))
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_POINTS"));
        mvc.perform(post("/api/loyalty/customers/" + customerId + "/adjust").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"points\":4,\"reason\":\"Bù điểm hóa đơn giấy\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.points").value(40));
        assertEquals(40, balance(customerId));
    }

    @Test
    @DisplayName("Khách không tồn tại thì không âm thầm bán thành khách lẻ")
    void unknownCustomerRejected() throws Exception {
        mvc.perform(post("/api/invoices").header("Authorization", tokenFor("admin")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"CARD\",\"customerId\":1730000000000,\"items\":[{\"productId\":1,\"quantity\":1}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CUSTOMER_NOT_FOUND"));
    }

    @Test
    @DisplayName("Quy tắc điểm mặc định")
    void policy() throws Exception {
        mvc.perform(get("/api/loyalty/policy").header("Authorization", tokenFor("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.earnPerAmount").value(10000))
                .andExpect(jsonPath("$.data.pointValue").value(1000))
                .andExpect(jsonPath("$.data.redeemMaxPercent").value(50))
                .andExpect(jsonPath("$.data.minRedeemPoints").value(10));
    }
}
