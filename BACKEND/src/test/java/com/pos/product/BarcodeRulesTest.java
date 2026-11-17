package com.pos.product;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BarcodeRulesTest {

    @Test
    @DisplayName("Mã nội bộ = 20 + ID đủ 10 số + số kiểm tra EAN-13")
    void storeBarcodeFromId() {
        assertEquals("2000000000039", BarcodeRules.storeBarcode(3));
        assertEquals("2000000019499", BarcodeRules.storeBarcode(1949));
        assertTrue(BarcodeRules.isValidGtin(BarcodeRules.storeBarcode(123456789L)));
        assertFalse(BarcodeRules.needsRegeneration(BarcodeRules.storeBarcode(42)));
    }

    @Test
    @DisplayName("Giữ mã EAN/UPC/ITF-14 thật in trên bao bì")
    void keepsRealGtins() {
        assertFalse(BarcodeRules.needsRegeneration("8934588012228")); // Pepsi lon
        assertFalse(BarcodeRules.needsRegeneration("69025310"));      // Snickers EAN-8
        assertFalse(BarcodeRules.needsRegeneration("070074118659"));  // Ensure UPC-A
        assertFalse(BarcodeRules.needsRegeneration("58934588012223")); // thùng Pepsi ITF-14
    }

    @Test
    @DisplayName("Sinh lại mã nội bộ BHX, mã giả của script cào, mã sai số kiểm tra")
    void regeneratesLegacyCodes() {
        assertTrue(BarcodeRules.needsRegeneration("1053086000170")); // BHX, sai số kiểm tra
        assertTrue(BarcodeRules.needsRegeneration("1034170000453")); // BHX, số kiểm tra trùng hợp đúng
        assertTrue(BarcodeRules.needsRegeneration("2701122063800")); // mã cân của cửa hàng khác
        assertTrue(BarcodeRules.needsRegeneration("8931000000009")); // script cào tự sinh
        assertTrue(BarcodeRules.needsRegeneration("8934567890123")); // mã mẫu sai số kiểm tra
        assertTrue(BarcodeRules.needsRegeneration("ABC-1"));
        assertTrue(BarcodeRules.needsRegeneration(null));
    }
}
