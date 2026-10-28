package com.pos.product;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Đồng bộ mã vạch trong database theo {@link BarcodeRules}: sản phẩm mang mã cũ không quét được được
 * gán mã nội bộ của cửa hàng. Chỉ đổi cột barcode (không xóa dòng nào) nên hóa đơn, tồn kho vẫn giữ nguyên.
 * Chạy lại nhiều lần không đổi thêm gì.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BarcodeSyncService {

    private final ProductRepository productRepository;

    @Transactional
    public Map<String, Object> syncLegacyBarcodes() {
        List<Product> products = productRepository.findAll();
        List<Map<String, Object>> changes = new ArrayList<>();
        List<Map<String, Object>> skipped = new ArrayList<>();

        for (Product p : products) {
            if (!BarcodeRules.needsRegeneration(p.getBarcode())) {
                continue;
            }
            String newCode = BarcodeRules.storeBarcode(p.getId());
            var owner = productRepository.findByBarcode(newCode);
            if (owner.isPresent() && !owner.get().getId().equals(p.getId())) {
                log.warn("Không đổi mã sản phẩm #{}: mã {} đang thuộc sản phẩm #{}", p.getId(), newCode, owner.get().getId());
                skipped.add(change(p, newCode));
                continue;
            }
            changes.add(change(p, newCode));
            p.setBarcode(newCode);
            productRepository.saveAndFlush(p);
        }

        if (!changes.isEmpty()) {
            log.info("Đã đồng bộ mã vạch cho {}/{} sản phẩm theo quy tắc mã nội bộ", changes.size(), products.size());
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("checked", products.size());
        report.put("updated", changes.size());
        report.put("skipped", skipped.size());
        report.put("changes", changes);
        report.put("skippedItems", skipped);
        return report;
    }

    private static Map<String, Object> change(Product p, String newCode) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("productId", p.getId());
        m.put("name", p.getName());
        m.put("oldBarcode", p.getBarcode());
        m.put("newBarcode", newCode);
        return m;
    }
}
