package com.pos.expiry;

import com.jayway.jsonpath.JsonPath;
import com.pos.inventory.InventoryRepository;
import com.pos.product.ProductRepository;
import com.pos.security.CustomUserDetailsService;
import com.pos.security.JwtTokenProvider;
import com.pos.support.TestAuth;
import com.pos.user.User;
import com.pos.user.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
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
class ExpiryIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JwtTokenProvider tokenProvider;
    @Autowired private CustomUserDetailsService userDetailsService;
    @Autowired private UserRepository userRepository;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private JdbcTemplate jdbc;

    private String tokenFor(String username) {
        User user = userRepository.findByUsername(username).orElseThrow();
        UserDetails details = userDetailsService.loadUserByUsername(username);
        return "Bearer " + tokenProvider.generateToken(details, TestAuth.branchOf(user), user.getRole().name(), user.getFullName());
    }

    @Test
    @DisplayName("Hạn sử dụng: nhập lô có hạn, cảnh báo cận date, chặn nhập hàng đã hết hạn, lập phiếu hủy hàng hết hạn")
    void nearExpiryWarningAndDisposalCount() throws Exception {
        String admin = tokenFor("admin");
        String p = mvc.perform(post("/api/products").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sữa chua test hạn\",\"salePrice\":8000,\"costPrice\":5000}"))
                .andReturn().getResponse().getContentAsString();
        long productId = JsonPath.<Integer>read(p, "$.data.id").longValue();
        String near = LocalDate.now().plusDays(5).toString();
        String far = LocalDate.now().plusDays(90).toString();

        mvc.perform(post("/api/stock-in").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":1,\"items\":[{\"productId\":" + productId + ",\"quantity\":3,\"unitCost\":5000,"
                                + "\"expiryDate\":\"" + LocalDate.now().minusDays(1) + "\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EXPIRY_IN_PAST"));
        mvc.perform(post("/api/stock-in").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":1,\"items\":[{\"productId\":" + productId + ",\"quantity\":6,\"unitCost\":5000,"
                                + "\"lotNumber\":\"L-NEAR\",\"expiryDate\":\"" + near + "\"}]}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/stock-in").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchId\":1,\"items\":[{\"productId\":" + productId + ",\"quantity\":4,\"unitCost\":5000,"
                                + "\"lotNumber\":\"L-FAR\",\"expiryDate\":\"" + far + "\"}]}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/expiry?days=30").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lots[?(@.lotNumber=='L-NEAR')].remainingQty").value(org.hamcrest.Matchers.hasItem(6)))
                .andExpect(jsonPath("$.data.lots[?(@.lotNumber=='L-NEAR')].status").value(org.hamcrest.Matchers.hasItem("NEAR_EXPIRY")))
                .andExpect(jsonPath("$.data.lots[?(@.lotNumber=='L-FAR')]").isEmpty());

        mvc.perform(post("/api/expiry/disposal-count").header("Authorization", tokenFor("cashier")))
                .andExpect(status().isForbidden());

        // Giả lập lô L-NEAR đã quá hạn
        jdbc.update("UPDATE stock_in_detail SET expiry_date = ? WHERE lot_number = 'L-NEAR'", LocalDate.now().minusDays(1));
        String count = mvc.perform(post("/api/expiry/disposal-count").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        java.util.List<Integer> counted = JsonPath.read(count, "$.data.lines[?(@.productId==" + productId + ")].countedQty");
        java.util.List<String> reasons = JsonPath.read(count, "$.data.lines[?(@.productId==" + productId + ")].reason");
        assertThat(counted).containsExactly(4);
        assertThat(reasons).containsExactly("EXPIRED");

        long countId = JsonPath.<Integer>read(count, "$.data.id").longValue();
        mvc.perform(post("/api/stock-counts/" + countId + "/approve").header("Authorization", admin)).andExpect(status().isOk());
        assertThat(inventoryRepository.findQuantity(productId, 1L)).isEqualTo(4);
        // Hủy xong thì lô hết hạn không còn hàng
        mvc.perform(get("/api/expiry").header("Authorization", admin))
                .andExpect(jsonPath("$.data.lots[?(@.productId==" + productId + ")]").isEmpty());
    }
}
