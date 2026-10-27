package com.pos.purchase;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.pos.branch.Branch;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "stock_in")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockIn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @Column(name = "employee_id")
    private Long employeeId;

    @Column(name = "total_cost", precision = 14, scale = 2)
    private BigDecimal totalCost;

    @Column(length = 255)
    private String note;

    /** Mã do màn hình nhập kho tạo cho mỗi phiếu; gửi lại cùng mã (sau khi timeout) sẽ không cộng kho lần nữa */
    @Column(name = "client_request_id", length = 64, unique = true)
    private String clientRequestId;

    @Builder.Default
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @OneToMany(mappedBy = "stockIn", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<StockInDetail> details = new ArrayList<>();
}
