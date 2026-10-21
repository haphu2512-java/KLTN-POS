package com.pos.shift;

import com.pos.branch.Branch;
import com.pos.branch.BranchRepository;
import com.pos.common.BusinessException;
import com.pos.sale.Invoice;
import com.pos.sale.InvoiceRepository;
import com.pos.sale.InvoiceStatus;
import com.pos.sale.PaymentMethod;
import com.pos.user.Role;
import com.pos.user.User;
import com.pos.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Quản lý ca làm việc: mở ca với tiền đầu ca, gắn hóa đơn vào ca đang mở,
 * đóng ca bằng số tiền mặt đếm được và tính chênh lệch so với số tiền lẽ ra phải có.
 * <p>
 * Tiền mặt dự kiến = tiền đầu ca + tiền mặt bán trong ca - tiền mặt hoàn trả chi từ két của ca.
 * <p>
 * Quyền xem ca: ADMIN mọi ca; MANAGER các ca ở chi nhánh đang đăng nhập; nhân viên khác chỉ ca của mình.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ShiftService {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    /** Giới hạn khoảng ngày khi xem lịch sử ca để một lần tải không quá nặng */
    static final int MAX_RANGE_DAYS = 366;

    private final WorkShiftRepository workShiftRepository;
    private final ShiftSessionRepository shiftSessionRepository;
    private final InvoiceRepository invoiceRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;

    /** true: thu ngân phải mở ca mới bán được hàng */
    @Value("${pos.shift.require-open-shift:true}")
    private boolean requireOpenShift = true;

    // ---------- Ca cố định ----------

    public List<WorkShift> getWorkShifts() {
        return workShiftRepository.findAllByOrderBySortOrderAsc();
    }

    @Transactional
    public WorkShift updateWorkShift(Long id, WorkShiftRequest req) {
        WorkShift shift = workShiftRepository.findById(id)
                .orElseThrow(() -> new BusinessException("WORK_SHIFT_NOT_FOUND", "Không tìm thấy ca làm việc", HttpStatus.NOT_FOUND));
        applyWorkShift(shift, req);
        return workShiftRepository.save(shift);
    }

    @Transactional
    public WorkShift createWorkShift(WorkShiftRequest req) {
        int nextOrder = workShiftRepository.findAllByOrderBySortOrderAsc().stream()
                .mapToInt(WorkShift::getSortOrder).max().orElse(0) + 1;
        WorkShift shift = WorkShift.builder().sortOrder(nextOrder).active(true).build();
        applyWorkShift(shift, req);
        return workShiftRepository.save(shift);
    }

    private void applyWorkShift(WorkShift shift, WorkShiftRequest req) {
        if (req.getStartTime().equals(req.getEndTime())) {
            throw new BusinessException("INVALID_WORK_SHIFT", "Giờ bắt đầu và kết thúc không được trùng nhau");
        }
        shift.setName(req.getName().trim());
        shift.setStartTime(req.getStartTime());
        shift.setEndTime(req.getEndTime());
        if (req.getActive() != null) {
            shift.setActive(req.getActive());
        }
    }

    /** Ca chứa giờ {@code time}, nếu giờ đó nằm ngoài mọi ca thì null */
    WorkShift workShiftAt(LocalTime time) {
        return workShiftRepository.findByActiveTrueOrderBySortOrderAsc().stream()
                .filter(ws -> ws.contains(time))
                .findFirst()
                .orElse(null);
    }

    // ---------- Ca trực của nhân viên ----------

    public CurrentShiftResponse getCurrent(User actor) {
        ShiftSession open = shiftSessionRepository.findByEmployeeIdAndStatus(actor.getId(), ShiftStatus.OPEN).orElse(null);
        CurrentShiftResponse.CurrentShiftResponseBuilder resp = CurrentShiftResponse.builder()
                .shiftRequired(isShiftRequired(actor));
        if (open != null) {
            return resp.session(toResponse(open)).build();
        }

        WorkShift suggested = workShiftAt(LocalTime.now());
        resp.suggestedWorkShiftId(suggested != null ? suggested.getId() : null)
                .suggestedOpeningCash(BigDecimal.ZERO);
        if (actor.getCurrentBranchId() != null) {
            shiftSessionRepository.findFirstByBranchIdAndStatusOrderByClosedAtDesc(actor.getCurrentBranchId(), ShiftStatus.CLOSED)
                    .ifPresent(prev -> resp.previousShift(toResponse(prev)).suggestedOpeningCash(prev.getClosingCash()));
        }
        return resp.build();
    }

    /** Mở ca tại chi nhánh của phiên đăng nhập (chọn lúc đăng nhập), không nhận chi nhánh từ client */
    @Transactional
    public ShiftSessionResponse openShift(OpenShiftRequest req, User actor) {
        if (shiftSessionRepository.findByEmployeeIdAndStatus(actor.getId(), ShiftStatus.OPEN).isPresent()) {
            throw new BusinessException("SHIFT_ALREADY_OPEN", "Bạn đang có một ca chưa đóng", HttpStatus.CONFLICT);
        }

        Long branchId = actor.getCurrentBranchId();
        if (branchId == null || !branchRepository.existsById(branchId)) {
            throw new BusinessException("BRANCH_REQUIRED", "Phiên đăng nhập chưa chọn chi nhánh hợp lệ để mở ca");
        }

        LocalDateTime now = LocalDateTime.now();
        WorkShift workShift;
        if (req.getWorkShiftId() != null) {
            workShift = workShiftRepository.findById(req.getWorkShiftId())
                    .filter(ws -> Boolean.TRUE.equals(ws.getActive()))
                    .orElseThrow(() -> new BusinessException("WORK_SHIFT_NOT_FOUND", "Ca làm việc không tồn tại hoặc đã ngưng"));
        } else {
            workShift = workShiftAt(now.toLocalTime());
            if (workShift == null) {
                throw new BusinessException("OUTSIDE_WORK_SHIFTS", "Giờ hiện tại không thuộc ca nào, vui lòng chọn ca");
            }
        }

        ShiftSession session = ShiftSession.builder()
                .branchId(branchId)
                .workShift(workShift)
                .employeeId(actor.getId())
                .employeeName(actor.getFullName() != null ? actor.getFullName() : actor.getUsername())
                .shiftDate(shiftDateOf(workShift, now))
                .status(ShiftStatus.OPEN)
                .openKey(actor.getId())
                .openedAt(now)
                .openingCash(req.getOpeningCash())
                .openingNote(trimToNull(req.getNote()))
                .build();
        try {
            session = shiftSessionRepository.saveAndFlush(session);
        } catch (DataIntegrityViolationException e) {
            // Hai yêu cầu mở ca cùng lúc: cột open_key unique chặn bản thứ hai
            throw new BusinessException("SHIFT_ALREADY_OPEN", "Bạn đang có một ca chưa đóng", HttpStatus.CONFLICT);
        }
        log.info("{} mở {} tại chi nhánh {} với tiền đầu ca {}", actor.getUsername(), workShift.getName(), branchId, req.getOpeningCash());
        return toResponse(session);
    }

    @Transactional
    public ShiftSessionResponse closeOwnShift(CloseShiftRequest req, User actor) {
        ShiftSession session = shiftSessionRepository.findByEmployeeIdAndStatus(actor.getId(), ShiftStatus.OPEN)
                .orElseThrow(() -> new BusinessException("SHIFT_NOT_OPEN", "Bạn chưa mở ca nào", HttpStatus.CONFLICT));
        return close(session, req, actor);
    }

    /** Quản lý đóng hộ ca của nhân viên quên đóng (MANAGER chỉ trong chi nhánh đang đăng nhập) */
    @Transactional
    public ShiftSessionResponse closeShiftById(Long id, CloseShiftRequest req, User actor) {
        ShiftSession session = find(id);
        checkManagerScope(session, actor);
        if (session.getStatus() != ShiftStatus.OPEN) {
            throw new BusinessException("SHIFT_ALREADY_CLOSED", "Ca này đã được đóng", HttpStatus.CONFLICT);
        }
        return close(session, req, actor);
    }

    private ShiftSessionResponse close(ShiftSession session, CloseShiftRequest req, User actor) {
        Totals totals = totalsOf(session);
        BigDecimal expected = totals.expectedCash(session.getOpeningCash());

        session.setStatus(ShiftStatus.CLOSED);
        session.setOpenKey(null);
        session.setClosedAt(LocalDateTime.now());
        session.setClosedBy(actor.getId());
        session.setCashSales(totals.cashSales);
        session.setNonCashSales(totals.nonCashSales);
        session.setInvoiceCount(totals.invoiceCount);
        session.setCashRefunds(totals.cashRefunds);
        session.setNonCashRefunds(totals.nonCashRefunds);
        session.setRefundedCount(totals.refundedCount);
        session.setExpectedCash(expected);
        session.setClosingCash(req.getClosingCash());
        session.setCashDifference(req.getClosingCash().subtract(expected));
        session.setHandoverNote(trimToNull(req.getHandoverNote()));
        shiftSessionRepository.save(session);

        log.info("{} đóng ca #{}: dự kiến {}, đếm được {}, chênh lệch {}", actor.getUsername(), session.getId(),
                expected, req.getClosingCash(), session.getCashDifference());
        return toResponse(session, totals, branchNames(), userNames(session));
    }

    // ---------- Xem lịch sử ca ----------

    /**
     * Lịch sử ca theo bộ lọc (mặc định hôm nay). ADMIN lọc tự do; MANAGER bị giới hạn ở chi nhánh đang
     * đăng nhập; vai trò khác chỉ thấy ca của chính mình (ở mọi chi nhánh mình từng trực).
     */
    public ShiftSummaryResponse listShifts(ShiftFilter filter, User actor) {
        LocalDate end = filter.to() != null ? filter.to() : LocalDate.now();
        LocalDate start = filter.from() != null ? filter.from() : end;
        if (start.isAfter(end)) {
            throw new BusinessException("INVALID_DATE_RANGE", "Ngày bắt đầu phải trước ngày kết thúc");
        }
        if (ChronoUnit.DAYS.between(start, end) >= MAX_RANGE_DAYS) {
            throw new BusinessException("INVALID_DATE_RANGE", "Chỉ xem được tối đa " + MAX_RANGE_DAYS + " ngày mỗi lần");
        }

        Long branchId = filter.branchId();
        Long employeeId = filter.employeeId();
        if (actor.getRole() == Role.MANAGER) {
            branchId = actor.getCurrentBranchId();
        } else if (actor.getRole() != Role.ADMIN) {
            employeeId = actor.getId();
        }

        List<ShiftSession> sessions = shiftSessionRepository.search(start, end, branchId, employeeId, filter.status());
        Map<Long, String> branches = branchNames();
        Map<Long, String> users = userNames(sessions.toArray(ShiftSession[]::new));
        List<ShiftSessionResponse> rows = sessions.stream()
                .map(s -> toResponse(s, totalsFor(s), branches, users))
                .toList();

        BigDecimal shortage = BigDecimal.ZERO;
        BigDecimal overage = BigDecimal.ZERO;
        int mismatched = 0;
        for (ShiftSessionResponse r : rows) {
            BigDecimal diff = r.getCashDifference();
            if (diff == null || diff.signum() == 0) continue;
            mismatched++;
            if (diff.signum() < 0) shortage = shortage.add(diff.negate());
            else overage = overage.add(diff);
        }
        return ShiftSummaryResponse.builder()
                .shifts(rows)
                .totalShifts(rows.size())
                .openShifts((int) rows.stream().filter(r -> r.getStatus() == ShiftStatus.OPEN).count())
                .mismatchedShifts(mismatched)
                .totalShortage(shortage)
                .totalOverage(overage)
                .build();
    }

    public ShiftSessionResponse getShift(Long id, User actor) {
        ShiftSession session = find(id);
        checkCanView(session, actor);
        return toResponse(session);
    }

    /** Các hóa đơn làm thay đổi tiền két của ca, để dò nguyên nhân thiếu/dư */
    public List<ShiftInvoiceRow> getShiftInvoices(Long id, User actor) {
        ShiftSession session = find(id);
        checkCanView(session, actor);
        return invoiceRepository.findForShift(id).stream().map(inv -> toInvoiceRow(inv, id)).toList();
    }

    // ---------- Gắn hóa đơn vào ca ----------

    /**
     * Ca dùng để gắn hóa đơn mới. Thu ngân bắt buộc có ca đang mở tại đúng chi nhánh bán;
     * các vai trò khác bán được khi không trực ca (hóa đơn không gắn ca).
     */
    public Long resolveShiftForSale(User actor, Long branchId) {
        ShiftSession open = shiftSessionRepository.findByEmployeeIdAndStatus(actor.getId(), ShiftStatus.OPEN).orElse(null);
        if (open != null && open.getBranchId().equals(branchId)) {
            return open.getId();
        }
        if (isShiftRequired(actor)) {
            throw new BusinessException("SHIFT_NOT_OPEN", "Vui lòng mở ca trước khi bán hàng", HttpStatus.CONFLICT);
        }
        return null;
    }

    /**
     * Ca có két chi tiền hoàn trả: ca đang mở của người hoàn trả ở chi nhánh của hóa đơn; nếu người đó
     * không trực ca thì ca duy nhất đang mở ở chi nhánh đó. Không xác định được (không có hoặc nhiều ca
     * đang mở) thì null, khi đó tiền hoàn trả tính vào ca đã bán hóa đơn.
     */
    public Long resolveShiftForRefund(User actor, Long invoiceBranchId) {
        ShiftSession own = shiftSessionRepository.findByEmployeeIdAndStatus(actor.getId(), ShiftStatus.OPEN).orElse(null);
        if (own != null && own.getBranchId().equals(invoiceBranchId)) {
            return own.getId();
        }
        List<ShiftSession> openHere = shiftSessionRepository.findByBranchIdAndStatus(invoiceBranchId, ShiftStatus.OPEN);
        return openHere.size() == 1 ? openHere.get(0).getId() : null;
    }

    boolean isShiftRequired(User actor) {
        return requireOpenShift && actor.getRole() == Role.CASHIER;
    }

    // ---------- Nội bộ ----------

    private ShiftSession find(Long id) {
        return shiftSessionRepository.findById(id)
                .orElseThrow(() -> new BusinessException("SHIFT_NOT_FOUND", "Không tìm thấy ca", HttpStatus.NOT_FOUND));
    }

    private void checkCanView(ShiftSession session, User actor) {
        if (session.getEmployeeId().equals(actor.getId())) return;
        checkManagerScope(session, actor);
    }

    private void checkManagerScope(ShiftSession session, User actor) {
        if (actor.getRole() == Role.ADMIN) return;
        if (actor.getRole() == Role.MANAGER && session.getBranchId().equals(actor.getCurrentBranchId())) return;
        throw new BusinessException("SHIFT_FORBIDDEN", "Bạn không có quyền với ca này", HttpStatus.FORBIDDEN);
    }

    /** Ca qua đêm mở sau nửa đêm (vd. 02:00 của ca 22:00 - 06:00) vẫn tính cho ngày bắt đầu ca */
    static LocalDate shiftDateOf(WorkShift ws, LocalDateTime openedAt) {
        boolean overnight = !ws.getStartTime().isBefore(ws.getEndTime());
        if (overnight && openedAt.toLocalTime().isBefore(ws.getEndTime())) {
            return openedAt.toLocalDate().minusDays(1);
        }
        return openedAt.toLocalDate();
    }

    record Totals(BigDecimal cashSales, BigDecimal nonCashSales, int invoiceCount,
                  BigDecimal cashRefunds, BigDecimal nonCashRefunds, int refundedCount) {

        BigDecimal expectedCash(BigDecimal openingCash) {
            BigDecimal refunds = cashRefunds != null ? cashRefunds : BigDecimal.ZERO;
            return openingCash.add(cashSales).subtract(refunds);
        }
    }

    private Totals totalsOf(ShiftSession session) {
        BigDecimal[] sold = sumByMethod(invoiceRepository.sumSoldByShift(session.getId()));
        BigDecimal[] refunded = sumByMethod(invoiceRepository.sumRefundedByShift(session.getId()));
        return new Totals(sold[0], sold[1], sold[2].intValue(), refunded[0], refunded[1], refunded[2].intValue());
    }

    /** Dòng [phương thức, số hóa đơn, tổng tiền] → {tiền mặt, không tiền mặt, số hóa đơn} */
    private static BigDecimal[] sumByMethod(List<Object[]> rows) {
        BigDecimal cash = BigDecimal.ZERO;
        BigDecimal nonCash = BigDecimal.ZERO;
        long count = 0;
        for (Object[] row : rows) {
            BigDecimal amount = row[2] != null ? new BigDecimal(row[2].toString()) : BigDecimal.ZERO;
            if (row[0] == PaymentMethod.CASH) {
                cash = cash.add(amount);
            } else {
                nonCash = nonCash.add(amount);
            }
            count += ((Number) row[1]).longValue();
        }
        return new BigDecimal[]{cash, nonCash, BigDecimal.valueOf(count)};
    }

    /** Ca đã đóng dùng số đã chốt; ca đang mở tính tạm đến hiện tại */
    private Totals totalsFor(ShiftSession s) {
        if (s.getStatus() == ShiftStatus.CLOSED && s.getCashSales() != null) {
            return new Totals(s.getCashSales(), s.getNonCashSales(),
                    s.getInvoiceCount() != null ? s.getInvoiceCount() : 0,
                    s.getCashRefunds(), s.getNonCashRefunds(),
                    s.getRefundedCount() != null ? s.getRefundedCount() : 0);
        }
        return totalsOf(s);
    }

    private Map<Long, String> branchNames() {
        return branchRepository.findAll().stream().collect(Collectors.toMap(Branch::getId, Branch::getName));
    }

    private Map<Long, String> userNames(ShiftSession... sessions) {
        List<Long> ids = java.util.Arrays.stream(sessions).map(ShiftSession::getClosedBy).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, u -> u.getFullName() != null ? u.getFullName() : u.getUsername()));
    }

    private ShiftSessionResponse toResponse(ShiftSession s) {
        return toResponse(s, totalsFor(s), branchNames(), userNames(s));
    }

    private ShiftSessionResponse toResponse(ShiftSession s, Totals totals, Map<Long, String> branches, Map<Long, String> users) {
        WorkShift ws = s.getWorkShift();
        BigDecimal expected = s.getStatus() == ShiftStatus.CLOSED && s.getExpectedCash() != null
                ? s.getExpectedCash()
                : totals.expectedCash(s.getOpeningCash());
        return ShiftSessionResponse.builder()
                .id(s.getId())
                .branchId(s.getBranchId())
                .branchName(branches.get(s.getBranchId()))
                .workShiftId(ws.getId())
                .workShiftName(ws.getName())
                .workShiftTime(ws.getStartTime().format(HH_MM) + " - " + ws.getEndTime().format(HH_MM))
                .employeeId(s.getEmployeeId())
                .employeeName(s.getEmployeeName())
                .shiftDate(s.getShiftDate())
                .status(s.getStatus())
                .openedAt(s.getOpenedAt())
                .closedAt(s.getClosedAt())
                .closedBy(s.getClosedBy())
                .closedByName(s.getClosedBy() != null ? users.get(s.getClosedBy()) : null)
                .closedByOther(s.getClosedBy() != null && !s.getClosedBy().equals(s.getEmployeeId()))
                .openingCash(s.getOpeningCash())
                .openingNote(s.getOpeningNote())
                .cashSales(totals.cashSales)
                .nonCashSales(totals.nonCashSales)
                .invoiceCount(totals.invoiceCount)
                .refundedCount(totals.refundedCount)
                .cashRefunds(totals.cashRefunds)
                .nonCashRefunds(totals.nonCashRefunds)
                .expectedCash(expected)
                .closingCash(s.getClosingCash())
                .cashDifference(s.getCashDifference())
                .handoverNote(s.getHandoverNote())
                .build();
    }

    private static ShiftInvoiceRow toInvoiceRow(Invoice inv, Long shiftId) {
        boolean soldHere = shiftId.equals(inv.getShiftSessionId());
        boolean refunded = inv.getStatus() == InvoiceStatus.REFUNDED;
        boolean refundHere = refunded && (shiftId.equals(inv.getRefundShiftSessionId())
                || (inv.getRefundShiftSessionId() == null && soldHere));
        return ShiftInvoiceRow.builder()
                .id(inv.getId())
                .kind(soldHere ? ShiftInvoiceRow.Kind.SALE : ShiftInvoiceRow.Kind.REFUND_OTHER_SHIFT)
                .createdAt(inv.getCreatedAt())
                .paymentMethod(inv.getPaymentMethod())
                .status(inv.getStatus())
                .totalAmount(inv.getTotalAmount())
                .receivedAmount(inv.getReceivedAmount())
                .changeAmount(inv.getChangeAmount())
                .discount(inv.getDiscount())
                .itemCount(inv.getDetails() == null ? 0 : inv.getDetails().size())
                .customerName(inv.getCustomer() != null ? inv.getCustomer().getName() : null)
                .refundedAt(inv.getRefundedAt())
                .refundCountedHere(refundHere)
                .note(inv.getNote())
                .build();
    }

    private static String trimToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
