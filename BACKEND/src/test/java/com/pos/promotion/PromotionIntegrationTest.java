package com.pos.promotion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADMIN quản lý khuyến mãi, thu ngân chỉ dùng được khi bán; hóa đơn lưu tiền giảm do server tính.
 * Sản phẩm 1 (Sữa & Bơ) giá 36.000đ, sản phẩm 2 (Bánh kẹo) giá 24.000đ theo dữ liệu mẫu.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:promotiondb;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "pos.shift.require-open-shift=false"
})
@AutoConfigureMockMvc
class PromotionIntegrationTest {

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

    private String tokenFor(String username) {
        User user = userRepository.findByUsername(username).orElseThrow();
        UserDetails details = userDetailsService.loadUserByUsername(username);
        return "Bearer " + tokenProvider.generateToken(details, TestAuth.branchOf(user), user.getRole().name(), user.getFullName());
    }

    private JsonNode data(String json) throws Exception {
        return objectMapper.readTree(json).get("data");
    }

    @Test
    @DisplayName("ADMIN tạo/sửa/tắt/xóa khuyến mãi; thu ngân không quản lý được nhưng bán được với khuyến mãi")
    void manageAndApply() throws Exception {
        String admin = tokenFor("admin");
        String cashier = tokenFor("cashier");
        String body = "{\"name\":\"Sữa giảm 10%\",\"type\":\"PERCENT\",\"value\":10,\"category\":\"Sữa & Bơ\"}";

        // Thu ngân không tạo hay xem danh sách quản lý được
        mvc.perform(post("/api/promotions").header("Authorization", cashier)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/promotions").header("Authorization", cashier))
                .andExpect(status().isForbidden());

        // Dữ liệu sai bị từ chối
        mvc.perform(post("/api/promotions").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sai\",\"type\":\"PERCENT\",\"value\":150}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PROMOTION"));

        long promoId = data(mvc.perform(post("/api/promotions").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();

        String cart = "\"items\":[{\"productId\":1,\"quantity\":2},{\"productId\":2,\"quantity\":1}]";

        // POS xem trước: 10% của 72.000đ tiền sữa = 7.200đ (bánh kẹo không được giảm)
        mvc.perform(post("/api/promotions/applicable").header("Authorization", cashier)
                        .contentType(MediaType.APPLICATION_JSON).content("{" + cart + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].promotionId").value(promoId))
                .andExpect(jsonPath("$.data[0].discountAmount").value(7200));

        // Bán: server tự tính tiền giảm, cộng thêm giảm tay 800đ
        JsonNode invoice = data(mvc.perform(post("/api/invoices").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"CARD\",\"promotionId\":" + promoId + ",\"discount\":800," + cart + "}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertEquals(96000.0, invoice.get("subtotal").asDouble());
        assertEquals(7200.0, invoice.get("promotionDiscount").asDouble());
        assertEquals("Sữa giảm 10%", invoice.get("promotionName").asText());
        assertEquals(88000.0, invoice.get("totalAmount").asDouble());

        // Sửa thành chỉ dành cho thành viên: khách lẻ không áp được nữa
        mvc.perform(put("/api/promotions/" + promoId).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Thành viên giảm 10%\",\"type\":\"PERCENT\",\"value\":10,\"memberOnly\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberOnly").value(true))
                .andExpect(jsonPath("$.data.category").doesNotExist());
        mvc.perform(post("/api/invoices").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"CARD\",\"promotionId\":" + promoId + "," + cart + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROMOTION_NOT_APPLICABLE"));

        // Tắt rồi xóa; thu ngân không tắt được
        mvc.perform(patch("/api/promotions/" + promoId + "/active").header("Authorization", cashier)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/promotions/" + promoId + "/active").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));
        mvc.perform(post("/api/promotions/applicable").header("Authorization", cashier)
                        .contentType(MediaType.APPLICATION_JSON).content("{" + cart + "}"))
                .andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(delete("/api/promotions/" + promoId).header("Authorization", admin))
                .andExpect(status().isOk());
        mvc.perform(get("/api/promotions/" + promoId).header("Authorization", admin))
                .andExpect(status().isNotFound());

        // Hóa đơn cũ vẫn giữ tên và tiền khuyến mãi
        mvc.perform(get("/api/invoices/" + invoice.get("id").asLong()).header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.promotionName").value("Sữa giảm 10%"))
                .andExpect(jsonPath("$.data.promotionDiscount").value(7200));
    }
}
