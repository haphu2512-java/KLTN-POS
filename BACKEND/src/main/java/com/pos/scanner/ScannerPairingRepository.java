package com.pos.scanner;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScannerPairingRepository extends JpaRepository<ScannerPairing, String> {

    List<ScannerPairing> findByTerminalIdOrderByCreatedAtDesc(String terminalId);

    List<ScannerPairing> findByDeviceId(String deviceId);

    List<ScannerPairing> findByTerminalIdAndUsernameAndDeviceIdIsNull(String terminalId, String username);

    List<ScannerPairing> findByDeviceIdIsNullOrderByCreatedAtDesc();
}
