package com.pos.scanner;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Điện thoại (của một tài khoản) đã ghép cặp với một quầy POS bằng mã QR.
 * id là mã bí mật điện thoại gửi kèm mỗi lần bắn mã.
 */
@Entity
@Table(name = "scanner_pairing")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScannerPairing {

    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "terminal_id", nullable = false)
    private PosTerminal terminal;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(name = "device_name", length = 100)
    private String deviceName;

    @Builder.Default
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;
}
