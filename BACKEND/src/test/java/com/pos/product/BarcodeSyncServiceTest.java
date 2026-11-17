package com.pos.product;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.sql.init.mode=never"
})
@Import(BarcodeSyncService.class)
class BarcodeSyncServiceTest {

    @Autowired
    private BarcodeSyncService syncService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private EntityManager em;

    private Product product(String barcode) {
        return productRepository.save(Product.builder().barcode(barcode).name("SP " + barcode)
                .salePrice(BigDecimal.TEN).build());
    }

    @Test
    @DisplayName("Đổi mã cũ sang mã nội bộ theo ID, giữ mã thật, chạy lại không đổi gì thêm")
    void syncsOnlyLegacyCodes() {
        Product legacy = product("1053086000170");
        Product real = product("8934588012228");

        Map<String, Object> report = syncService.syncLegacyBarcodes();
        em.clear();

        assertEquals(1, report.get("updated"));
        assertEquals(BarcodeRules.storeBarcode(legacy.getId()),
                productRepository.findById(legacy.getId()).orElseThrow().getBarcode());
        assertEquals("8934588012228", productRepository.findById(real.getId()).orElseThrow().getBarcode());
        assertEquals(0, syncService.syncLegacyBarcodes().get("updated"));
    }
}
