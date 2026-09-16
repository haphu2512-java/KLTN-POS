package com.pos.inventory;

import com.pos.branch.Branch;
import com.pos.product.Product;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "inventory", uniqueConstraints = {
        @UniqueConstraint(name = "uk_product_branch", columnNames = {"product_id", "branch_id"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Builder.Default
    @Column(nullable = false)
    private Integer quantity = 0;

    @Builder.Default
    @Column(name = "min_threshold", nullable = false)
    private Integer minThreshold = 5;
}
