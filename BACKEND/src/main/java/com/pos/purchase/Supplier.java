package com.pos.purchase;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "supplier")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Supplier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 20)
    private String phone;

    @Column(length = 255)
    private String address;

    /**
     * Danh mục sản phẩm NCC cung cấp (trùng với product.category). Lập đơn từ đề xuất AI chỉ đưa
     * mặt hàng thuộc các danh mục này vào đơn của NCC; NCC chưa có danh mục thì không nhận mặt hàng nào từ AI.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "supplier_category", joinColumns = @JoinColumn(name = "supplier_id"))
    @Column(name = "category", length = 100, nullable = false)
    @OrderBy
    @Builder.Default
    private Set<String> categories = new LinkedHashSet<>();
}
