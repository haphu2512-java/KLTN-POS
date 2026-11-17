package com.pos.audit;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class AuditLogIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JwtTokenProvider tokenProvider;
    @Autowired private CustomUserDetailsService userDetailsService;
    @Autowired private UserRepository userRepository;
    @Autowired private AuditLogRepository auditLogRepository;

    private String tokenFor(String username) {
        User user = userRepository.findByUsername(username).orElseThrow();
        UserDetails details = userDetailsService.loadUserByUsername(username);
        return "Bearer " + tokenProvider.generateToken(details, TestAuth.branchOf(user), user.getRole().name(), user.getFullName());
    }

    @Test
    @DisplayName("Đổi giá sản phẩm ghi nhật ký kèm giá cũ → mới và người sửa; admin tra cứu được, thu ngân bị chặn")
    void priceChangeIsAudited() throws Exception {
        String admin = tokenFor("admin");
        // Tạo sản phẩm riêng để không đổi giá dữ liệu mẫu mà test khác dùng chung (cùng database H2)
        String created = mvc.perform(post("/api/products").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"SP kiểm thử nhật ký\",\"salePrice\":10000}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String id = String.valueOf(com.jayway.jsonpath.JsonPath.<Integer>read(created, "$.data.id"));
        mvc.perform(put("/api/products/" + id).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"SP kiểm thử nhật ký\",\"salePrice\":123456}"))
                .andExpect(status().isOk());

        AuditLog row = auditLogRepository.findAll().stream()
                .filter(a -> a.getAction() == AuditAction.PRICE_CHANGE && id.equals(a.getEntityId()))
                .reduce((a, b) -> b).orElseThrow();
        assertThat(row.getActorUsername()).isEqualTo("admin");
        assertThat(row.getSummary()).contains("Giá bán").contains("123.456đ");

        mvc.perform(get("/api/audit-logs").param("action", "PRICE_CHANGE").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].summary", hasItem(row.getSummary())))
                .andExpect(jsonPath("$.data.items[0].actionLabel").value("Đổi giá sản phẩm"));
        mvc.perform(get("/api/audit-logs").header("Authorization", tokenFor("cashier")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Đăng nhập sai vẫn được ghi nhật ký dù yêu cầu trả lỗi")
    void failedLoginIsAudited() throws Exception {
        long before = auditLogRepository.count();
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"sai-mat-khau\"}"))
                .andExpect(status().is4xxClientError());
        assertThat(auditLogRepository.count()).isEqualTo(before + 1);
        AuditLog last = auditLogRepository.findAll().stream().reduce((a, b) -> b).orElseThrow();
        assertThat(last.getAction()).isEqualTo(AuditAction.LOGIN_FAILED);
        assertThat(last.getActorUsername()).isEqualTo("admin");
    }
}
