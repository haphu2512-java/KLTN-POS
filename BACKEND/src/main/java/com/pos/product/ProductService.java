package com.pos.product;

import java.util.stream.Stream;
import java.util.stream.Collectors;
import java.util.Objects;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Autowired;
import com.pos.audit.AuditService;
import com.pos.audit.AuditAction;
import com.pos.common.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    /** Nhật ký thao tác; để trống trong unit test dựng service bằng tay */
    @Autowired(required = false)
    private AuditService auditService;

    private void audit(AuditAction action, String entityType, Object entityId, Long branchId, String summary) {
        if (auditService != null) auditService.log(action, entityType, entityId, branchId, summary);
    }

    public List<Product> getAllProducts(String search, String category) {
        if (StringUtils.hasText(search)) {
            return productRepository.searchProducts(search.trim());
        }
        if (StringUtils.hasText(category) && !"ALL".equalsIgnoreCase(category)) {
            return productRepository.findByCategoryAndActiveTrue(category.trim());
        }
        return productRepository.findByActiveTrue();
    }

    public Product getProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Không tìm thấy sản phẩm với ID: " + id));
    }

    public Product getProductByBarcode(String barcode) {
        return productRepository.findByBarcode(barcode.trim())
                .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Không tìm thấy sản phẩm có mã vạch: " + barcode));
    }

    @Transactional
    public Product createProduct(ProductRequest req) {
        boolean generateBarcode = !StringUtils.hasText(req.getBarcode());
        if (!generateBarcode && productRepository.existsByBarcode(req.getBarcode().trim())) {
            throw new BusinessException("BARCODE_ALREADY_EXISTS", "Mã vạch đã tồn tại trong hệ thống: " + req.getBarcode());
        }

        Product product = Product.builder()
                // Bỏ trống mã vạch: lưu mã tạm để lấy ID, rồi sinh mã nội bộ theo BarcodeRules
                .barcode(generateBarcode ? "TMP-" + UUID.randomUUID() : req.getBarcode().trim())
                .name(req.getName().trim())
                .category(req.getCategory() != null ? req.getCategory().trim() : "Khác")
                .costPrice(req.getCostPrice() != null ? req.getCostPrice() : req.getSalePrice())
                .salePrice(req.getSalePrice())
                .unit(StringUtils.hasText(req.getUnit()) ? req.getUnit().trim() : "Cái")
                .imageUrl(req.getImageUrl() != null ? req.getImageUrl().trim() : null)
                .active(true)
                .build();

        Product saved = productRepository.saveAndFlush(product);
        if (generateBarcode) {
            saved.setBarcode(BarcodeRules.storeBarcode(saved.getId()));
            saved = productRepository.save(saved);
        }
        audit(AuditAction.PRODUCT_CREATE, "PRODUCT", saved.getId(), null,
                saved.getName() + " - giá bán " + AuditService.money(saved.getSalePrice()));
        return saved;
    }

    @Transactional
    public Product updateProduct(Long id, ProductRequest req) {
        Product existing = getProductById(id);
        BigDecimal oldSale = existing.getSalePrice();
        BigDecimal oldCost = existing.getCostPrice();

        // Bỏ trống mã vạch khi sửa thì giữ mã hiện tại
        if (StringUtils.hasText(req.getBarcode()) && !existing.getBarcode().equalsIgnoreCase(req.getBarcode().trim())) {
            if (productRepository.existsByBarcode(req.getBarcode().trim())) {
                throw new BusinessException("BARCODE_ALREADY_EXISTS", "Mã vạch mới đã tồn tại trên hệ thống");
            }
            existing.setBarcode(req.getBarcode().trim());
        }

        existing.setName(req.getName().trim());
        existing.setCategory(req.getCategory() != null ? req.getCategory().trim() : existing.getCategory());
        existing.setCostPrice(req.getCostPrice() != null ? req.getCostPrice() : existing.getCostPrice());
        existing.setSalePrice(req.getSalePrice());
        if (StringUtils.hasText(req.getUnit())) {
            existing.setUnit(req.getUnit().trim());
        }
        if (req.getImageUrl() != null) {
            existing.setImageUrl(req.getImageUrl().trim());
        }

        Product saved = productRepository.save(existing);
        String changes = Stream.of(
                        AuditService.diff("Giá bán", oldSale, saved.getSalePrice()),
                        AuditService.diff("Giá vốn", oldCost, saved.getCostPrice()))
                .filter(Objects::nonNull).collect(Collectors.joining("; "));
        if (!changes.isEmpty()) {
            audit(AuditAction.PRICE_CHANGE, "PRODUCT", saved.getId(), null, saved.getName() + ": " + changes);
        }
        return saved;
    }

    @Transactional
    public void deleteProduct(Long id) {
        Product existing = getProductById(id);
        existing.setActive(false);
        productRepository.save(existing);
        audit(AuditAction.PRODUCT_DELETE, "PRODUCT", id, null, "Ngừng bán: " + existing.getName());
    }
}
