package com.pos.scanner;

import com.pos.product.Product;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Thông tin sản phẩm gửi xuống điện thoại / POS khi quét mã.
 * Không chứa giá vốn.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScannerProductDto {
    private Long id;
    private String barcode;
    private String name;
    private String category;
    private String unit;
    private String imageUrl;
    private BigDecimal salePrice;
    /** Tồn kho tại chi nhánh của người quét; null nếu chưa có bản ghi tồn kho */
    private Integer stock;

    public static ScannerProductDto from(Product p, Integer stock) {
        return ScannerProductDto.builder()
                .id(p.getId())
                .barcode(p.getBarcode())
                .name(p.getName())
                .category(p.getCategory())
                .unit(p.getUnit())
                .imageUrl(p.getImageUrl())
                .salePrice(p.getSalePrice())
                .stock(stock)
                .build();
    }
}
