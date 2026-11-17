package com.pos.purchase;

import com.pos.common.BusinessException;
import com.pos.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;

    public List<Supplier> getAllSuppliers() {
        return supplierRepository.findAll();
    }

    public Supplier getSupplierById(Long id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> new BusinessException("SUPPLIER_NOT_FOUND", "Không tìm thấy nhà cung cấp với ID: " + id));
    }

    @Transactional
    public Supplier createSupplier(Supplier supplier) {
        supplier.setId(null);
        validate(supplier);
        supplier.setCategories(normalize(supplier.getCategories()));
        return supplierRepository.save(supplier);
    }

    @Transactional
    public Supplier updateSupplier(Long id, Supplier req) {
        Supplier s = getSupplierById(id);
        validate(req);
        s.setName(req.getName().trim());
        s.setPhone(req.getPhone());
        s.setAddress(req.getAddress());
        s.getCategories().clear();
        s.getCategories().addAll(normalize(req.getCategories()));
        return supplierRepository.save(s);
    }

    /** Danh mục để chọn khi gán cho NCC: danh mục đang có trên sản phẩm cộng danh mục NCC đã gán */
    public List<String> getCategoryOptions() {
        Set<String> all = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        productRepository.findDistinctCategories().forEach(c -> { if (StringUtils.hasText(c)) all.add(c.trim()); });
        supplierRepository.findAll().forEach(s -> all.addAll(s.getCategories()));
        return List.copyOf(all);
    }

    private static void validate(Supplier s) {
        if (s == null || !StringUtils.hasText(s.getName())) {
            throw new BusinessException("SUPPLIER_NAME_REQUIRED", "Tên nhà cung cấp không được để trống");
        }
        s.setName(s.getName().trim());
    }

    /** Bỏ khoảng trắng, bỏ trùng (không phân biệt hoa thường), cắt độ dài cho vừa cột */
    static Set<String> normalize(Collection<String> categories) {
        Set<String> seen = new java.util.HashSet<>();
        Set<String> out = new LinkedHashSet<>();
        if (categories == null) return out;
        for (String c : categories) {
            if (!StringUtils.hasText(c)) continue;
            String v = c.trim();
            if (v.length() > 100) v = v.substring(0, 100);
            // MySQL utf8mb4_unicode_ci coi "Bánh" và "banh" là một: bỏ trùng theo cùng quy tắc để không vỡ khóa chính
            String key = java.text.Normalizer.normalize(v.toLowerCase(java.util.Locale.ROOT), java.text.Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "").replace('đ', 'd');
            if (seen.add(key)) out.add(v);
        }
        return out;
    }
}
