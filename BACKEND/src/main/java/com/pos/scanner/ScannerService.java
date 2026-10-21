package com.pos.scanner;

import com.pos.common.BusinessException;
import com.pos.inventory.Inventory;
import com.pos.inventory.InventoryRepository;
import com.pos.product.Product;
import com.pos.product.ProductRepository;
import com.pos.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Mã quét từ điện thoại được gửi tới quầy POS mà điện thoại đã ghép cặp (/topic/terminals/{id}/scans),
 * không phụ thuộc tài khoản đang đăng nhập trên máy POS.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScannerService {

    private static final int RECENT_SCAN_IDS_LIMIT = 5000;
    private static final int SEARCH_LIMIT = 30;

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final TerminalService terminalService;

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
        Map<Long, Integer> stockByProduct = user.getCurrentBranchId() == null ? Map.of()
                : inventoryRepository.findByBranchId(user.getCurrentBranchId()).stream()
                .collect(Collectors.toMap(i -> i.getProduct().getId(), Inventory::getQuantity, (a, b) -> a));
        return productRepository.findByActiveTrue().stream()
                .map(p -> ScannerProductDto.from(p, stockByProduct.get(p.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ScannerProductDto> search(String query, User user) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            return List.of();
        }
        return productRepository.searchProducts(q).stream()
                .limit(SEARCH_LIMIT)
                .map(p -> ScannerProductDto.from(p, stockOf(p, user)))
                .toList();
    }

    /**
     * Thay mã vạch của sản phẩm bằng mã vừa quét từ bao bì.
     * Dùng khi dữ liệu nhập về mang mã nội bộ (vd. mã của Bách Hóa Xanh) thay vì mã in trên sản phẩm.
     */
    @Transactional
    public ScannerProductDto assignBarcode(Long productId, String barcode, User user) {
        String clean = barcode.trim();
        Product product = productRepository.findById(productId)
                .filter(p -> Boolean.TRUE.equals(p.getActive()))
                .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND",
                        "Không tìm thấy sản phẩm với ID: " + productId, HttpStatus.NOT_FOUND));

        Optional<Product> owner = productRepository.findByBarcode(clean);
        if (owner.isPresent() && !owner.get().getId().equals(product.getId())) {
            throw new BusinessException("BARCODE_ALREADY_EXISTS",
                    "Mã vạch " + clean + " đang thuộc sản phẩm \"" + owner.get().getName() + "\"", HttpStatus.CONFLICT);
        }

        log.info("{} gán mã vạch {} cho sản phẩm #{} (mã cũ {})", user.getUsername(), clean, product.getId(), product.getBarcode());
        product.setBarcode(clean);
        return ScannerProductDto.from(productRepository.save(product), stockOf(product, user));
    }

    /** Bắn một mã vào giỏ hàng của quầy POS mà điện thoại đã ghép cặp */
    @Transactional
    public Map<String, Object> sendToPos(BarcodeScanRequest req, User user) {
        PosTerminal terminal = terminalService.resolvePairing(req.getPairingId(), user);
        Map<String, Object> item = toScanItem(req, user);
        item.put("terminalName", terminal.getName());
        if (markSeen(user, (String) item.get("clientScanId"))) {
            Map<String, Object> payload = new HashMap<>(item);
            payload.put("type", "SCAN");
            terminalService.send(terminal.getId(), payload);
        } else {
            item.put("duplicate", true);
        }
        return item;
    }

    /**
     * Gửi các mã quét lúc offline thành MỘT tin dạng BATCH, theo thứ tự thời gian quét.
     * POS sẽ hỏi thu ngân trước khi thêm vào giỏ, thay vì tự thêm hàng loạt.
     */
    @Transactional
    public Map<String, Object> syncBatch(String pairingId, List<BarcodeScanRequest> scans, User user) {
        PosTerminal terminal = terminalService.resolvePairing(pairingId, user);
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
            terminalService.send(terminal.getId(), payload);
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("terminalName", terminal.getName());
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
        item.put("branchId", user.getCurrentBranchId());
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
        if (user.getCurrentBranchId() == null) {
            return null;
        }
        return inventoryRepository.findByProductIdAndBranchId(p.getId(), user.getCurrentBranchId())
                .map(Inventory::getQuantity)
                .orElse(null);
    }

    /** @return true nếu đây là lần đầu thấy clientScanId này của tài khoản */
    private boolean markSeen(User user, String clientScanId) {
        synchronized (recentScanIds) {
            return recentScanIds.add(user.getUsername() + ":" + clientScanId);
        }
    }

}
