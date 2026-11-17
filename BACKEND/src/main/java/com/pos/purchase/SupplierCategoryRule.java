package com.pos.purchase;

import com.pos.common.BusinessException;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Quy tắc chung cho mọi đường nhập hàng (phiếu nhập kho tự do, gửi đơn đặt hàng): NCC chỉ được nhập / đặt
 * những mặt hàng thuộc danh mục NCC đó bán (Supplier.categories). Một quy tắc ở một chỗ để hai màn hình
 * không lệch nhau và lịch sử nhập truy vết được đúng NCC.
 * <ul>
 *   <li>NCC chưa gán danh mục: không cho dùng, phải gán ở trang Đối tác trước.</li>
 *   <li>Mặt hàng chưa có danh mục: không kiểm tra được nên cho qua.</li>
 * </ul>
 */
public final class SupplierCategoryRule {

    private SupplierCategoryRule() {}

    /** Một mặt hàng cần kiểm tra: tên để báo lỗi, danh mục để so */
    public record Line(String productName, String category) {}

    /** Khóa so sánh danh mục, giống catKey ở frontend */
    public static String key(String category) {
        return category == null ? "" : category.trim().toLowerCase(Locale.ROOT);
    }

    /** Danh mục (tên hiển thị) trong các dòng mà NCC không bán, kèm tên mặt hàng thuộc danh mục đó */
    public static Map<String, List<String>> unsold(Supplier supplier, Collection<Line> lines) {
        Set<String> sold = supplier.getCategories().stream().map(SupplierCategoryRule::key).collect(Collectors.toSet());
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (Line l : lines) {
            if (!StringUtils.hasText(l.category())) continue;
            if (!sold.contains(key(l.category()))) {
                out.computeIfAbsent(l.category().trim(), k -> new ArrayList<>()).add(l.productName());
            }
        }
        return out;
    }

    /** Ném lỗi nếu NCC chưa có danh mục hoặc không bán danh mục của một mặt hàng nào đó */
    public static void check(Supplier supplier, Collection<Line> lines) {
        if (supplier.getCategories() == null || supplier.getCategories().isEmpty()) {
            throw new BusinessException("SUPPLIER_NO_CATEGORY",
                    supplier.getName() + " chưa được gán danh mục hàng cung cấp. Gán danh mục ở trang Đối tác trước khi nhập hàng.");
        }
        Map<String, List<String>> bad = unsold(supplier, lines);
        if (!bad.isEmpty()) {
            String detail = bad.entrySet().stream()
                    .map(e -> e.getKey() + " (" + String.join(", ", e.getValue()) + ")")
                    .collect(Collectors.joining("; "));
            throw new BusinessException("SUPPLIER_CATEGORY_MISMATCH",
                    supplier.getName() + " không bán danh mục: " + detail
                            + ". Chọn NCC khác hoặc tách các mặt hàng này sang phiếu riêng.",
                    org.springframework.http.HttpStatus.BAD_REQUEST, bad.keySet());
        }
    }
}
