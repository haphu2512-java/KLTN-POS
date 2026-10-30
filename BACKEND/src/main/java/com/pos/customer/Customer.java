package com.pos.customer;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "customer")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(unique = true, length = 20)
    private String phone;

    /**
     * Số dư điểm. Chỉ đổi bằng câu UPDATE nguyên tử trong CustomerRepository (updatable = false),
     * để lưu entity không ghi đè số dư mà quầy khác vừa cộng/trừ.
     */
    @Builder.Default
    @Column(nullable = false, updatable = false)
    private Integer points = 0;

    @Column(length = 255)
    private String address;

    @Builder.Default
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
