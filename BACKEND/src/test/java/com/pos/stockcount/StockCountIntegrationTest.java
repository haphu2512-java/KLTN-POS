package com.pos.stockcount;

import com.jayway.jsonpath.JsonPath;
import com.pos.inventory.InventoryRepository;
import com.pos.product.ProductRepository;
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
class StockCountIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JwtTokenProvider tokenProvider;
    @Autowired private CustomUserDetailsService userDetailsService;
    @Autowired private UserRepository userRepository;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private ProductRepository productRepository;

    private String tokenFor(String username) {
        User user = userRepository.findByUsername(username).orElseThrow();
        UserDetails details = userDetailsService.loadUserByUsername(username);
        return "Bearer " + tokenProvider.generateToken(details, TestAuth.branchOf(user), user.getRole().name(), user.getFullName());
    }

    @Test
    @DisplayName("Kiểm kê: quét mã cộng dồn, dòng lệch phải có lý do, duyệt thì trừ đúng phần chênh lệch và có trong báo cáo hao hụt")
    void countSubmitApproveAdjustsStock() throws Exception {
        String admin = tokenFor("admin");
        // Sản phẩm riêng của test để không ảnh hưởng tồn mà test khác dùng (cùng database H2)
        String p = mvc.perform(post("/api/products").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"SP kiểm kê\",\"salePrice\":20000,\"costPrice\":15000}"))
                .andReturn().getResponse().getContentAsString();
        long productId = JsonPath.<Integer>read(p, "$.data.id").longValue();
        String barcode = JsonPath.read(p, "$.data.barcode");
        mvc.perform(post("/api/stock-in").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"branchId\":1,\"items\":[{\"productId\":" + productId + ",\"quantity\":10,\"unitCost\":15000}]}"));
        int before = inventoryRepository.findQuantity(productId, 1L);

        String created = mvc.perform(post("/api/stock-counts").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Kiểm kê cuối tháng\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long id = JsonPath.<Integer>read(created, "$.data.id").longValue();

        // Quét 3 lần, mỗi lần cộng 1; rồi sửa thành before - 2
        for (int i = 0; i < 3; i++) {
            mvc.perform(put("/api/stock-counts/" + id + "/lines").header("Authorization", admin)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"barcode\":\"" + barcode + "\",\"add\":true}"))
                    .andExpect(status().isOk());
        }
        mvc.perform(get("/api/stock-counts/" + id).header("Authorization", admin))
                .andExpect(jsonPath("$.data.lines[0].countedQty").value(3))
                .andExpect(jsonPath("$.data.lines[0].systemQty").value(before));
        mvc.perform(put("/api/stock-counts/" + id + "/lines").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":" + productId + ",\"countedQty\":" + (before - 2) + "}"));

        mvc.perform(post("/api/stock-counts/" + id + "/submit").header("Authorization", admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ADJUST_REASON_REQUIRED"));
        mvc.perform(put("/api/stock-counts/" + id + "/lines").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":" + productId + ",\"countedQty\":" + (before - 2) + ",\"reason\":\"DAMAGED\"}"));
        mvc.perform(post("/api/stock-counts/" + id + "/submit").header("Authorization", admin)).andExpect(status().isOk());

        mvc.perform(post("/api/stock-counts/" + id + "/approve").header("Authorization", tokenFor("cashier")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/stock-counts/" + id + "/approve").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.varianceValue").value(-30000.0));
        assertThat(inventoryRepository.findQuantity(productId, 1L)).isEqualTo(before - 2);

        mvc.perform(get("/api/stock-counts/shrinkage").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.byReason[?(@.reason=='DAMAGED')].quantity").value(org.hamcrest.Matchers.hasItem(-2)));
        // Phiếu đã duyệt không sửa được
        mvc.perform(put("/api/stock-counts/" + id + "/lines").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"productId\":" + productId + ",\"countedQty\":1}"))
                .andExpect(status().isConflict());
    }
}
