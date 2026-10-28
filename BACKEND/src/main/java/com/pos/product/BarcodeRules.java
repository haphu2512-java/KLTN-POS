package com.pos.product;

import java.util.Set;

/**
 * Quy tắc mã vạch sản phẩm.
 *
 * <p>Mã do cửa hàng tự sinh là EAN-13 dải GS1 "dùng nội bộ" (đầu 20): {@code 20} + ID sản phẩm đủ 10 số
 * + số kiểm tra. Ví dụ sản phẩm #3 → {@code 2000000000039}. Mã suy ra từ ID nên không trùng, sinh lại
 * bao nhiêu lần cũng ra cùng một mã, và in ra tem thì camera đọc được như mọi mã EAN-13.
 *
 * <p>Mã EAN/UPC thật in trên bao bì (số kiểm tra đúng) được giữ nguyên. Các mã sau bị coi là mã cũ
 * không quét được và cần sinh lại: sai định dạng hoặc sai số kiểm tra; mã nội bộ Bách Hóa Xanh lấy từ
 * dữ liệu cào về; mã giả {@code 8931000000xxx} do script cào tự sinh; mã đầu 21–29 của cửa hàng khác.
 */
public final class BarcodeRules {

    public static final String STORE_PREFIX = "20";

    /** Đầu 3 số của mã nội bộ Bách Hóa Xanh (productCode) có trong dữ liệu import */
    private static final Set<String> BHX_INTERNAL_PREFIXES = Set.of(
            "101", "103", "105", "106", "123", "125", "135", "149", "925", "989", "990", "991", "992", "993");

    private static final String SCRAPER_FAKE_PREFIX = "8931000000";

    private BarcodeRules() {
    }

    /** Mã EAN-13 nội bộ của cửa hàng cho sản phẩm có ID này */
    public static String storeBarcode(long productId) {
        if (productId < 0 || productId > 9_999_999_999L) {
            throw new IllegalArgumentException("ID sản phẩm ngoài phạm vi mã nội bộ: " + productId);
        }
        String body = STORE_PREFIX + String.format("%010d", productId);
        return body + checkDigit(body);
    }

    /** Mã GTIN (EAN-8, UPC-A, EAN-13, ITF-14) đúng độ dài và đúng số kiểm tra */
    public static boolean isValidGtin(String code) {
        if (code == null || !code.matches("\\d{8}|\\d{12,14}")) {
            return false;
        }
        String body = code.substring(0, code.length() - 1);
        return checkDigit(body) == code.charAt(code.length() - 1) - '0';
    }

    /** true nếu mã không quét được trên bao bì thật hoặc tem của cửa hàng, cần thay bằng {@link #storeBarcode} */
    public static boolean needsRegeneration(String code) {
        if (code == null || !isValidGtin(code.trim())) {
            return true;
        }
        String c = code.trim();
        if (c.length() != 13) {
            return false;
        }
        if (c.startsWith("2") && !c.startsWith(STORE_PREFIX)) {
            return true;
        }
        // Mã BHX có dạng 7 số + "00" + 4 số (vd. 1034170000453), khác mã EAN thật cùng đầu số
        boolean bhxInternal = BHX_INTERNAL_PREFIXES.contains(c.substring(0, 3)) && c.startsWith("00", 7);
        return bhxInternal || c.startsWith(SCRAPER_FAKE_PREFIX);
    }

    /** Số kiểm tra GS1 cho phần thân mã (không gồm số kiểm tra) */
    static int checkDigit(String body) {
        int sum = 0;
        for (int i = 0; i < body.length(); i++) {
            int digit = body.charAt(body.length() - 1 - i) - '0';
            sum += (i % 2 == 0) ? digit * 3 : digit;
        }
        return (10 - sum % 10) % 10;
    }
}
