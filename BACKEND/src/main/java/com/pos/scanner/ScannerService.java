package com.pos.scanner;

import com.pos.inventory.Inventory;
import com.pos.inventory.InventoryRepository;
import com.pos.product.Product;
import com.pos.product.ProductRepository;
import com.pos.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Điện thoại đăng nhập cùng tài khoản với máy POS; mã quét chỉ được gửi tới các phiên WebSocket
 * của đúng tài khoản đó (/user/queue/barcode-scans), không phát cho mọi quầy.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScannerService {

    public static final String USER_QUEUE = "/queue/barcode-scans";
    private static final int RECENT_SCAN_IDS_LIMIT = 5000;

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /** clientScanId đã xử lý gần đây (theo tài khoản), để bỏ qua khi điện thoại gửi lại do timeout */
    private final Set<String> recentScanIds = Collections.newSetFromMap(new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > RECENT_SCAN_IDS_LIMIT;
        }
    });

    @Transactional(readOnly = true)
    public Optional<ScannerProductDto> lookup(String barcode, User user) {
        return productRepository.findByBarcode(barcode.trim())
                .map(p -> ScannerProductDto.from(p, stockOf(p, user)));
    }

    @Transactional(readOnly = true)
    public List<ScannerProductDto> catalog(User user) {
        Map<Long, Integer> stockByProduct = user.getBranchId() == null ? Map.of()
                : inventoryRepository.findByBranchId(user.getBranchId()).stream()
                .collect(Collectors.toMap(i -> i.getProduct().getId(), Inventory::getQuantity, (a, b) -> a));
        return productRepository.findByActiveTrue().stream()
                .map(p -> ScannerProductDto.from(p, stockByProduct.get(p.getId())))
                .toList();
    }

    /** Bắn một mã vào giỏ hàng POS của chính tài khoản đang quét */
    @Transactional(readOnly = true)
    public Map<String, Object> sendToPos(BarcodeScanRequest req, User user) {
        Map<String, Object> item = toScanItem(req, user);
        if (markSeen(user, (String) item.get("clientScanId"))) {
            Map<String, Object> payload = new HashMap<>(item);
            payload.put("type", "SCAN");
            send(user, payload);
        } else {
            item.put("duplicate", true);
        }
        return item;
    }

    /**
     * Gửi các mã quét lúc offline thành MỘT tin dạng BATCH, theo thứ tự thời gian quét.
     * POS sẽ hỏi thu ngân trước khi thêm vào giỏ, thay vì tự thêm hàng loạt.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> syncBatch(List<BarcodeScanRequest> scans, User user) {
        List<BarcodeScanRequest> ordered = new ArrayList<>(scans);
        ordered.sort(Comparator.comparing(s -> Objects.toString(s.getTimestamp(), "")));

        List<Map<String, Object>> results = new ArrayList<>();
        List<Map<String, Object>> fresh = new ArrayList<>();
        for (BarcodeScanRequest scan : ordered) {
            Map<String, Object> item = toScanItem(scan, user);
            boolean isNew = markSeen(user, (String) item.get("clientScanId"));
            item.put("duplicate", !isNew);
            results.add(item);
            if (isNew) {
                fresh.add(item);
            }
        }

        if (!fresh.isEmpty()) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "BATCH");
            payload.put("action", "ADD_TO_CART");
            payload.put("scans", fresh);
            send(user, payload);
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("totalSynced", results.size());
        resp.put("totalSent", fresh.size());
        resp.put("results", results);
        return resp;
    }

    private Map<String, Object> toScanItem(BarcodeScanRequest req, User user) {
        String cleanBarcode = req.getBarcode().trim();
        Optional<Product> productOpt = productRepository.findByBarcode(cleanBarcode);

        Map<String, Object> item = new HashMap<>();
        item.put("barcode", cleanBarcode);
        item.put("branchId", user.getBranchId());
        item.put("deviceName", req.getDeviceName() != null ? req.getDeviceName() : "Điện thoại quét mã");
        item.put("clientScanId", req.getClientScanId() != null ? req.getClientScanId() : UUID.randomUUID().toString());
        item.put("scannedAt", req.getTimestamp());
        item.put("action", "ADD_TO_CART");
        item.put("timestamp", System.currentTimeMillis());
        item.put("found", productOpt.isPresent());
        item.put("product", productOpt.map(p -> ScannerProductDto.from(p, stockOf(p, user))).orElse(null));
        return item;
    }

    private Integer stockOf(Product p, User user) {
        if (user.getBranchId() == null) {
            return null;
        }
        return inventoryRepository.findByProductIdAndBranchId(p.getId(), user.getBranchId())
                .map(Inventory::getQuantity)
                .orElse(null);
    }

    /** @return true nếu đây là lần đầu thấy clientScanId này của tài khoản */
    private boolean markSeen(User user, String clientScanId) {
        synchronized (recentScanIds) {
            return recentScanIds.add(user.getUsername() + ":" + clientScanId);
        }
    }

    private void send(User user, Map<String, Object> payload) {
        try {
            messagingTemplate.convertAndSendToUser(user.getUsername(), USER_QUEUE, payload);
        } catch (Exception e) {
            log.warn("Không gửi được mã quét tới POS của '{}': {}", user.getUsername(), e.getMessage());
        }
    }
}
