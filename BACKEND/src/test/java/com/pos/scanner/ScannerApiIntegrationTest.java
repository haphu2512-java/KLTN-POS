package com.pos.scanner;

import com.pos.support.TestAuth;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
        return "Bearer " + tokenProvider.generateToken(details, TestAuth.branchOf(user), user.getRole().name(), user.getFullName());
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

    /** Máy POS (đăng nhập "posUser") đăng ký quầy; trả id quầy */
    private String registerTerminal(String posUser) throws Exception {
        String body = mvc.perform(post("/api/scanner/terminals").header("Authorization", tokenFor(posUser))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Quầy 1\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.data.id");
    }

    /** Máy POS tạo mã QR, điện thoại (đăng nhập "phoneUser") quét để ghép cặp; trả pairingId */
    private String pair(String terminalId, String posUser, String phoneUser) throws Exception {
        String codeBody = mvc.perform(post("/api/scanner/terminals/" + terminalId + "/pairing-code")
                        .header("Authorization", tokenFor(posUser)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String code = com.jayway.jsonpath.JsonPath.read(codeBody, "$.data.code");
        String pairBody = mvc.perform(post("/api/scanner/pair").header("Authorization", tokenFor(phoneUser))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.terminalName").value("Quầy 1"))
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(pairBody, "$.data.pairingId");
    }

    @Test
    @DisplayName("Chưa ghép cặp: bắn mã trả 409 NOT_PAIRED và không gửi gì")
    void broadcastRequiresPairing() throws Exception {
        mvc.perform(post("/api/scanner/broadcast").header("Authorization", tokenFor("cashier"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"barcode\":\"123\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_PAIRED"));
        mvc.perform(post("/api/scanner/broadcast").header("Authorization", tokenFor("cashier"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"barcode\":\"123\",\"pairingId\":\"bogus\"}"))
                .andExpect(status().isConflict());
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("Mã ghép cặp chỉ dùng một lần; ghép cặp gắn với tài khoản đã quét QR")
    void pairingCodeSingleUseAndBoundToAccount() throws Exception {
        String terminalId = registerTerminal("admin");
        String codeBody = mvc.perform(post("/api/scanner/terminals/" + terminalId + "/pairing-code")
                        .header("Authorization", tokenFor("admin")))
                .andReturn().getResponse().getContentAsString();
        String code = com.jayway.jsonpath.JsonPath.read(codeBody, "$.data.code");
        String pairBody = mvc.perform(post("/api/scanner/pair").header("Authorization", tokenFor("cashier"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/scanner/pair").header("Authorization", tokenFor("cashier"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAIRING_CODE_INVALID"));

        // Tài khoản khác dùng lại pairingId của "cashier" thì không được
        String pairingId = com.jayway.jsonpath.JsonPath.read(pairBody, "$.data.pairingId");
        mvc.perform(post("/api/scanner/broadcast").header("Authorization", tokenFor("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"barcode\":\"123\",\"pairingId\":\"" + pairingId + "\"}"))
                .andExpect(status().isConflict());

        // Máy POS gỡ điện thoại: ghép cặp hết hiệu lực
        mvc.perform(delete("/api/scanner/terminals/" + terminalId + "/pairings/" + pairingId)
                        .header("Authorization", tokenFor("admin")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/scanner/pairings/" + pairingId).header("Authorization", tokenFor("cashier")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Mã quét đi tới quầy đã ghép cặp (không theo tài khoản), gửi trùng clientScanId bị bỏ qua")
    void broadcastGoesToPairedTerminal() throws Exception {
        // Máy POS đăng nhập "admin", điện thoại đăng nhập "cashier": khác tài khoản vẫn nhận được
        String terminalId = registerTerminal("admin");
        String pairingId = pair(terminalId, "admin", "cashier");
        String body = "{\"barcode\":\"" + firstBarcode() + "\",\"clientScanId\":\"dup-1\",\"pairingId\":\"" + pairingId + "\"}";
        mvc.perform(post("/api/scanner/broadcast").header("Authorization", tokenFor("cashier"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.found").value(true))
                .andExpect(jsonPath("$.data.terminalName").value("Quầy 1"));
        mvc.perform(post("/api/scanner/broadcast").header("Authorization", tokenFor("cashier"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.duplicate").value(true));

        var captor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate, times(2)).convertAndSend(eq(TerminalAccess.topicOf(terminalId)), captor.capture());
        assertThat(captor.getAllValues()).extracting(p -> (Object) ((Map<?, ?>) p).get("type")).containsExactly("PAIRED", "SCAN");
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any(Object.class));
    }

    /** Giả lập request tới từ thiết bị có IP này */
    private static RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    @Test
    @DisplayName("Điện thoại khác mạng với máy POS: không ghép cặp, không bắn mã, không đồng bộ được (403 NOT_SAME_NETWORK)")
    void phoneMustBeOnSameNetworkAsPos() throws Exception {
        String posIp = "192.168.77.10";
        String terminalId = JsonPath.read(mvc.perform(post("/api/scanner/terminals").with(from(posIp))
                        .header("Authorization", tokenFor("admin"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Quầy LAN\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.data.id");

        // Ghép cặp từ 4G (IP công cộng): bị chặn, mã đã dùng không dùng lại được
        String code = JsonPath.read(mvc.perform(post("/api/scanner/terminals/" + terminalId + "/pairing-code")
                        .with(from(posIp)).header("Authorization", tokenFor("admin")))
                .andReturn().getResponse().getContentAsString(), "$.data.code");
        mvc.perform(post("/api/scanner/pair").with(from("113.161.5.6")).header("Authorization", tokenFor("cashier"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_SAME_NETWORK"));

        // Cùng Wi-Fi cửa hàng (cùng dải 192.168.77.x): ghép được
        code = JsonPath.read(mvc.perform(post("/api/scanner/terminals/" + terminalId + "/pairing-code")
                        .with(from(posIp)).header("Authorization", tokenFor("admin")))
                .andReturn().getResponse().getContentAsString(), "$.data.code");
        String pairingId = JsonPath.read(mvc.perform(post("/api/scanner/pair").with(from("192.168.77.23"))
                        .header("Authorization", tokenFor("cashier"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.data.pairingId");
        verify(messagingTemplate, times(1)).convertAndSend(eq(TerminalAccess.topicOf(terminalId)), any(Object.class));

        String scan = "{\"barcode\":\"123\",\"clientScanId\":\"net-1\",\"pairingId\":\"" + pairingId + "\"}";
        // Mang điện thoại đã ghép về nhà (mạng khác, kể cả mạng nội bộ khác dải) hoặc dùng 4G: bị chặn
        for (String elsewhere : List.of("113.161.5.6", "192.168.1.23", "10.0.0.5")) {
            mvc.perform(post("/api/scanner/broadcast").with(from(elsewhere)).header("Authorization", tokenFor("cashier"))
                            .contentType(MediaType.APPLICATION_JSON).content(scan))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("NOT_SAME_NETWORK"));
        }
        mvc.perform(post("/api/scanner/sync-batch").with(from("113.161.5.6")).header("Authorization", tokenFor("cashier"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pairingId\":\"" + pairingId + "\",\"scans\":[{\"barcode\":\"123\",\"clientScanId\":\"net-2\"}]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_SAME_NETWORK"));
        verify(messagingTemplate, times(1)).convertAndSend(eq(TerminalAccess.topicOf(terminalId)), any(Object.class));

        // Quay lại Wi-Fi cửa hàng: bắn mã bình thường
        mvc.perform(post("/api/scanner/broadcast").with(from("192.168.77.23")).header("Authorization", tokenFor("cashier"))
                        .contentType(MediaType.APPLICATION_JSON).content(scan))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.duplicate").doesNotExist());
        verify(messagingTemplate, times(2)).convertAndSend(eq(TerminalAccess.topicOf(terminalId)), any(Object.class));
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
        String terminalId = registerTerminal("cashier");
        String pairingId = pair(terminalId, "cashier", "cashier");
        String barcode = firstBarcode();
        String body = "{\"pairingId\":\"" + pairingId + "\",\"scans\":["
                + "{\"barcode\":\"" + barcode + "\",\"clientScanId\":\"b-2\",\"timestamp\":\"2026-10-20T10:05:00Z\"},"
                + "{\"barcode\":\"999\",\"clientScanId\":\"b-1\",\"timestamp\":\"2026-10-20T10:00:00Z\"}]}";
        mvc.perform(post("/api/scanner/sync-batch").header("Authorization", tokenFor("cashier"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalSent").value(2));

        var captor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate, times(2)).convertAndSend(eq(TerminalAccess.topicOf(terminalId)), captor.capture());
        Map<String, Object> payload = (Map<String, Object>) captor.getAllValues().get(1);
        assertThat(payload.get("type")).isEqualTo("BATCH");
        List<Map<String, Object>> scans = (List<Map<String, Object>>) payload.get("scans");
        assertThat(scans).extracting(s -> s.get("clientScanId")).containsExactly("b-1", "b-2");

        mvc.perform(post("/api/scanner/sync-batch").header("Authorization", tokenFor("cashier"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pairingId\":\"" + pairingId + "\",\"scans\":[{\"clientScanId\":\"x\"}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Gán mã vạch vừa quét cho sản phẩm: tra được bằng mã mới, mã của sản phẩm khác trả 409, thu ngân không được gán")
    void assignScannedBarcode() throws Exception {
        String catalog = mvc.perform(get("/api/scanner/products/cache").header("Authorization", tokenFor("admin")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Integer id = com.jayway.jsonpath.JsonPath.read(catalog, "$.data[1].id");
        String name = com.jayway.jsonpath.JsonPath.read(catalog, "$.data[1].name");
        String otherBarcode = com.jayway.jsonpath.JsonPath.read(catalog, "$.data[2].barcode");
        String newBarcode = "8934588012228";

        mvc.perform(get("/api/scanner/products/search").param("q", name).header("Authorization", tokenFor("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(id))
                .andExpect(jsonPath("$.data[0].costPrice").doesNotExist());

        mvc.perform(put("/api/scanner/products/" + id + "/barcode").header("Authorization", tokenFor("cashier"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"barcode\":\"" + newBarcode + "\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/scanner/products/" + id + "/barcode").header("Authorization", tokenFor("admin"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"barcode\":\" " + newBarcode + " \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.barcode").value(newBarcode));

        mvc.perform(get("/api/scanner/lookup").param("barcode", newBarcode).header("Authorization", tokenFor("cashier")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id));

        mvc.perform(put("/api/scanner/products/" + id + "/barcode").header("Authorization", tokenFor("admin"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"barcode\":\"" + otherBarcode + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BARCODE_ALREADY_EXISTS"));
    }
}
