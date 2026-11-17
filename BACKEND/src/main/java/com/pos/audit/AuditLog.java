package com.pos.audit;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Một dòng nhật ký thao tác: ai, làm gì, trên đối tượng nào, lúc nào, từ máy nào. Chỉ thêm, không sửa/xóa. */
@Entity
@Table(name = "audit_log", indexes = {
        @Index(name = "idx_audit_created", columnList = "created_at"),
        @Index(name = "idx_audit_action", columnList = "action"),
        @Index(name = "idx_audit_branch", columnList = "branch_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "actor_username", length = 50)
    private String actorUsername;

    @Column(name = "actor_role", length = 20)
    private String actorRole;

    @Column(name = "branch_id")
    private Long branchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AuditAction action;

    @Column(name = "entity_type", length = 40)
    private String entityType;

    @Column(name = "entity_id", length = 40)
    private String entityId;

    /** Mô tả ngắn, đọc được ngay (vd. "Giá bán 15.000 → 18.000") */
    @Column(nullable = false, length = 500)
    private String summary;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Transient
    public String getActionLabel() {
        return action != null ? action.getLabel() : null;
    }
}
