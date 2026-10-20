package com.pos.sale;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.pos.branch.Branch;
import com.pos.customer.Customer;
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
@Table(name = "invoice")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(name = "employee_id")
    private Long employeeId;

    @Column(name = "subtotal", precision = 14, scale = 2)
    private BigDecimal subtotal;

    @Builder.Default
    @Column(name = "discount", precision = 14, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    @Column(name = "received_amount", precision = 14, scale = 2)
    private BigDecimal receivedAmount;

    @Column(name = "change_amount", precision = 14, scale = 2)
    private BigDecimal changeAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    @Builder.Default
    private InvoiceStatus status = InvoiceStatus.PAID;

    @Column(length = 255)
    private String note;

    /** Mã do máy POS tạo cho mỗi đơn; gửi lại cùng mã (sau khi timeout) sẽ không tạo hóa đơn thứ hai */
    @Column(name = "client_request_id", length = 64, unique = true)
    private String clientRequestId;

    @Builder.Default
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<InvoiceDetail> details = new ArrayList<>();
}
