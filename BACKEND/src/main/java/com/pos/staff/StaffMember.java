package com.pos.staff;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Nhân viên trực ca của một chi nhánh. Không phải tài khoản đăng nhập: quầy dùng tài khoản chung,
 * quản lý xếp nhân viên vào ca và ca mở ra thuộc về nhân viên được xếp.
 */
@Entity
@Table(name = "staff_member")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(length = 20)
    private String phone;

    /** Vị trí, vd. "Thu ngân", "Bán thời gian" */
    @Column(length = 50)
    private String position;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
