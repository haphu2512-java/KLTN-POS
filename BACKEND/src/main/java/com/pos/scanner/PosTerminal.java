package com.pos.scanner;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Một quầy thu ngân (một máy POS). Mã quét từ điện thoại được gửi tới quầy đã ghép cặp,
 * không phụ thuộc tài khoản nào đang đăng nhập trên máy POS.
 */
@Entity
@Table(name = "pos_terminal")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PosTerminal {

    /** UUID do server sinh; chỉ máy POS giữ, không in lên mã QR */
    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "branch_id")
    private Long branchId;

    @Column(name = "created_by", length = 50)
    private String createdBy;

    @Builder.Default
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
