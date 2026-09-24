package com.pos.product;

import com.pos.common.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

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
        if (productRepository.existsByBarcode(req.getBarcode().trim())) {
            throw new BusinessException("BARCODE_ALREADY_EXISTS", "Mã vạch đã tồn tại trong hệ thống: " + req.getBarcode());
        }

        Product product = Product.builder()
                .barcode(req.getBarcode().trim())
                .name(req.getName().trim())
                .category(req.getCategory() != null ? req.getCategory().trim() : "Khác")
                .costPrice(req.getCostPrice() != null ? req.getCostPrice() : req.getSalePrice())
                .salePrice(req.getSalePrice())
                .unit(StringUtils.hasText(req.getUnit()) ? req.getUnit().trim() : "Cái")
                .active(true)
                .build();

        return productRepository.save(product);
    }

    @Transactional
    public Product updateProduct(Long id, ProductRequest req) {
        Product existing = getProductById(id);

        if (!existing.getBarcode().equalsIgnoreCase(req.getBarcode().trim())) {
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

        return productRepository.save(existing);
    }

    @Transactional
    public void deleteProduct(Long id) {
        Product existing = getProductById(id);
        existing.setActive(false);
        productRepository.save(existing);
    }
}
