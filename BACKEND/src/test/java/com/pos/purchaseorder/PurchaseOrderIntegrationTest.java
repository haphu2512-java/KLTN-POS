package com.pos.purchaseorder;

import com.jayway.jsonpath.JsonPath;
import com.pos.inventory.InventoryRepository;
import com.pos.product.ProductRepository;
import com.pos.purchase.Supplier;
import com.pos.purchase.SupplierRepository;
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
class PurchaseOrderIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JwtTokenProvider tokenProvider;
    @Autowired private CustomUserDetailsService userDetailsService;
    @Autowired private UserRepository userRepository;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private SupplierRepository supplierRepository;

    private String tokenFor(String username) {
        User user = userRepository.findByUsername(username).orElseThrow();
        UserDetails details = userDetailsService.loadUserByUsername(username);
        return "Bearer " + tokenProvider.generateToken(details, TestAuth.branchOf(user), user.getRole().name(), user.getFullName());
    }

    @Test
    @DisplayName("Đơn đặt hàng: gửi cần NCC, nhận một phần rồi nhận đủ, tồn kho tăng đúng số nhận")
    void draftSendReceivePartialThenFull() throws Exception {
        String admin = tokenFor("admin");
        String p = mvc.perform(post("/api/products").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"SP đặt hàng\",\"salePrice\":20000,\"costPrice\":12000}"))
                .andReturn().getResponse().getContentAsString();
        long productId = JsonPath.<Integer>read(p, "$.data.id").longValue();
        String s = mvc.perform(post("/api/suppliers").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"NCC đặt hàng test\",\"phone\":\"0900000001\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long supplierId = JsonPath.<Integer>read(s, "$.data.id").longValue();
        Integer q0 = inventoryRepository.findQuantity(productId, 1L);
        int before = q0 == null ? 0 : q0;

        String created = mvc.perform(post("/api/purchase-orders").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[{\"productId\":" + productId + ",\"orderedQty\":10}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.lines[0].unitCost").value(12000.0))
                .andReturn().getResponse().getContentAsString();
        long id = JsonPath.<Integer>read(created, "$.data.id").longValue();

        // Chưa chọn NCC thì không gửi được
        mvc.perform(post("/api/purchase-orders/" + id + "/send").header("Authorization", admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PO_SUPPLIER_REQUIRED"));
        mvc.perform(put("/api/purchase-orders/" + id).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":" + supplierId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.supplierName").value("NCC đặt hàng test"));
        mvc.perform(post("/api/purchase-orders/" + id + "/send").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SENT"));
        // Đã gửi thì không sửa được
        mvc.perform(put("/api/purchase-orders/" + id).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"x\"}"))
                .andExpect(status().isConflict());

        // Nhận vượt số còn lại bị chặn
        mvc.perform(post("/api/purchase-orders/" + id + "/receive").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[{\"productId\":" + productId + ",\"quantity\":11}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PO_QTY_EXCEEDED"));
        mvc.perform(post("/api/purchase-orders/" + id + "/receive").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[{\"productId\":" + productId + ",\"quantity\":4}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.order.status").value("PARTIALLY_RECEIVED"))
                .andExpect(jsonPath("$.data.order.lines[0].receivedQty").value(4));
        assertThat(inventoryRepository.findQuantity(productId, 1L)).isEqualTo(before + 4);

        // Không gửi dòng nào = nhận đủ phần còn lại
        mvc.perform(post("/api/purchase-orders/" + id + "/receive").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.order.status").value("RECEIVED"));
        assertThat(inventoryRepository.findQuantity(productId, 1L)).isEqualTo(before + 10);

        mvc.perform(post("/api/purchase-orders/" + id + "/cancel").header("Authorization", admin))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/purchase-orders").header("Authorization", tokenFor("cashier")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Đơn đặt hàng từ AI: lập được đơn nháp theo đề xuất, hoặc báo rõ khi AI chưa đề xuất gì")
    void fromForecastCreatesDraftsOrExplains() throws Exception {
        String res = mvc.perform(post("/api/purchase-orders/from-forecast?days=7").header("Authorization", tokenFor("admin")))
                .andReturn().getResponse().getContentAsString();
        boolean ok = JsonPath.read(res, "$.success");
        if (ok) {
            assertThat(JsonPath.<java.util.List<Object>>read(res, "$.data")).isNotEmpty();
            assertThat(JsonPath.<String>read(res, "$.data[0].status")).isEqualTo("DRAFT");
            assertThat(JsonPath.<String>read(res, "$.data[0].source")).isEqualTo("FORECAST");
            // Mặt hàng chỉ vào đơn của NCC có bán đúng danh mục của nó
            java.util.Map<Long, java.util.List<String>> catsOf = new java.util.HashMap<>();
            for (Supplier sup : supplierRepository.findAll()) {
                catsOf.put(sup.getId(), sup.getCategories().stream().map(c -> c.toLowerCase(java.util.Locale.ROOT)).toList());
            }
            java.util.List<java.util.Map<String, Object>> orders = JsonPath.read(res, "$.data");
            for (java.util.Map<String, Object> o : orders) {
                if (o.get("supplierId") == null) continue;
                java.util.List<String> cats = catsOf.get(((Number) o.get("supplierId")).longValue());
                for (java.util.Map<String, Object> line : (java.util.List<java.util.Map<String, Object>>) o.get("lines")) {
                    assertThat(cats).contains(String.valueOf(line.get("category")).toLowerCase(java.util.Locale.ROOT));
                }
            }
        } else {
            assertThat(JsonPath.<String>read(res, "$.code")).isEqualTo("NO_SUGGESTION");
        }
    }

    @Test
    @DisplayName("NCC: thêm/sửa danh mục cung cấp, bỏ trùng, và lấy được danh sách danh mục để chọn")
    void supplierCategories() throws Exception {
        String admin = tokenFor("admin");
        String s = mvc.perform(post("/api/suppliers").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" NCC bánh \",\"categories\":[\"Bánh gạo\",\" bánh gạo \",\"\",\"Bánh quy\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("NCC bánh"))
                .andExpect(jsonPath("$.data.categories.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        long id = JsonPath.<Integer>read(s, "$.data.id").longValue();
        mvc.perform(put("/api/suppliers/" + id).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"NCC bánh\",\"phone\":\"0900\",\"categories\":[\"Kẹo các loại\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phone").value("0900"))
                .andExpect(jsonPath("$.data.categories[0]").value("Kẹo các loại"))
                .andExpect(jsonPath("$.data.categories.length()").value(1));
        mvc.perform(put("/api/suppliers/" + id).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest());
        String opts = mvc.perform(get("/api/suppliers/categories").header("Authorization", admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<java.util.List<String>>read(opts, "$.data")).contains("Kẹo các loại", "Sữa & Bơ");
        // NCC mẫu được gán danh mục mặc định lúc khởi động
        assertThat(supplierRepository.findAll().stream().filter(x -> x.getName().contains("Vinamilk")).findFirst().orElseThrow()
                .getCategories()).contains("Sữa & Bơ");
    }
}
