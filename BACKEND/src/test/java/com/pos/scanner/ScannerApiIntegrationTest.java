package com.pos.scanner;

import com.pos.security.CustomUserDetailsService;
import com.pos.security.JwtTokenProvider;
import com.pos.user.User;
import com.pos.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:scannerdb;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
@AutoConfigureMockMvc
class ScannerApiIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private SimpMessagingTemplate messagingTemplate;

    private String tokenFor(String username) {
        User user = userRepository.findByUsername(username).orElseThrow();
        UserDetails details = userDetailsService.loadUserByUsername(username);
        return "Bearer " + tokenProvider.generateToken(details, user.getBranchId(), user.getRole().name(), user.getFullName());
    }

    private String firstBarcode() throws Exception {
        String body = mvc.perform(get("/api/scanner/products/cache").header("Authorization", tokenFor("cashier")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.data[0].barcode");
    }

    @Test
    @DisplayName("Không có token: mọi API scanner trừ ping đều trả 401")
    void scannerRequiresLogin() throws Exception {
        mvc.perform(get("/api/scanner/ping")).andExpect(status().isOk());
        mvc.perform(get("/api/scanner/products/cache")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/scanner/lookup").param("barcode", "123")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/scanner/broadcast").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"barcode\":\"123\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/scanner/sync-batch").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scans\":[]}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("Danh mục offline không chứa giá vốn")
    void catalogHidesCostPrice() throws Exception {
        mvc.perform(get("/api/scanner/products/cache").header("Authorization", tokenFor("cashier")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].salePrice").exists())
                .andExpect(jsonPath("$.data[0].costPrice").doesNotExist());
    }

    @Test
    @DisplayName("Tra giá không gửi gì tới POS")
    void lookupDoesNotBroadcast() throws Exception {
        String barcode = firstBarcode();
        mvc.perform(get("/api/scanner/lookup").param("barcode", barcode).header("Authorization", tokenFor("cashier")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.barcode").value(barcode))
                .andExpect(jsonPath("$.data.costPrice").doesNotExist());
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("Mã quét chỉ gửi tới POS của đúng tài khoản, gửi trùng clientScanId bị bỏ qua")
    void broadcastGoesOnlyToSameUser() throws Exception {
        String body = "{\"barcode\":\"" + firstBarcode() + "\",\"clientScanId\":\"dup-1\"}";
        mvc.perform(post("/api/scanner/broadcast").header("Authorization", tokenFor("cashier"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.found").value(true));
        mvc.perform(post("/api/scanner/broadcast").header("Authorization", tokenFor("cashier"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.duplicate").value(true));

        verify(messagingTemplate, times(1)).convertAndSendToUser(eq("cashier"), eq(ScannerService.USER_QUEUE), any(Object.class));
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("Action khác ADD_TO_CART bị từ chối ở endpoint bắn mã")
    void broadcastRoleAndActionChecked() throws Exception {
        mvc.perform(post("/api/scanner/broadcast").header("Authorization", tokenFor("cashier"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"barcode\":\"123\",\"action\":\"LOOKUP_ONLY\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("Đồng bộ offline gửi một tin BATCH theo thứ tự quét; phần tử thiếu barcode trả 400")
    @SuppressWarnings("unchecked")
    void syncBatchSendsOneOrderedBatch() throws Exception {
        String barcode = firstBarcode();
        String body = "{\"scans\":["
                + "{\"barcode\":\"" + barcode + "\",\"clientScanId\":\"b-2\",\"timestamp\":\"2026-10-20T10:05:00Z\"},"
                + "{\"barcode\":\"999\",\"clientScanId\":\"b-1\",\"timestamp\":\"2026-10-20T10:00:00Z\"}]}";
        mvc.perform(post("/api/scanner/sync-batch").header("Authorization", tokenFor("cashier"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalSent").value(2));

        var captor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate, times(1)).convertAndSendToUser(eq("cashier"), eq(ScannerService.USER_QUEUE), captor.capture());
        Map<String, Object> payload = (Map<String, Object>) captor.getValue();
        assertThat(payload.get("type")).isEqualTo("BATCH");
        List<Map<String, Object>> scans = (List<Map<String, Object>>) payload.get("scans");
        assertThat(scans).extracting(s -> s.get("clientScanId")).containsExactly("b-1", "b-2");

        mvc.perform(post("/api/scanner/sync-batch").header("Authorization", tokenFor("cashier"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"scans\":[{\"clientScanId\":\"x\"}]}"))
                .andExpect(status().isBadRequest());
    }
}
