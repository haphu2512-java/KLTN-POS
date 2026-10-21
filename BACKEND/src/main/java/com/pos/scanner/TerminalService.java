package com.pos.scanner;

import com.pos.common.BusinessException;
import com.pos.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quầy POS và việc ghép cặp điện thoại với quầy.
 * Luồng: máy POS đăng ký quầy → xin mã ghép cặp (6 số, sống vài phút) → hiện QR →
 * điện thoại đã đăng nhập quét QR → server tạo ScannerPairing; từ đó mã quét của điện thoại đi tới quầy này.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TerminalService {

    static final long PAIRING_CODE_TTL_SECONDS = 300;

    private final PosTerminalRepository terminalRepository;
    private final ScannerPairingRepository pairingRepository;
    private final SimpMessagingTemplate messagingTemplate;

    private final SecureRandom random = new SecureRandom();

    /** Mã ghép cặp đang chờ: code → (quầy, hạn). Mã ngắn hạn nên giữ trong bộ nhớ là đủ. */
    private final Map<String, PendingCode> pendingCodes = new ConcurrentHashMap<>();

    record PendingCode(String terminalId, Instant expiresAt) {
    }

    @Transactional
    public PosTerminal register(String name, User user) {
        PosTerminal terminal = PosTerminal.builder()
                .id(UUID.randomUUID().toString())
                .name(cleanName(name))
                .branchId(user.getCurrentBranchId())
                .createdBy(user.getUsername())
                .build();
        return terminalRepository.save(terminal);
    }

    @Transactional(readOnly = true)
    public PosTerminal get(String terminalId, User user) {
        PosTerminal terminal = terminalRepository.findById(terminalId)
                .orElseThrow(() -> new BusinessException("TERMINAL_NOT_FOUND", "Không tìm thấy quầy POS", HttpStatus.NOT_FOUND));
        if (!TerminalAccess.canUse(user, terminal)) {
            // Không tiết lộ quầy của chi nhánh khác
            throw new BusinessException("TERMINAL_NOT_FOUND", "Không tìm thấy quầy POS", HttpStatus.NOT_FOUND);
        }
        return terminal;
    }

    @Transactional
    public PosTerminal rename(String terminalId, String name, User user) {
        PosTerminal terminal = get(terminalId, user);
        terminal.setName(cleanName(name));
        return terminalRepository.save(terminal);
    }

    /** Tạo mã mới cho quầy; mã cũ của quầy này hết hiệu lực */
    public Map<String, Object> createPairingCode(String terminalId, User user) {
        PosTerminal terminal = get(terminalId, user);
        Instant now = Instant.now();
        pendingCodes.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now)
                || e.getValue().terminalId().equals(terminal.getId()));

        String code;
        do {
            code = String.format("%06d", random.nextInt(1_000_000));
        } while (pendingCodes.putIfAbsent(code, new PendingCode(terminal.getId(), now.plusSeconds(PAIRING_CODE_TTL_SECONDS))) != null);

        Map<String, Object> result = new HashMap<>();
        result.put("code", code);
        result.put("terminalName", terminal.getName());
        result.put("expiresInSeconds", PAIRING_CODE_TTL_SECONDS);
        return result;
    }

    /** Điện thoại gửi mã đọc từ QR; mã chỉ dùng được một lần */
    @Transactional
    public ScannerPairing pair(String code, String deviceName, User user) {
        String clean = code == null ? "" : code.trim();
        PendingCode pending = pendingCodes.remove(clean);
        if (pending == null || pending.expiresAt().isBefore(Instant.now())) {
            throw new BusinessException("PAIRING_CODE_INVALID",
                    "Mã ghép cặp không đúng hoặc đã hết hạn. Hãy tạo mã mới trên máy POS.", HttpStatus.BAD_REQUEST);
        }
        PosTerminal terminal = terminalRepository.findById(pending.terminalId())
                .orElseThrow(() -> new BusinessException("TERMINAL_NOT_FOUND", "Không tìm thấy quầy POS", HttpStatus.NOT_FOUND));
        if (!TerminalAccess.canUse(user, terminal)) {
            throw new BusinessException("TERMINAL_OTHER_BRANCH",
                    "Quầy POS này thuộc chi nhánh khác với tài khoản của bạn", HttpStatus.FORBIDDEN);
        }

        ScannerPairing pairing = pairingRepository.save(ScannerPairing.builder()
                .id(UUID.randomUUID().toString())
                .terminal(terminal)
                .username(user.getUsername())
                .deviceName(deviceName == null || deviceName.isBlank() ? "Điện thoại quét mã" : deviceName.trim())
                .build());

        Map<String, Object> event = new HashMap<>(toDto(pairing));
        event.put("type", "PAIRED");
        event.put("fullName", user.getFullName());
        send(terminal.getId(), event);
        return pairing;
    }

    /**
     * Quầy mà điện thoại này đang ghép cặp. Ghép cặp gắn với tài khoản đã quét QR:
     * đăng nhập tài khoản khác trên điện thoại thì phải ghép lại.
     */
    @Transactional
    public PosTerminal resolvePairing(String pairingId, User user) {
        ScannerPairing pairing = pairingId == null ? null : pairingRepository.findById(pairingId)
                .filter(p -> p.getUsername().equals(user.getUsername()))
                .filter(p -> TerminalAccess.canUse(user, p.getTerminal()))
                .orElse(null);
        if (pairing == null) {
            throw new BusinessException("NOT_PAIRED",
                    "Điện thoại chưa ghép cặp với quầy POS nào. Hãy quét mã QR trên máy POS.", HttpStatus.CONFLICT);
        }
        pairing.setLastUsedAt(LocalDateTime.now());
        pairingRepository.save(pairing);
        return pairing.getTerminal();
    }

    @Transactional(readOnly = true)
    public ScannerPairing getOwnPairing(String pairingId, User user) {
        return pairingRepository.findById(pairingId)
                .filter(p -> p.getUsername().equals(user.getUsername()))
                .orElseThrow(() -> new BusinessException("NOT_PAIRED",
                        "Ghép cặp không còn hiệu lực", HttpStatus.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listPairings(String terminalId, User user) {
        get(terminalId, user);
        return pairingRepository.findByTerminalIdOrderByCreatedAtDesc(terminalId).stream()
                .map(TerminalService::toDto)
                .toList();
    }

    /** Máy POS gỡ một điện thoại khỏi quầy */
    @Transactional
    public void unpairFromTerminal(String terminalId, String pairingId, User user) {
        get(terminalId, user);
        ScannerPairing pairing = pairingRepository.findById(pairingId)
                .filter(p -> p.getTerminal().getId().equals(terminalId))
                .orElseThrow(() -> new BusinessException("NOT_PAIRED", "Không tìm thấy điện thoại đã ghép", HttpStatus.NOT_FOUND));
        delete(pairing);
    }

    /** Điện thoại tự hủy ghép cặp của mình */
    @Transactional
    public void unpairOwn(String pairingId, User user) {
        delete(getOwnPairing(pairingId, user));
    }

    public static Map<String, Object> toDto(ScannerPairing p) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("pairingId", p.getId());
        dto.put("terminalName", p.getTerminal().getName());
        dto.put("branchId", p.getTerminal().getBranchId());
        dto.put("username", p.getUsername());
        dto.put("deviceName", p.getDeviceName());
        dto.put("createdAt", Objects.toString(p.getCreatedAt(), null));
        dto.put("lastUsedAt", Objects.toString(p.getLastUsedAt(), null));
        return dto;
    }

    private void delete(ScannerPairing pairing) {
        pairingRepository.delete(pairing);
        send(pairing.getTerminal().getId(), Map.of("type", "UNPAIRED", "pairingId", pairing.getId()));
    }

    void send(String terminalId, Map<String, Object> payload) {
        try {
            messagingTemplate.convertAndSend(TerminalAccess.topicOf(terminalId), payload);
        } catch (Exception e) {
            log.warn("Không gửi được tin tới quầy {}: {}", terminalId, e.getMessage());
        }
    }

    private static String cleanName(String name) {
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty()) {
            return "Quầy POS";
        }
        return clean.length() > 100 ? clean.substring(0, 100) : clean;
    }
}
