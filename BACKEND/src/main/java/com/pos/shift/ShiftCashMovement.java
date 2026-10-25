package com.pos.shift;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Một khoản thu/chi két trong ca. Chỉ sửa, xóa được khi ca còn mở; xóa là xóa mềm để còn vết.
 */
@Entity
@Table(name = "shift_cash_movement")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShiftCashMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shift_session_id", nullable = false)
    private Long shiftSessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CashMovementType type;

    /** Danh mục chi (vd. "Vận chuyển"), để trống với nộp quỹ / bổ sung */
    @Column(length = 50)
    private String category;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 255)
    private String reason;

    /** Người nhận tiền nộp quỹ hoặc người đưa tiền lẻ */
    @Column(length = 100)
    private String counterpart;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_by_name", length = 100)
    private String createdByName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;
}
