package com.pos.shift;

import com.pos.branch.BranchRepository;
import com.pos.common.BusinessException;
import com.pos.sale.InvoiceRepository;
import com.pos.sale.InvoiceStatus;
import com.pos.sale.PaymentMethod;
import com.pos.user.Role;
import com.pos.user.User;
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
import java.util.List;

/**
 * Quản lý ca làm việc: mở ca với tiền đầu ca, gắn hóa đơn vào ca đang mở,
 * đóng ca bằng số tiền mặt đếm được và tính chênh lệch so với số tiền lẽ ra phải có.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ShiftService {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final WorkShiftRepository workShiftRepository;
    private final ShiftSessionRepository shiftSessionRepository;
    private final InvoiceRepository invoiceRepository;
    private final BranchRepository branchRepository;

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
        if (req.getStartTime().equals(req.getEndTime())) {
            throw new BusinessException("INVALID_WORK_SHIFT", "Giờ bắt đầu và kết thúc không được trùng nhau");
        }
        shift.setName(req.getName().trim());
        shift.setStartTime(req.getStartTime());
        shift.setEndTime(req.getEndTime());
        if (req.getActive() != null) {
            shift.setActive(req.getActive());
        }
        return workShiftRepository.save(shift);
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
        if (actor.getBranchId() != null) {
            shiftSessionRepository.findFirstByBranchIdAndStatusOrderByClosedAtDesc(actor.getBranchId(), ShiftStatus.CLOSED)
                    .ifPresent(prev -> resp.previousShift(toResponse(prev)).suggestedOpeningCash(prev.getClosingCash()));
        }
        return resp.build();
    }

    @Transactional
    public ShiftSessionResponse openShift(OpenShiftRequest req, User actor) {
        if (shiftSessionRepository.findByEmployeeIdAndStatus(actor.getId(), ShiftStatus.OPEN).isPresent()) {
            throw new BusinessException("SHIFT_ALREADY_OPEN", "Bạn đang có một ca chưa đóng", HttpStatus.CONFLICT);
        }

        Long branchId = actor.getBranchId() != null ? actor.getBranchId() : req.getBranchId();
        if (branchId == null || !branchRepository.existsById(branchId)) {
            throw new BusinessException("BRANCH_REQUIRED", "Chưa chọn chi nhánh hợp lệ để mở ca");
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

    /** Quản lý đóng hộ ca của nhân viên quên đóng (MANAGER chỉ trong chi nhánh của mình) */
    @Transactional
    public ShiftSessionResponse closeShiftById(Long id, CloseShiftRequest req, User actor) {
        ShiftSession session = findVisible(id, actor);
        if (session.getStatus() != ShiftStatus.OPEN) {
            throw new BusinessException("SHIFT_ALREADY_CLOSED", "Ca này đã được đóng", HttpStatus.CONFLICT);
        }
        return close(session, req, actor);
    }

    private ShiftSessionResponse close(ShiftSession session, CloseShiftRequest req, User actor) {
        Totals totals = totalsOf(session);
        BigDecimal expected = session.getOpeningCash().add(totals.cashSales);

        session.setStatus(ShiftStatus.CLOSED);
        session.setOpenKey(null);
        session.setClosedAt(LocalDateTime.now());
        session.setClosedBy(actor.getId());
        session.setCashSales(totals.cashSales);
        session.setNonCashSales(totals.nonCashSales);
        session.setInvoiceCount(totals.invoiceCount);
        session.setExpectedCash(expected);
        session.setClosingCash(req.getClosingCash());
        session.setCashDifference(req.getClosingCash().subtract(expected));
        session.setHandoverNote(trimToNull(req.getHandoverNote()));
        shiftSessionRepository.save(session);

        log.info("{} đóng ca #{}: dự kiến {}, đếm được {}, chênh lệch {}", actor.getUsername(), session.getId(),
                expected, req.getClosingCash(), session.getCashDifference());
        return toResponse(session, totals);
    }

    public List<ShiftSessionResponse> listShifts(Long branchId, LocalDate from, LocalDate to, User actor) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end;
        if (start.isAfter(end)) {
            throw new BusinessException("INVALID_DATE_RANGE", "Ngày bắt đầu phải trước ngày kết thúc");
        }
        Long scopedBranch = actor.getRole() == Role.ADMIN ? branchId : actor.getBranchId();
        List<ShiftSession> sessions = scopedBranch != null
                ? shiftSessionRepository.findByBranchIdAndShiftDateBetweenOrderByOpenedAtDesc(scopedBranch, start, end)
                : shiftSessionRepository.findByShiftDateBetweenOrderByOpenedAtDesc(start, end);
        return sessions.stream().map(this::toResponse).toList();
    }

    public ShiftSessionResponse getShift(Long id, User actor) {
        ShiftSession session = shiftSessionRepository.findById(id)
                .orElseThrow(() -> new BusinessException("SHIFT_NOT_FOUND", "Không tìm thấy ca", HttpStatus.NOT_FOUND));
        boolean own = session.getEmployeeId().equals(actor.getId());
        if (!own) {
            checkManagerScope(session, actor);
        }
        return toResponse(session);
    }

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

    boolean isShiftRequired(User actor) {
        return requireOpenShift && actor.getRole() == Role.CASHIER;
    }

    // ---------- Nội bộ ----------

    private ShiftSession findVisible(Long id, User actor) {
        ShiftSession session = shiftSessionRepository.findById(id)
                .orElseThrow(() -> new BusinessException("SHIFT_NOT_FOUND", "Không tìm thấy ca", HttpStatus.NOT_FOUND));
        checkManagerScope(session, actor);
        return session;
    }

    private void checkManagerScope(ShiftSession session, User actor) {
        if (actor.getRole() == Role.ADMIN) return;
        if (actor.getRole() == Role.MANAGER && session.getBranchId().equals(actor.getBranchId())) return;
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

    private record Totals(BigDecimal cashSales, BigDecimal nonCashSales, int invoiceCount, int refundedCount) {}

    private Totals totalsOf(ShiftSession session) {
        BigDecimal cash = BigDecimal.ZERO;
        BigDecimal nonCash = BigDecimal.ZERO;
        int count = 0;
        for (Object[] row : invoiceRepository.sumPaidByShift(session.getId())) {
            BigDecimal amount = row[2] != null ? new BigDecimal(row[2].toString()) : BigDecimal.ZERO;
            if (row[0] == PaymentMethod.CASH) {
                cash = cash.add(amount);
            } else {
                nonCash = nonCash.add(amount);
            }
            count += ((Number) row[1]).intValue();
        }
        int refunded = (int) invoiceRepository.countByShiftSessionIdAndStatus(session.getId(), InvoiceStatus.REFUNDED);
        return new Totals(cash, nonCash, count, refunded);
    }

    private ShiftSessionResponse toResponse(ShiftSession s) {
        if (s.getStatus() == ShiftStatus.CLOSED && s.getCashSales() != null) {
            int refunded = (int) invoiceRepository.countByShiftSessionIdAndStatus(s.getId(), InvoiceStatus.REFUNDED);
            return toResponse(s, new Totals(s.getCashSales(), s.getNonCashSales(),
                    s.getInvoiceCount() != null ? s.getInvoiceCount() : 0, refunded));
        }
        return toResponse(s, totalsOf(s));
    }

    private ShiftSessionResponse toResponse(ShiftSession s, Totals totals) {
        WorkShift ws = s.getWorkShift();
        BigDecimal expected = s.getStatus() == ShiftStatus.CLOSED && s.getExpectedCash() != null
                ? s.getExpectedCash()
                : s.getOpeningCash().add(totals.cashSales);
        return ShiftSessionResponse.builder()
                .id(s.getId())
                .branchId(s.getBranchId())
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
                .openingCash(s.getOpeningCash())
                .openingNote(s.getOpeningNote())
                .cashSales(totals.cashSales)
                .nonCashSales(totals.nonCashSales)
                .invoiceCount(totals.invoiceCount)
                .refundedCount(totals.refundedCount)
                .expectedCash(expected)
                .closingCash(s.getClosingCash())
                .cashDifference(s.getCashDifference())
                .handoverNote(s.getHandoverNote())
                .build();
    }

    private static String trimToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
