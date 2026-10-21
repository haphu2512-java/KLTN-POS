package com.pos.scanner;

import com.pos.support.TestAuth;
import com.pos.security.CustomUserDetailsService;
import com.pos.security.JwtTokenProvider;
import com.jayway.jsonpath.JsonPath;
import com.pos.branch.Branch;
import com.pos.branch.BranchRepository;
import com.pos.user.Role;
import com.pos.user.User;
import com.pos.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kết nối STOMP thật: mã quét từ điện thoại chỉ tới quầy POS đã ghép cặp, không tới quầy khác.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:scannerwsdb;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
class ScannerWebSocketDeliveryTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String tokenFor(String username) {
        User user = userRepository.findByUsername(username).orElseThrow();
        return "Bearer " + tokenProvider.generateToken(userDetailsService.loadUserByUsername(username),
                TestAuth.branchOf(user), user.getRole().name(), user.getFullName());
    }

    private StompSession connect(String username) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new MappingJackson2MessageConverter());
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", tokenFor(username));
        return client.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(),
                connectHeaders, new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS);
    }

    private String postJson(String path, String username, String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", tokenFor(username));
        return new RestTemplate().postForEntity("http://localhost:" + port + path,
                new HttpEntity<>(json, headers), String.class).getBody();
    }

    private String registerTerminal(String posUser, String name) {
        return JsonPath.read(postJson("/api/scanner/terminals", posUser, "{\"name\":\"" + name + "\"}"), "$.data.id");
    }

    private String pair(String terminalId, String posUser, String phoneUser) {
        String code = JsonPath.read(postJson("/api/scanner/terminals/" + terminalId + "/pairing-code", posUser, "{}"), "$.data.code");
        return JsonPath.read(postJson("/api/scanner/pair", phoneUser, "{\"code\":\"" + code + "\"}"), "$.data.pairingId");
    }

    private BlockingQueue<Map<String, Object>> subscribe(String username, String destination) throws Exception {
        StompSession session = connect(username);
        BlockingQueue<Map<String, Object>> received = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add((Map<String, Object>) payload);
            }
        });
        Thread.sleep(300); // chờ SUBSCRIBE được đăng ký
        return received;
    }

    @Test
    @DisplayName("Mã quét chỉ tới quầy đã ghép cặp, quầy khác không nhận")
    void scanReachesOnlyPairedTerminal() throws Exception {
        String quay1 = registerTerminal("admin", "Quầy 1");
        String quay2 = registerTerminal("admin", "Quầy 2");
        // Máy POS quầy 1 đăng nhập "admin", quầy 2 đăng nhập "cashier"
        BlockingQueue<Map<String, Object>> pos1 = subscribe("admin", TerminalAccess.topicOf(quay1));
        BlockingQueue<Map<String, Object>> pos2 = subscribe("cashier", TerminalAccess.topicOf(quay2));

        // Điện thoại đăng nhập "cashier" ghép với quầy 1
        String pairingId = pair(quay1, "admin", "cashier");
        Map<String, Object> paired = pos1.poll(5, TimeUnit.SECONDS);
        assertThat(paired).isNotNull();
        assertThat(paired.get("type")).isEqualTo("PAIRED");

        postJson("/api/scanner/broadcast", "cashier",
                "{\"barcode\":\"ws-test-123\",\"clientScanId\":\"ws-1\",\"pairingId\":\"" + pairingId + "\"}");

        Map<String, Object> msg = pos1.poll(5, TimeUnit.SECONDS);
        assertThat(msg).isNotNull();
        assertThat(msg.get("type")).isEqualTo("SCAN");
        assertThat(msg.get("barcode")).isEqualTo("ws-test-123");
        assertThat(pos2.poll(1, TimeUnit.SECONDS)).isNull();
    }

    @Test
    @DisplayName("Không nghe trộm được quầy khác chi nhánh hay bằng ký tự đại diện, không gửi thẳng được vào kênh quầy")
    void clientCannotSnoopOrInject() throws Exception {
        Long otherBranch = branchRepository.findAll().stream()
                .map(Branch::getId)
                .filter(id -> !id.equals(TestAuth.branchOf(userRepository.findByUsername("cashier").orElseThrow())))
                .findFirst().orElseThrow();
        if (userRepository.findByUsername("cashier2").isEmpty()) {
            userRepository.save(User.builder().username("cashier2").password(passwordEncoder.encode("123456"))
                    .fullName("Thu ngân chi nhánh 2").role(Role.CASHIER).branchIds(TestAuth.branches(otherBranch)).build());
        }

        String quay = registerTerminal("cashier", "Quầy 1");
        BlockingQueue<Map<String, Object>> pos = subscribe("cashier", TerminalAccess.topicOf(quay));
        BlockingQueue<Map<String, Object>> otherBranchSnoop = subscribe("cashier2", TerminalAccess.topicOf(quay));
        BlockingQueue<Map<String, Object>> wildcardSnoop = subscribe("admin", "/topic/terminals/**");
        BlockingQueue<Map<String, Object>> inventoryFeed = subscribe("cashier", "/topic/inventory-updates");

        connect("admin").send(TerminalAccess.topicOf(quay), Map.of("type", "SCAN", "barcode", "INJECTED"));
        connect("admin").send("/topic/inventory-updates", Map.of("branchId", 1, "productId", 1));
        assertThat(pos.poll(2, TimeUnit.SECONDS)).isNull();
        assertThat(inventoryFeed.poll(1, TimeUnit.SECONDS)).isNull();

        String pairingId = pair(quay, "cashier", "cashier");
        postJson("/api/scanner/broadcast", "cashier",
                "{\"barcode\":\"ws-test-456\",\"clientScanId\":\"ws-2\",\"pairingId\":\"" + pairingId + "\"}");
        assertThat(pos.poll(5, TimeUnit.SECONDS)).isNotNull();
        assertThat(otherBranchSnoop.poll(1, TimeUnit.SECONDS)).isNull();
        assertThat(wildcardSnoop.poll(1, TimeUnit.SECONDS)).isNull();
    }
}
