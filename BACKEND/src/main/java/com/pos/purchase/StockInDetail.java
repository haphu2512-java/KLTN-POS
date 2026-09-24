package com.pos.purchase;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.pos.product.Product;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "stock_in_detail")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockInDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_in_id", nullable = false)
    @JsonBackReference
    private StockIn stockIn;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_cost", precision = 14, scale = 2)
    private BigDecimal unitCost;
}
