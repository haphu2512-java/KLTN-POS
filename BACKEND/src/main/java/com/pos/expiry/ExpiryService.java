package com.pos.expiry;

import com.pos.common.BusinessException;
import com.pos.inventory.InventoryRepository;
import com.pos.purchase.StockInRepository;
import com.pos.security.CurrentUserService;
import com.pos.stockcount.AdjustmentReason;
import com.pos.stockcount.StockCount;
import com.pos.stockcount.StockCountService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Theo dõi hạn sử dụng theo lô nhập.
 *
 * Hệ thống không ghi lô cho từng món bán ra, mà giả định cửa hàng bán theo FEFO (hết hạn trước, xuất trước):
 * tồn hiện tại được coi là thuộc các lô có hạn xa nhất. Vì vậy số còn lại của mỗi lô suy ra được từ tồn kho
 * mà không phải sửa luồng bán hàng, trả hàng, điều chuyển. Hàng nhập không khai hạn không được theo dõi.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExpiryService {

    public static final int DEFAULT_WARN_DAYS = 30;

    private final StockInRepository stockInRepository;
    private final InventoryRepository inventoryRepository;
    private final CurrentUserService currentUserService;
    private final StockCountService stockCountService;

    public enum LotStatus { EXPIRED, NEAR_EXPIRY, OK }

    public record LotView(Long productId, String productName, String barcode, String lotNumber, LocalDate expiryDate,
                          int receivedQty, int remainingQty, long daysLeft, LotStatus status, BigDecimal value,
                          Long stockInId, LocalDateTime receivedAt) {}

    public record ExpiryReport(Long branchId, int warnDays, List<LotView> lots, int expiredLots, int nearLots,
                               BigDecimal expiredValue, BigDecimal nearValue) {}

    /** Lô còn hàng đã hết hạn hoặc hết hạn trong warnDays ngày tới, hết hạn sớm nhất trước */
    public ExpiryReport report(Long requestedBranchId, Integer warnDays) {
        Long branchId = currentUserService.branchForRead(requestedBranchId, false);
        int days = warnDays == null || warnDays < 0 ? DEFAULT_WARN_DAYS : Math.min(warnDays, 365);
        List<LotView> lots = remainingLots(branchId, LocalDate.now(), days).stream()
                .filter(l -> l.remainingQty() > 0 && l.status() != LotStatus.OK)
                .sorted(Comparator.comparing(LotView::expiryDate).thenComparing(LotView::productName))
                .toList();
        int expired = 0, near = 0;
        BigDecimal expiredValue = BigDecimal.ZERO, nearValue = BigDecimal.ZERO;
        for (LotView l : lots) {
            if (l.status() == LotStatus.EXPIRED) {
                expired++;
                expiredValue = expiredValue.add(l.value());
            } else {
                near++;
                nearValue = nearValue.add(l.value());
            }
        }
        return new ExpiryReport(branchId, days, lots, expired, near, expiredValue, nearValue);
    }

    /**
     * Lập phiếu kiểm kê nháp để hủy hàng đã hết hạn: mỗi sản phẩm một dòng, số đếm = tồn hệ thống - số hết hạn,
     * lý do Hết hạn. Nhân viên kiểm lại thực tế rồi gửi quản lý duyệt như phiếu kiểm kê thường.
     */
    @Transactional
    public StockCount createDisposalCount() {
        Long branchId = currentUserService.currentBranchId();
        Map<Long, Integer> expiredByProduct = new LinkedHashMap<>();
        for (LotView l : remainingLots(branchId, LocalDate.now(), 0)) {
            if (l.status() == LotStatus.EXPIRED && l.remainingQty() > 0) {
                expiredByProduct.merge(l.productId(), l.remainingQty(), Integer::sum);
            }
        }
        if (expiredByProduct.isEmpty()) {
            throw new BusinessException("NO_EXPIRED_STOCK", "Không có lô hàng nào đã hết hạn ở chi nhánh này");
        }
        StockCount count = stockCountService.create("Hủy hàng hết hạn " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        for (Map.Entry<Long, Integer> e : expiredByProduct.entrySet()) {
            Integer system = inventoryRepository.findQuantity(e.getKey(), branchId);
            int counted = Math.max(0, (system != null ? system : 0) - e.getValue());
            count = stockCountService.upsertLine(count.getId(),
                    new StockCountService.LineRequest(e.getKey(), null, counted, AdjustmentReason.EXPIRED, false));
        }
        return count;
    }

    /** Số còn lại của từng lô có hạn ở chi nhánh, chia tồn hiện tại cho các lô hạn xa nhất trước (giả định FEFO) */
    List<LotView> remainingLots(Long branchId, LocalDate today, int warnDays) {
        Map<Long, List<Object[]>> byProduct = new LinkedHashMap<>();
        for (Object[] row : stockInRepository.lotsWithExpiry(branchId)) {
            byProduct.computeIfAbsent((Long) row[0], k -> new ArrayList<>()).add(row);
        }
        List<LotView> result = new ArrayList<>();
        for (Map.Entry<Long, List<Object[]>> e : byProduct.entrySet()) {
            Integer stock = inventoryRepository.findQuantity(e.getKey(), branchId);
            result.addAll(allocate(e.getValue(), stock != null ? stock : 0, today, warnDays));
        }
        return result;
    }

    /** Thuần tính toán, tách riêng để unit test: rows là các lô của cùng một sản phẩm */
    static List<LotView> allocate(List<Object[]> rows, int stock, LocalDate today, int warnDays) {
        List<Object[]> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator.<Object[], LocalDate>comparing(r -> (LocalDate) r[4])
                .thenComparing(r -> (LocalDateTime) r[8], Comparator.nullsFirst(Comparator.naturalOrder()))
                .reversed());
        int left = Math.max(0, stock);
        List<LotView> out = new ArrayList<>();
        for (Object[] r : sorted) {
            int received = ((Number) r[5]).intValue();
            int remaining = Math.min(received, left);
            left -= remaining;
            LocalDate expiry = (LocalDate) r[4];
            long daysLeft = ChronoUnit.DAYS.between(today, expiry);
            LotStatus status = daysLeft < 0 ? LotStatus.EXPIRED : daysLeft <= warnDays ? LotStatus.NEAR_EXPIRY : LotStatus.OK;
            BigDecimal cost = r[6] != null ? (BigDecimal) r[6] : BigDecimal.ZERO;
            out.add(new LotView((Long) r[0], (String) r[1], (String) r[2], (String) r[3], expiry, received, remaining,
                    daysLeft, status, cost.multiply(BigDecimal.valueOf(remaining)), (Long) r[7], (LocalDateTime) r[8]));
        }
        return out;
    }
}
