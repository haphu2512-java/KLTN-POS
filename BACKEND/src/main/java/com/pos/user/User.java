package com.pos.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    /**
     * Các chi nhánh nhân viên được phép làm việc. Lúc đăng nhập nhân viên chọn một trong số này.
     * ADMIN không cần gán: được chọn mọi chi nhánh đang hoạt động.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_branch", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "branch_id", nullable = false)
    @Builder.Default
    private Set<Long> branchIds = new HashSet<>();

    /**
     * Chi nhánh của phiên đăng nhập hiện tại, lấy từ token đã xác thực (không lưu DB).
     * Chỉ có giá trị khi User được lấy qua CurrentUserService.
     */
    @Transient
    private Long currentBranchId;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    /** PIN duyệt tại quầy của ADMIN/MANAGER (BCrypt), dùng cho giảm giá vượt hạn mức của thu ngân */
    @JsonIgnore
    @Column(name = "approval_pin_hash")
    private String approvalPinHash;

    @Builder.Default
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
