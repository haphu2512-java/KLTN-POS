package com.pos.shift;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Một lượt trực ca của một nhân viên: tiền đầu ca, tiền chốt cuối ca và ghi chú bàn giao.
 * Các số tổng (doanh số, tiền mặt dự kiến, chênh lệch) được chốt lại lúc đóng ca.
 */
@Entity
@Table(name = "shift_session")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShiftSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "work_shift_id", nullable = false)
    private WorkShift workShift;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "employee_name", length = 100)
    private String employeeName;

    @Column(name = "shift_date", nullable = false)
    private LocalDate shiftDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ShiftStatus status = ShiftStatus.OPEN;

    /**
     * Bằng employeeId khi ca đang mở, null khi đã đóng. Cột unique nên mỗi nhân viên
     * chỉ có tối đa một ca mở, kể cả khi bấm "Mở ca" hai lần cùng lúc.
     */
    @Column(name = "open_key", unique = true)
    private Long openKey;

    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;

    @Column(name = "opening_cash", nullable = false, precision = 14, scale = 2)
    private BigDecimal openingCash;

    @Column(name = "opening_note", length = 500)
    private String openingNote;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    /** Người bấm đóng ca: chính nhân viên, hoặc quản lý đóng hộ khi nhân viên quên */
    @Column(name = "closed_by")
    private Long closedBy;

    /** Tiền mặt thực đếm được trong két lúc đóng ca */
    @Column(name = "closing_cash", precision = 14, scale = 2)
    private BigDecimal closingCash;

    /** Tiền đầu ca + doanh số tiền mặt - tiền mặt hoàn trả của ca */
    @Column(name = "expected_cash", precision = 14, scale = 2)
    private BigDecimal expectedCash;

    /** closingCash - expectedCash: âm là thiếu tiền, dương là dư */
    @Column(name = "cash_difference", precision = 14, scale = 2)
    private BigDecimal cashDifference;

    @Column(name = "cash_sales", precision = 14, scale = 2)
    private BigDecimal cashSales;

    @Column(name = "non_cash_sales", precision = 14, scale = 2)
    private BigDecimal nonCashSales;

    @Column(name = "invoice_count")
    private Integer invoiceCount;

    /** Tiền mặt chi ra từ két để hoàn trả trong ca (null với ca đóng trước khi có cột này) */
    @Column(name = "cash_refunds", precision = 14, scale = 2)
    private BigDecimal cashRefunds;

    @Column(name = "non_cash_refunds", precision = 14, scale = 2)
    private BigDecimal nonCashRefunds;

    @Column(name = "refunded_count")
    private Integer refundedCount;

    @Column(name = "handover_note", length = 500)
    private String handoverNote;
}
