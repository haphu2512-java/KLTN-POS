package com.pos.shift;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pos.branch.Branch;
import com.pos.branch.BranchRepository;
import com.pos.common.BusinessException;
import com.pos.sale.Invoice;
import com.pos.sale.InvoiceRepository;
import com.pos.sale.InvoiceStatus;
import com.pos.sale.PaymentMethod;
import com.pos.staff.StaffMember;
import com.pos.staff.StaffMemberRepository;
import com.pos.staff.StaffService;
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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Quản lý ca làm việc. Mỗi chi nhánh có tối đa một ca mở (một két); quầy dùng tài khoản chung để mở ca,
 * còn ca thuộc về nhân viên (bảng staff_member) được quản lý xếp vào ca đó.
 * <p>
 * Tiền mặt dự kiến = tiền đầu ca + bán tiền mặt - hoàn trả tiền mặt - chi trong ca - nộp quỹ + bổ sung tiền lẻ.
 * VietQR và thẻ đối soát riêng: hệ thống tính = bán - hoàn, thu ngân nhập số trên sao kê / phiếu chốt máy POS.
 * <p>
 * Đóng ca: khớp tiền thì khóa sổ ngay (CLOSED); lệch quá ngưỡng thì phải ghi lý do và chờ quản lý duyệt.
 * <p>
 * Quyền: ADMIN mọi ca; các vai trò khác các ca ở chi nhánh đang đăng nhập. Đóng hộ, duyệt ca, mở ca cho người
 * không được xếp chỉ dành cho MANAGER/ADMIN.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ShiftService {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    /** Giới hạn khoảng ngày khi xem lịch sử ca để một lần tải không quá nặng */
    static final int MAX_RANGE_DAYS = 366;
    /** Mệnh giá tiền Việt được đếm khi chốt két */
    static final Set<Integer> DENOMINATIONS = Set.of(500000, 200000, 100000, 50000, 20000, 10000, 5000, 2000, 1000, 500);
    private static final List<ShiftStatus> FINISHED = List.of(ShiftStatus.PENDING_REVIEW, ShiftStatus.CLOSED);

    private final WorkShiftService workShiftService;
    private final ShiftSessionRepository shiftSessionRepository;
    private final ShiftCashMovementRepository cashMovementRepository;
    private final ShiftAssignmentRepository assignmentRepository;
    private final InvoiceRepository invoiceRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final StaffService staffService;
    private final StaffMemberRepository staffRepository;
    private final ObjectMapper objectMapper;

    /** true: thu ngân phải có ca mở ở chi nhánh mới bán được hàng */
    @Value("${pos.shift.require-open-shift:true}")
    private boolean requireOpenShift = true;

    /** true: chỉ mở được ca trong khung giờ của ca (thu ngân bị chặn, quản lý mở ngoài giờ phải ghi lý do) */
    @Value("${pos.shift.enforce-open-window:true}")
    private boolean enforceOpenWindow = true;

    @Value("${pos.shift.open-early-minutes:15}")
    private int openEarlyMinutes = 15;

    /** Chênh lệch lớn hơn mức này (tính theo trị tuyệt đối) phải ghi lý do và chờ duyệt */
    @Value("${pos.shift.difference-threshold:0}")
    private BigDecimal differenceThreshold = BigDecimal.ZERO;

    @Value("${pos.shift.overdue-hours:2}")
    private int overdueHours = 2;

    @Value("${pos.shift.standard-float:1000000}")
    private BigDecimal standardFloat = BigDecimal.valueOf(1_000_000);

    @Value("${pos.shift.drawer-limit:5000000}")
    private BigDecimal drawerLimit = BigDecimal.valueOf(5_000_000);

    // ---------- Ca cố định ----------

    /** Ca mở được lúc {@code time} (kể cả mở sớm), nếu giờ đó nằm ngoài mọi ca thì null */
    WorkShift workShiftAt(Long branchId, LocalTime time) {
        return workShiftService.activeShiftsOf(branchId).stream()
                .filter(ws -> canOpenAt(ws, time, openEarlyMinutes))
                .findFirst()
                .orElse(null);
    }

    /** Được mở ca từ {@code earlyMinutes} phút trước giờ bắt đầu cho tới giờ kết thúc ca */
    static boolean canOpenAt(WorkShift ws, LocalTime time, int earlyMinutes) {
        return ws.contains(time) || ws.contains(time.plusMinutes(earlyMinutes));
    }

    // ---------- Ca đang mở ----------

    public CurrentShiftResponse getCurrent(User actor) {
        Long branchId = actor.getCurrentBranchId();
        ShiftSession open = branchId == null ? null : openAt(branchId);
        CurrentShiftResponse.CurrentShiftResponseBuilder resp = CurrentShiftResponse.builder()
                .shiftRequired(isShiftRequired(actor))
                .differenceThreshold(differenceThreshold)
                .standardFloat(standardFloat)
                .drawerLimit(drawerLimit)
                .openEarlyMinutes(openEarlyMinutes);
        if (open != null) {
            return resp.session(toResponse(open)).build();
        }

        resp.suggestedOpeningCash(BigDecimal.ZERO);
        if (branchId == null) {
            return resp.build();
        }
        LocalDateTime now = LocalDateTime.now();
        WorkShift suggested = suggestWorkShift(branchId, now);
        if (suggested != null) {
            resp.suggestedWorkShiftId(suggested.getId());
            assignmentRepository.findByBranchIdAndWorkShiftIdAndShiftDate(branchId, suggested.getId(), shiftDateOf(suggested, now))
                    .ifPresent(a -> resp.assignedStaffId(a.getStaffMemberId()).assignedStaffName(staffName(a.getStaffMemberId())));
        }
        shiftSessionRepository.findFirstByBranchIdAndStatusInOrderByClosedAtDesc(branchId, FINISHED)
                .ifPresent(prev -> resp.previousShift(toResponse(prev))
                        .handoverExpected(leftInDrawerOf(prev))
                        .suggestedOpeningCash(leftInDrawerOf(prev)));
        return resp.build();
    }

    /**
     * Ca nên mở: ca đang trong khung giờ, bỏ qua ca hôm nay chi nhánh đã trực xong; nếu ca đó đã trực
     * thì lấy ca kế tiếp chưa trực.
     */
    WorkShift suggestWorkShift(Long branchId, LocalDateTime now) {
        List<WorkShift> active = workShiftService.activeShiftsOf(branchId);
        Set<String> done = shiftSessionRepository.findByBranchIdAndShiftDateBetween(branchId,
                        now.toLocalDate().minusDays(1), now.toLocalDate()).stream()
                .filter(s -> s.getStatus() != ShiftStatus.OPEN)
                .map(s -> s.getWorkShift().getId() + "@" + s.getShiftDate())
                .collect(Collectors.toSet());
        int start = -1;
        for (int i = 0; i < active.size(); i++) {
            if (canOpenAt(active.get(i), now.toLocalTime(), openEarlyMinutes)) {
                start = i;
                break;
            }
        }
        if (start < 0) return null;
        for (int i = start; i < active.size(); i++) {
            WorkShift ws = active.get(i);
            if (!done.contains(ws.getId() + "@" + shiftDateOf(ws, now))) {
                return ws;
            }
        }
        return null;
    }

    /** Mở ca tại chi nhánh của phiên đăng nhập (chọn lúc đăng nhập), không nhận chi nhánh từ client */
    @Transactional
    public ShiftSessionResponse openShift(OpenShiftRequest req, User actor) {
        Long branchId = actor.getCurrentBranchId();
        if (branchId == null || !branchRepository.existsById(branchId)) {
            throw new BusinessException("BRANCH_REQUIRED", "Phiên đăng nhập chưa chọn chi nhánh hợp lệ để mở ca");
        }
        ShiftSession already = openAt(branchId);
        if (already != null) {
            throw new BusinessException("SHIFT_ALREADY_OPEN", "Chi nhánh đang có " + already.getWorkShift().getName()
                    + " của " + already.getEmployeeName() + " chưa đóng. Đóng ca đó trước khi mở ca mới.", HttpStatus.CONFLICT);
        }

        boolean manager = isManager(actor);
        String overrideReason = trimToNull(req.getOverrideReason());
        LocalDateTime now = LocalDateTime.now();

        WorkShift workShift;
        if (req.getWorkShiftId() != null) {
            workShift = workShiftService.activeShiftsOf(branchId).stream()
                    .filter(ws -> ws.getId().equals(req.getWorkShiftId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("WORK_SHIFT_NOT_FOUND", "Ca làm việc không thuộc lịch ca của chi nhánh hoặc đã ngưng"));
        } else {
            workShift = workShiftAt(branchId, now.toLocalTime());
            if (workShift == null) {
                throw new BusinessException("OUTSIDE_WORK_SHIFTS", "Giờ hiện tại không thuộc ca nào, vui lòng chọn ca");
            }
        }
        boolean outsideWindow = enforceOpenWindow && !canOpenAt(workShift, now.toLocalTime(), openEarlyMinutes);
        if (outsideWindow) {
            String window = workShift.getStartTime().format(HH_MM) + " - " + workShift.getEndTime().format(HH_MM);
            if (!manager) {
                throw new BusinessException("OUTSIDE_SHIFT_WINDOW", workShift.getName() + " chỉ mở được trong khung giờ "
                        + window + " (sớm tối đa " + openEarlyMinutes + " phút). Nhờ quản lý mở nếu cần trực ngoài giờ.");
            }
            if (overrideReason == null) {
                throw new BusinessException("OVERRIDE_REASON_REQUIRED",
                        "Đang ngoài khung giờ " + window + " của " + workShift.getName() + ", ghi lý do mở ca (trực thay, tăng ca...)");
            }
        }

        LocalDate shiftDate = shiftDateOf(workShift, now);
        Long assigned = assignmentRepository.findByBranchIdAndWorkShiftIdAndShiftDate(branchId, workShift.getId(), shiftDate)
                .map(ShiftAssignment::getStaffMemberId).orElse(null);
        Long staffId = req.getStaffMemberId() != null ? req.getStaffMemberId() : assigned;
        if (staffId == null) {
            throw new BusinessException("SHIFT_NOT_ASSIGNED", workShift.getName() + " hôm nay chưa được xếp cho ai. "
                    + (manager ? "Chọn nhân viên trực và ghi lý do." : "Nhờ quản lý xếp ca hoặc mở ca trực thay."));
        }
        if (!staffId.equals(assigned)) {
            if (!manager) {
                throw new BusinessException("SHIFT_ASSIGNED_TO_OTHER",
                        "Ca này đã xếp cho " + staffName(assigned) + ". Chỉ quản lý được đổi người trực.", HttpStatus.FORBIDDEN);
            }
            if (overrideReason == null) {
                throw new BusinessException("OVERRIDE_REASON_REQUIRED",
                        assigned == null ? "Ca chưa xếp ai, ghi lý do khi chọn người trực"
                                : "Ca đã xếp cho " + staffName(assigned) + ", ghi lý do trực thay");
            }
        }
        StaffMember staff = staffService.activeIn(branchId, staffId);

        BigDecimal handoverExpected = shiftSessionRepository.findFirstByBranchIdAndStatusInOrderByClosedAtDesc(branchId, FINISHED)
                .map(ShiftService::leftInDrawerOf).orElse(null);
        BigDecimal handoverDifference = handoverExpected == null ? null : req.getOpeningCash().subtract(handoverExpected);
        String handoverReason = trimToNull(req.getHandoverReason());
        if (handoverDifference != null && handoverDifference.signum() != 0 && handoverReason == null) {
            throw new BusinessException("HANDOVER_REASON_REQUIRED", "Ca trước để lại " + handoverExpected.toPlainString()
                    + "đ nhưng két có " + req.getOpeningCash().toPlainString() + "đ. Ghi lý do chênh lệch khi nhận két.");
        }

        ShiftSession session = ShiftSession.builder()
                .branchId(branchId)
                .workShift(workShift)
                .employeeId(actor.getId())
                .staffMemberId(staff.getId())
                .employeeName(staff.getFullName())
                .shiftDate(shiftDate)
                .status(ShiftStatus.OPEN)
                .branchOpenKey(branchId)
                .openedAt(now)
                .openingCash(req.getOpeningCash())
                .openingNote(trimToNull(req.getNote()))
                .handoverExpected(handoverExpected)
                .handoverDifference(handoverDifference)
                .handoverReason(handoverDifference != null && handoverDifference.signum() != 0 ? handoverReason : null)
                .openOverrideReason(overrideReason != null && (outsideWindow || !staffId.equals(assigned)) ? overrideReason : null)
                .build();
        try {
            session = shiftSessionRepository.saveAndFlush(session);
        } catch (DataIntegrityViolationException e) {
            // Hai máy cùng mở ca: cột branch_open_key unique chặn bản thứ hai
            throw new BusinessException("SHIFT_ALREADY_OPEN", "Chi nhánh vừa có ca khác được mở", HttpStatus.CONFLICT);
        }
        log.info("{} mở {} cho {} tại chi nhánh {} với tiền đầu ca {}", actor.getUsername(), workShift.getName(),
                session.getEmployeeName(), branchId, req.getOpeningCash());
        return toResponse(session);
    }

    /** Đóng ca đang mở ở chi nhánh của phiên đăng nhập (tài khoản quầy hoặc quản lý) */
    @Transactional
    public ShiftSessionResponse closeCurrentShift(CloseShiftRequest req, User actor) {
        ShiftSession session = actor.getCurrentBranchId() == null ? null : openAt(actor.getCurrentBranchId());
        if (session == null) {
            throw new BusinessException("SHIFT_NOT_OPEN", "Chi nhánh chưa mở ca nào", HttpStatus.CONFLICT);
        }
        return close(session, req, actor);
    }

    /** Quản lý đóng hộ ca nhân viên quên đóng (MANAGER chỉ trong chi nhánh đang đăng nhập) */
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
        BigDecimal expectedVietqr = totals.vietqrSales.subtract(totals.vietqrRefunds);
        BigDecimal expectedCard = totals.cardSales.subtract(totals.cardRefunds);
        BigDecimal countedVietqr = req.getCountedVietqr() != null ? req.getCountedVietqr() : expectedVietqr;
        BigDecimal countedCard = req.getCountedCard() != null ? req.getCountedCard() : expectedCard;

        BigDecimal cashDiff = req.getClosingCash().subtract(expected);
        BigDecimal vietqrDiff = countedVietqr.subtract(expectedVietqr);
        BigDecimal cardDiff = countedCard.subtract(expectedCard);
        boolean needsReview = Stream.of(cashDiff, vietqrDiff, cardDiff).anyMatch(this::overThreshold);
        String differenceReason = trimToNull(req.getDifferenceReason());
        if (needsReview && differenceReason == null) {
            throw new BusinessException("DIFFERENCE_REASON_REQUIRED",
                    "Số đếm được lệch với số hệ thống, ghi lý do chênh lệch trước khi chốt ca");
        }

        String denominations = denominationsJson(req.getDenominations(), req.getClosingCash());
        BigDecimal left = req.getLeftInDrawer() != null ? req.getLeftInDrawer() : req.getClosingCash();
        if (left.compareTo(req.getClosingCash()) > 0) {
            throw new BusinessException("LEFT_EXCEEDS_COUNTED", "Tiền để lại cho ca sau không được lớn hơn tiền đếm được");
        }

        session.setStatus(needsReview ? ShiftStatus.PENDING_REVIEW : ShiftStatus.CLOSED);
        session.setOpenKey(null);
        session.setBranchOpenKey(null);
        session.setClosedAt(LocalDateTime.now());
        session.setClosedBy(actor.getId());
        session.setCashSales(totals.cashSales);
        session.setNonCashSales(totals.vietqrSales.add(totals.cardSales));
        session.setVietqrSales(totals.vietqrSales);
        session.setCardSales(totals.cardSales);
        session.setInvoiceCount(totals.invoiceCount);
        session.setCashRefunds(totals.cashRefunds);
        session.setNonCashRefunds(totals.vietqrRefunds.add(totals.cardRefunds));
        session.setVietqrRefunds(totals.vietqrRefunds);
        session.setCardRefunds(totals.cardRefunds);
        session.setRefundedCount(totals.refundedCount);
        session.setExpenses(totals.expenses);
        session.setDrops(totals.drops);
        session.setFloatIn(totals.floatIn);
        session.setExpectedCash(expected);
        session.setClosingCash(req.getClosingCash());
        session.setCashDifference(cashDiff);
        session.setCountedVietqr(countedVietqr);
        session.setCountedCard(countedCard);
        session.setVietqrDifference(vietqrDiff);
        session.setCardDifference(cardDiff);
        session.setDifferenceReason(needsReview ? differenceReason : null);
        session.setDenominations(denominations);
        session.setLeftInDrawer(left);
        session.setFinalDrop(req.getClosingCash().subtract(left));
        session.setHandoverNote(trimToNull(req.getHandoverNote()));
        shiftSessionRepository.save(session);

        log.info("{} đóng ca #{}: dự kiến {}, đếm được {}, chênh lệch TM {} / QR {} / thẻ {}", actor.getUsername(), session.getId(),
                expected, req.getClosingCash(), cashDiff, vietqrDiff, cardDiff);
        return toResponse(session, totals, branchNames(), userNames(session));
    }

    private boolean overThreshold(BigDecimal diff) {
        return diff.abs().compareTo(differenceThreshold) > 0;
    }

    private String denominationsJson(Map<Integer, Integer> counts, BigDecimal closingCash) {
        if (counts == null || counts.isEmpty()) return null;
        BigDecimal sum = BigDecimal.ZERO;
        for (Map.Entry<Integer, Integer> e : counts.entrySet()) {
            if (!DENOMINATIONS.contains(e.getKey()) || e.getValue() == null || e.getValue() < 0) {
                throw new BusinessException("INVALID_DENOMINATION", "Mệnh giá hoặc số tờ không hợp lệ: " + e.getKey());
            }
            sum = sum.add(BigDecimal.valueOf((long) e.getKey() * e.getValue()));
        }
        if (sum.compareTo(closingCash) != 0) {
            throw new BusinessException("DENOMINATION_MISMATCH", "Tổng theo mệnh giá (" + sum.toPlainString()
                    + "đ) khác tiền mặt đếm được (" + closingCash.toPlainString() + "đ)");
        }
        try {
            return objectMapper.writeValueAsString(new java.util.TreeMap<>(counts).descendingMap());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Quản lý duyệt ca lệch tiền: ghi cách xử lý, khóa sổ ca. Không tự duyệt ca mình trực (trừ ADMIN). */
    @Transactional
    public ShiftSessionResponse reviewShift(Long id, ReviewShiftRequest req, User actor) {
        ShiftSession session = find(id);
        checkManagerScope(session, actor);
        if (session.getStatus() != ShiftStatus.PENDING_REVIEW) {
            throw new BusinessException("SHIFT_NOT_PENDING_REVIEW", "Ca này không ở trạng thái chờ duyệt", HttpStatus.CONFLICT);
        }
        if (actor.getRole() != Role.ADMIN && session.getEmployeeId().equals(actor.getId())) {
            throw new BusinessException("SHIFT_FORBIDDEN", "Không tự duyệt ca do chính tài khoản mình mở", HttpStatus.FORBIDDEN);
        }
        session.setStatus(ShiftStatus.CLOSED);
        session.setReviewedBy(actor.getId());
        session.setReviewedAt(LocalDateTime.now());
        session.setReviewNote(req.getNote().trim());
        shiftSessionRepository.save(session);
        log.info("{} duyệt ca #{}: {}", actor.getUsername(), id, session.getReviewNote());
        return toResponse(session);
    }

    // ---------- Thu/chi két ----------

    public List<CashMovementResponse> getCurrentCashMovements(User actor) {
        ShiftSession session = requireOpenAtBranch(actor);
        return cashMovementRepository.findByShiftSessionIdOrderByCreatedAtDesc(session.getId()).stream()
                .map(CashMovementResponse::of).toList();
    }

    public List<CashMovementResponse> getCashMovements(Long shiftId, User actor) {
        checkCanView(find(shiftId), actor);
        return cashMovementRepository.findByShiftSessionIdOrderByCreatedAtDesc(shiftId).stream()
                .map(CashMovementResponse::of).toList();
    }

    @Transactional
    public CashMovementResponse addCashMovement(CashMovementRequest req, User actor) {
        ShiftSession session = requireOpenAtBranch(actor);
        validateMovement(req);
        ShiftCashMovement m = ShiftCashMovement.builder()
                .shiftSessionId(session.getId())
                .createdBy(actor.getId())
                .createdByName(displayName(actor))
                .createdAt(LocalDateTime.now())
                .build();
        applyMovement(m, req);
        m = cashMovementRepository.save(m);
        log.info("{} ghi {} {} vào ca #{}: {}", actor.getUsername(), m.getType(), m.getAmount(), session.getId(), m.getReason());
        return CashMovementResponse.of(m);
    }

    @Transactional
    public CashMovementResponse updateCashMovement(Long id, CashMovementRequest req, User actor) {
        ShiftCashMovement m = editableMovement(id, actor);
        validateMovement(req);
        applyMovement(m, req);
        m.setUpdatedAt(LocalDateTime.now());
        return CashMovementResponse.of(cashMovementRepository.save(m));
    }

    @Transactional
    public void deleteCashMovement(Long id, User actor) {
        ShiftCashMovement m = editableMovement(id, actor);
        m.setDeletedAt(LocalDateTime.now());
        m.setDeletedBy(actor.getId());
        cashMovementRepository.save(m);
        log.info("{} xóa khoản {} {} của ca #{}", actor.getUsername(), m.getType(), m.getAmount(), m.getShiftSessionId());
    }

    private ShiftCashMovement editableMovement(Long id, User actor) {
        ShiftCashMovement m = cashMovementRepository.findById(id)
                .filter(x -> x.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException("CASH_MOVEMENT_NOT_FOUND", "Không tìm thấy khoản thu/chi", HttpStatus.NOT_FOUND));
        ShiftSession session = find(m.getShiftSessionId());
        checkCanView(session, actor);
        if (session.getStatus() != ShiftStatus.OPEN) {
            throw new BusinessException("SHIFT_NOT_OPEN", "Ca đã chốt, không sửa được khoản thu/chi", HttpStatus.CONFLICT);
        }
        return m;
    }

    private static void validateMovement(CashMovementRequest req) {
        if (req.getType() != CashMovementType.EXPENSE && trimToNull(req.getCounterpart()) == null) {
            throw new BusinessException("COUNTERPART_REQUIRED", req.getType() == CashMovementType.DROP
                    ? "Ghi tên người nhận tiền nộp quỹ" : "Ghi tên người đưa tiền lẻ");
        }
    }

    private static void applyMovement(ShiftCashMovement m, CashMovementRequest req) {
        m.setType(req.getType());
        m.setCategory(req.getType() == CashMovementType.EXPENSE ? trimToNull(req.getCategory()) : null);
        m.setAmount(req.getAmount());
        m.setReason(req.getReason().trim());
        m.setCounterpart(trimToNull(req.getCounterpart()));
    }

    // ---------- Xem lịch sử ca ----------

    /**
     * Lịch sử ca theo bộ lọc (mặc định hôm nay). ADMIN lọc tự do; các vai trò khác bị giới hạn ở chi nhánh
     * đang đăng nhập.
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

        Long branchId = actor.getRole() == Role.ADMIN ? filter.branchId() : actor.getCurrentBranchId();
        List<ShiftSession> sessions = shiftSessionRepository.search(start, end, branchId, filter.staffId(), filter.status());
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
                .pendingReviewShifts((int) rows.stream().filter(r -> r.getStatus() == ShiftStatus.PENDING_REVIEW).count())
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
     * Ca dùng để gắn hóa đơn mới: ca đang mở của chi nhánh bán. Thu ngân bắt buộc có ca mở;
     * các vai trò khác bán được khi chi nhánh chưa mở ca (hóa đơn không gắn ca).
     */
    public Long resolveShiftForSale(User actor, Long branchId) {
        ShiftSession open = branchId == null ? null : openAt(branchId);
        if (open != null) {
            return open.getId();
        }
        if (isShiftRequired(actor)) {
            throw new BusinessException("SHIFT_NOT_OPEN", "Vui lòng mở ca trước khi bán hàng", HttpStatus.CONFLICT);
        }
        return null;
    }

    /**
     * Ca có két chi tiền hoàn trả: ca đang mở ở chi nhánh của hóa đơn. Chi nhánh chưa mở ca thì null,
     * khi đó tiền hoàn trả tính vào ca đã bán hóa đơn.
     */
    public Long resolveShiftForRefund(User actor, Long invoiceBranchId) {
        ShiftSession open = openAt(invoiceBranchId);
        return open != null ? open.getId() : null;
    }

    boolean isShiftRequired(User actor) {
        return requireOpenShift && actor.getRole() == Role.CASHIER;
    }

    // ---------- Nội bộ ----------

    private ShiftSession openAt(Long branchId) {
        return shiftSessionRepository.findByBranchIdAndStatus(branchId, ShiftStatus.OPEN).stream()
                .findFirst().orElse(null);
    }

    private ShiftSession requireOpenAtBranch(User actor) {
        ShiftSession session = actor.getCurrentBranchId() == null ? null : openAt(actor.getCurrentBranchId());
        if (session == null) {
            throw new BusinessException("SHIFT_NOT_OPEN", "Chi nhánh chưa mở ca nào", HttpStatus.CONFLICT);
        }
        return session;
    }

    private ShiftSession find(Long id) {
        return shiftSessionRepository.findById(id)
                .orElseThrow(() -> new BusinessException("SHIFT_NOT_FOUND", "Không tìm thấy ca", HttpStatus.NOT_FOUND));
    }

    private static boolean isManager(User actor) {
        return actor.getRole() == Role.ADMIN || actor.getRole() == Role.MANAGER;
    }

    /** Xem ca: ADMIN mọi ca; MANAGER các ca ở chi nhánh đang đăng nhập; vai trò khác chỉ ca do chính mình mở */
    private void checkCanView(ShiftSession session, User actor) {
        if (actor.getRole() == Role.ADMIN) return;
        if (actor.getRole() == Role.MANAGER && session.getBranchId().equals(actor.getCurrentBranchId())) return;
        if (session.getEmployeeId().equals(actor.getId())) return;
        throw new BusinessException("SHIFT_FORBIDDEN", "Bạn không có quyền với ca này", HttpStatus.FORBIDDEN);
    }

    private void checkManagerScope(ShiftSession session, User actor) {
        if (actor.getRole() == Role.ADMIN) return;
        if (actor.getRole() == Role.MANAGER && session.getBranchId().equals(actor.getCurrentBranchId())) return;
        throw new BusinessException("SHIFT_FORBIDDEN", "Bạn không có quyền với ca này", HttpStatus.FORBIDDEN);
    }

    private String staffName(Long staffId) {
        if (staffId == null) return null;
        return staffRepository.findById(staffId).map(StaffMember::getFullName).orElse("#" + staffId);
    }

    /** Ca shiftId do chính actor mở (dùng cho quyền xem hóa đơn trong ca của thu ngân) */
    public boolean isOwnShift(Long shiftId, User actor) {
        if (shiftId == null) return false;
        return shiftSessionRepository.findById(shiftId)
                .map(s -> actor.getId().equals(s.getEmployeeId()))
                .orElse(false);
    }

    public String userDisplayName(Long userId) {
        if (userId == null) return null;
        return userRepository.findById(userId).map(ShiftService::displayName).orElse(null);
    }

    /** Tên hiển thị của nhiều tài khoản trong một truy vấn (id không tồn tại thì không có trong map) */
    public Map<Long, String> userDisplayNames(Collection<Long> userIds) {
        List<Long> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        return userRepository.findAllById(ids).stream().collect(Collectors.toMap(User::getId, ShiftService::displayName));
    }

    static String displayName(User u) {
        return u.getFullName() != null && !u.getFullName().isBlank() ? u.getFullName() : u.getUsername();
    }

    /** Tiền ca để lại trong két cho ca sau; ca đóng trước khi có cột này coi như để lại toàn bộ tiền đếm được */
    private static BigDecimal leftInDrawerOf(ShiftSession s) {
        return s.getLeftInDrawer() != null ? s.getLeftInDrawer() : s.getClosingCash();
    }

    /** Ca qua đêm mở sau nửa đêm (vd. 02:00 của ca 22:00 - 06:00) vẫn tính cho ngày bắt đầu ca */
    static LocalDate shiftDateOf(WorkShift ws, LocalDateTime openedAt) {
        boolean overnight = !ws.getStartTime().isBefore(ws.getEndTime());
        if (overnight && openedAt.toLocalTime().isBefore(ws.getEndTime())) {
            return openedAt.toLocalDate().minusDays(1);
        }
        return openedAt.toLocalDate();
    }

    /** Giờ kết thúc theo lịch của ca (ca qua đêm kết thúc vào ngày hôm sau) */
    static LocalDateTime scheduledEnd(WorkShift ws, LocalDate shiftDate) {
        boolean overnight = !ws.getStartTime().isBefore(ws.getEndTime());
        return shiftDate.plusDays(overnight ? 1 : 0).atTime(ws.getEndTime());
    }

    record Totals(BigDecimal cashSales, BigDecimal vietqrSales, BigDecimal cardSales, int invoiceCount,
                  BigDecimal cashRefunds, BigDecimal vietqrRefunds, BigDecimal cardRefunds, int refundedCount,
                  BigDecimal expenses, BigDecimal drops, BigDecimal floatIn) {

        BigDecimal expectedCash(BigDecimal openingCash) {
            return openingCash.add(cashSales).subtract(nz(cashRefunds))
                    .subtract(nz(expenses)).subtract(nz(drops)).add(nz(floatIn));
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private Totals totalsOf(ShiftSession session) {
        Map<PaymentMethod, BigDecimal[]> sold = byMethod(invoiceRepository.sumSoldByShift(session.getId()));
        Map<PaymentMethod, BigDecimal[]> refunded = byMethod(invoiceRepository.sumRefundedByShift(session.getId()));
        Map<CashMovementType, BigDecimal> moves = new java.util.EnumMap<>(CashMovementType.class);
        for (Object[] row : cashMovementRepository.sumByType(session.getId())) {
            moves.put((CashMovementType) row[0], row[1] != null ? new BigDecimal(row[1].toString()) : BigDecimal.ZERO);
        }
        return new Totals(
                amount(sold, PaymentMethod.CASH), amount(sold, PaymentMethod.VIETQR), amount(sold, PaymentMethod.CARD), count(sold),
                amount(refunded, PaymentMethod.CASH), amount(refunded, PaymentMethod.VIETQR), amount(refunded, PaymentMethod.CARD), count(refunded),
                moves.getOrDefault(CashMovementType.EXPENSE, BigDecimal.ZERO),
                moves.getOrDefault(CashMovementType.DROP, BigDecimal.ZERO),
                moves.getOrDefault(CashMovementType.FLOAT_IN, BigDecimal.ZERO));
    }

    /** Dòng [phương thức, số hóa đơn, tổng tiền] → phương thức → {tổng tiền, số hóa đơn} */
    private static Map<PaymentMethod, BigDecimal[]> byMethod(List<Object[]> rows) {
        Map<PaymentMethod, BigDecimal[]> out = new java.util.EnumMap<>(PaymentMethod.class);
        for (Object[] row : rows) {
            BigDecimal amount = row[2] != null ? new BigDecimal(row[2].toString()) : BigDecimal.ZERO;
            out.put((PaymentMethod) row[0], new BigDecimal[]{amount, BigDecimal.valueOf(((Number) row[1]).longValue())});
        }
        return out;
    }

    private static BigDecimal amount(Map<PaymentMethod, BigDecimal[]> m, PaymentMethod method) {
        BigDecimal[] v = m.get(method);
        return v != null ? v[0] : BigDecimal.ZERO;
    }

    private static int count(Map<PaymentMethod, BigDecimal[]> m) {
        return m.values().stream().mapToInt(v -> v[1].intValue()).sum();
    }

    /** Ca đã chốt dùng số đã chốt; ca đang mở tính tạm đến hiện tại */
    private Totals totalsFor(ShiftSession s) {
        if (s.getStatus() != ShiftStatus.OPEN && s.getCashSales() != null) {
            // Ca đóng trước khi tách VietQR / thẻ: dồn phần không tiền mặt vào VietQR để tổng vẫn đúng
            boolean legacy = s.getVietqrSales() == null;
            return new Totals(s.getCashSales(),
                    legacy ? nz(s.getNonCashSales()) : s.getVietqrSales(),
                    legacy ? BigDecimal.ZERO : nz(s.getCardSales()),
                    s.getInvoiceCount() != null ? s.getInvoiceCount() : 0,
                    s.getCashRefunds(),
                    legacy ? nz(s.getNonCashRefunds()) : nz(s.getVietqrRefunds()),
                    legacy ? BigDecimal.ZERO : nz(s.getCardRefunds()),
                    s.getRefundedCount() != null ? s.getRefundedCount() : 0,
                    nz(s.getExpenses()), nz(s.getDrops()), nz(s.getFloatIn()));
        }
        return totalsOf(s);
    }

    private Map<Long, String> branchNames() {
        return branchRepository.findAll().stream().collect(Collectors.toMap(Branch::getId, Branch::getName));
    }

    private Map<Long, String> userNames(ShiftSession... sessions) {
        List<Long> ids = java.util.Arrays.stream(sessions)
                .flatMap(s -> Stream.of(s.getEmployeeId(), s.getClosedBy(), s.getReviewedBy()))
                .filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        return userRepository.findAllById(ids).stream().collect(Collectors.toMap(User::getId, ShiftService::displayName));
    }

    private ShiftSessionResponse toResponse(ShiftSession s) {
        return toResponse(s, totalsFor(s), branchNames(), userNames(s));
    }

    private ShiftSessionResponse toResponse(ShiftSession s, Totals totals, Map<Long, String> branches, Map<Long, String> users) {
        WorkShift ws = s.getWorkShift();
        boolean open = s.getStatus() == ShiftStatus.OPEN;
        BigDecimal expected = !open && s.getExpectedCash() != null
                ? s.getExpectedCash()
                : totals.expectedCash(s.getOpeningCash());
        boolean overdue = open && LocalDateTime.now().isAfter(scheduledEnd(ws, s.getShiftDate()).plusHours(overdueHours));
        return ShiftSessionResponse.builder()
                .id(s.getId())
                .branchId(s.getBranchId())
                .branchName(branches.get(s.getBranchId()))
                .workShiftId(ws.getId())
                .workShiftName(ws.getName())
                .workShiftTime(ws.getStartTime().format(HH_MM) + " - " + ws.getEndTime().format(HH_MM))
                .employeeId(s.getEmployeeId())
                .staffMemberId(s.getStaffMemberId())
                .employeeName(s.getEmployeeName())
                .openedBy(s.getEmployeeId())
                .openedByName(users.get(s.getEmployeeId()))
                .shiftDate(s.getShiftDate())
                .status(s.getStatus())
                .overdue(overdue)
                .openedAt(s.getOpenedAt())
                .closedAt(s.getClosedAt())
                .closedBy(s.getClosedBy())
                .closedByName(s.getClosedBy() != null ? users.get(s.getClosedBy()) : null)
                .closedByOther(s.getClosedBy() != null && !s.getClosedBy().equals(s.getEmployeeId()))
                .openingCash(s.getOpeningCash())
                .openingNote(s.getOpeningNote())
                .handoverExpected(s.getHandoverExpected())
                .handoverDifference(s.getHandoverDifference())
                .handoverReason(s.getHandoverReason())
                .openOverrideReason(s.getOpenOverrideReason())
                .cashSales(totals.cashSales)
                .nonCashSales(totals.vietqrSales.add(totals.cardSales))
                .vietqrSales(totals.vietqrSales)
                .cardSales(totals.cardSales)
                .invoiceCount(totals.invoiceCount)
                .refundedCount(totals.refundedCount)
                .cashRefunds(totals.cashRefunds)
                .nonCashRefunds(totals.cashRefunds == null ? s.getNonCashRefunds() : totals.vietqrRefunds.add(totals.cardRefunds))
                .vietqrRefunds(totals.vietqrRefunds)
                .cardRefunds(totals.cardRefunds)
                .expenses(totals.expenses)
                .drops(totals.drops)
                .floatIn(totals.floatIn)
                .expectedCash(expected)
                .expectedVietqr(totals.vietqrSales.subtract(totals.vietqrRefunds))
                .expectedCard(totals.cardSales.subtract(totals.cardRefunds))
                .closingCash(s.getClosingCash())
                .cashDifference(s.getCashDifference())
                .countedVietqr(s.getCountedVietqr())
                .countedCard(s.getCountedCard())
                .vietqrDifference(s.getVietqrDifference())
                .cardDifference(s.getCardDifference())
                .differenceReason(s.getDifferenceReason())
                .denominations(parseDenominations(s.getDenominations()))
                .leftInDrawer(s.getLeftInDrawer())
                .finalDrop(s.getFinalDrop())
                .handoverNote(s.getHandoverNote())
                .reviewedBy(s.getReviewedBy())
                .reviewedByName(s.getReviewedBy() != null ? users.get(s.getReviewedBy()) : null)
                .reviewedAt(s.getReviewedAt())
                .reviewNote(s.getReviewNote())
                .build();
    }

    private Map<Integer, Integer> parseDenominations(String json) {
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<java.util.LinkedHashMap<Integer, Integer>>() {});
        } catch (JsonProcessingException e) {
            return null;
        }
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

    static String trimToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
