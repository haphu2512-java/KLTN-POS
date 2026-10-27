package com.pos.shift;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class CashMovementResponse {
    private Long id;
    private Long shiftSessionId;
    private CashMovementType type;
    private String category;
    private BigDecimal amount;
    private String reason;
    private String counterpart;
    private Long createdBy;
    private String createdByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

    static CashMovementResponse of(ShiftCashMovement m) {
        return CashMovementResponse.builder()
                .id(m.getId())
                .shiftSessionId(m.getShiftSessionId())
                .type(m.getType())
                .category(m.getCategory())
                .amount(m.getAmount())
                .reason(m.getReason())
                .counterpart(m.getCounterpart())
                .createdBy(m.getCreatedBy())
                .createdByName(m.getCreatedByName())
                .createdAt(m.getCreatedAt())
                .updatedAt(m.getUpdatedAt())
                .deletedAt(m.getDeletedAt())
                .build();
    }
}
