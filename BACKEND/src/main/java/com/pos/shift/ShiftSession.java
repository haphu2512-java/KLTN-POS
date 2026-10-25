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
 * Một lượt trực ca tại một chi nhánh, thuộc về nhân viên được xếp ca (staffMemberId): tiền đầu ca, tiền chốt cuối ca
 * và ghi chú bàn giao.
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

    /** Tài khoản bấm mở ca (thường là tài khoản quầy dùng chung) */
    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    /** Nhân viên trực ca: người được quản lý xếp vào ca này (hoặc người trực thay). Null với ca cũ */
    @Column(name = "staff_member_id")
    private Long staffMemberId;

    /** Tên nhân viên trực ca (ca cũ: tên tài khoản) */
    @Column(name = "employee_name", length = 100)
    private String employeeName;

    @Column(name = "shift_date", nullable = false)
    private LocalDate shiftDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ShiftStatus status = ShiftStatus.OPEN;

    /** Cột cũ (mỗi nhân viên một ca mở); ca mới không dùng, đóng ca thì xóa về null */
    @Column(name = "open_key", unique = true)
    private Long openKey;

    /**
     * Bằng branchId khi ca đang mở, null khi đã đóng. Cột unique nên mỗi chi nhánh chỉ có
     * tối đa một ca mở, kể cả khi hai máy bấm "Mở ca" cùng lúc.
     */
    @Column(name = "branch_open_key", unique = true)
    private Long branchOpenKey;

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

    // ---------- Nhận két ----------

    /** Số tiền ca trước để lại trong két lúc mở ca này (null nếu chưa có ca trước ở chi nhánh) */
    @Column(name = "handover_expected", precision = 14, scale = 2)
    private BigDecimal handoverExpected;

    /** openingCash - handoverExpected: tiền mất/thừa giữa hai ca, không tính vào ca này */
    @Column(name = "handover_difference", precision = 14, scale = 2)
    private BigDecimal handoverDifference;

    @Column(name = "handover_reason", length = 255)
    private String handoverReason;

    /** Lý do quản lý mở ca ngoài khung giờ của ca */
    @Column(name = "open_override_reason", length = 255)
    private String openOverrideReason;

    // ---------- Thu/chi két (chốt lúc đóng ca) ----------

    @Column(name = "expenses", precision = 14, scale = 2)
    private BigDecimal expenses;

    @Column(name = "drops", precision = 14, scale = 2)
    private BigDecimal drops;

    @Column(name = "float_in", precision = 14, scale = 2)
    private BigDecimal floatIn;

    // ---------- Đối soát không tiền mặt (chốt lúc đóng ca) ----------

    @Column(name = "vietqr_sales", precision = 14, scale = 2)
    private BigDecimal vietqrSales;

    @Column(name = "card_sales", precision = 14, scale = 2)
    private BigDecimal cardSales;

    @Column(name = "vietqr_refunds", precision = 14, scale = 2)
    private BigDecimal vietqrRefunds;

    @Column(name = "card_refunds", precision = 14, scale = 2)
    private BigDecimal cardRefunds;

    /** Tiền trên sao kê VietQR thu ngân nhập (bằng số hệ thống nếu không nhập) */
    @Column(name = "counted_vietqr", precision = 14, scale = 2)
    private BigDecimal countedVietqr;

    @Column(name = "counted_card", precision = 14, scale = 2)
    private BigDecimal countedCard;

    @Column(name = "vietqr_difference", precision = 14, scale = 2)
    private BigDecimal vietqrDifference;

    @Column(name = "card_difference", precision = 14, scale = 2)
    private BigDecimal cardDifference;

    @Column(name = "difference_reason", length = 500)
    private String differenceReason;

    /** Số tờ theo mệnh giá lúc đếm két, dạng JSON {"500000":2,...} */
    @Column(name = "denominations", length = 500)
    private String denominations;

    // ---------- Bàn giao ----------

    /** Tiền mặt để lại trong két cho ca sau */
    @Column(name = "left_in_drawer", precision = 14, scale = 2)
    private BigDecimal leftInDrawer;

    /** Tiền mặt nộp quỹ lúc đóng ca = closingCash - leftInDrawer */
    @Column(name = "final_drop", precision = 14, scale = 2)
    private BigDecimal finalDrop;

    // ---------- Duyệt ca lệch ----------

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_note", length = 500)
    private String reviewNote;
}
