package com.pos.inventory;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Ghi lại mỗi lần điều chuyển; mã client_request_id chặn việc trừ/cộng kho 2 lần khi gửi lại */
@Entity
@Table(name = "stock_transfer")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "from_branch_id", nullable = false)
    private Long fromBranchId;

    @Column(name = "to_branch_id", nullable = false)
    private Long toBranchId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "employee_id")
    private Long employeeId;

    @Column(length = 255)
    private String note;

    @Column(name = "client_request_id", length = 64, unique = true)
    private String clientRequestId;

    @Builder.Default
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
