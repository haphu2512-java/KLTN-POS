package com.pos.product;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "product")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String barcode;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 150)
    private String category;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "cost_price", precision = 14, scale = 2)
    @JsonSerialize(using = CostPriceSerializer.class)
    private BigDecimal costPrice;

    @Column(name = "sale_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal salePrice;

    @Builder.Default
    @Column(length = 30)
    private String unit = "Cái";

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    @Builder.Default
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
