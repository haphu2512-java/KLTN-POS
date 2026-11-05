package com.pos.purchaseorder;

import com.pos.audit.AuditAction;
import com.pos.audit.AuditService;
import com.pos.common.BusinessException;
import com.pos.forecast.ForecastResponse;
import com.pos.forecast.ForecastService;
import com.pos.product.Product;
import com.pos.product.ProductRepository;
import com.pos.purchase.StockIn;
import com.pos.purchase.StockInRepository;
import com.pos.purchase.StockInRequest;
import com.pos.purchase.StockInService;
import com.pos.purchase.Supplier;
import com.pos.purchase.SupplierRepository;
import com.pos.security.CurrentUserService;
import com.pos.user.Role;
import com.pos.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Đơn đặt hàng nhà cung cấp: khép kín vòng AI dự báo → đặt hàng → nhận hàng → tồn kho.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PurchaseOrderService {

    private final PurchaseOrderRepository repository;
    private final ForecastService forecastService;
    private final ProductRepository productRepository;
    private final SupplierRepository supplierRepository;
    private final StockInRepository stockInRepository;
    private final StockInService stockInService;
    private final CurrentUserService currentUserService;

    @Autowired(required = false)
    private AuditService auditService;

    public record LineRequest(Long productId, Integer orderedQty, BigDecimal unitCost) {}

    public record UpdateRequest(Long supplierId, String note, LocalDate expectedDate, List<LineRequest> lines) {}

    public record ReceiveLine(Long productId, Integer quantity, BigDecimal unitCost) {}

    public record ReceiveRequest(List<ReceiveLine> lines, String note, String clientRequestId) {}

    public record ReceiveResult(PurchaseOrder order, StockIn stockIn) {}

    public List<PurchaseOrder> list(Long requestedBranchId) {
        return repository.findRecent(currentUserService.branchForRead(requestedBranchId, true), PageRequest.of(0, 100));
    }

    public PurchaseOrder get(Long id) {
        PurchaseOrder o = find(id);
        checkBranch(o, currentUserService.getCurrentUser());
        return o;
    }

    /**
     * Lập đơn nháp từ đề xuất nhập hàng của AI (sản phẩm có suggestedRestockQty > 0) ở chi nhánh đang đăng nhập.
     * Mỗi nhà cung cấp một đơn, theo NCC của lần nhập gần nhất; sản phẩm chưa từng nhập từ NCC nào gom vào
     * một đơn chưa chọn NCC. Số lượng làm tròn lên theo đề xuất; đơn giá là giá vốn hiện tại.
     */
    @Transactional
    public List<PurchaseOrder> createFromForecast(int days) {
        User actor = currentUserService.getCurrentUser();
        Long branchId = currentUserService.currentBranchId();
        List<ForecastResponse> suggestions = forecastService.getCachedOrComputeForecast(branchId, days, "EXP_SMOOTH")
                .stream().filter(r -> r.getSuggestedRestockQty() != null && r.getSuggestedRestockQty() > 0).toList();
        if (suggestions.isEmpty()) {
            throw new BusinessException("NO_SUGGESTION", "AI chưa đề xuất nhập mặt hàng nào cho chi nhánh này");
        }

        Map<Long, Object[]> supplierOf = new HashMap<>();
        for (Object[] row : stockInRepository.recentSuppliersByProduct(branchId)) {
            supplierOf.putIfAbsent((Long) row[0], row);
        }

        Map<Long, PurchaseOrder> bySupplier = new LinkedHashMap<>();
        for (ForecastResponse s : suggestions) {
            Object[] sup = supplierOf.get(s.getProductId());
            Long supplierId = sup != null ? (Long) sup[1] : null;
            PurchaseOrder order = bySupplier.computeIfAbsent(supplierId == null ? -1L : supplierId, k -> PurchaseOrder.builder()
                    .branchId(branchId)
                    .supplierId(supplierId)
                    .supplierName(sup != null ? (String) sup[2] : null)
                    .source("FORECAST")
                    .note("Lập từ đề xuất AI dự báo " + days + " ngày")
                    .createdBy(actor.getId())
                    .createdAt(LocalDateTime.now())
                    .build());
            order.getLines().add(PurchaseOrderLine.builder()
                    .purchaseOrder(order)
                    .productId(s.getProductId())
                    .productName(s.getProductName())
                    .barcode(s.getBarcode())
                    .orderedQty(s.getSuggestedRestockQty())
                    .suggestedQty(s.getSuggestedRestockQty())
                    .unitCost(s.getCostPrice())
                    .forecastNote(truncate(forecastNote(s), 255))
                    .build());
        }
        List<PurchaseOrder> saved = repository.saveAll(bySupplier.values());
        audit(AuditAction.PURCHASE_ORDER, null, branchId, "Lập " + saved.size() + " đơn đặt hàng nháp từ đề xuất AI ("
                + suggestions.size() + " mặt hàng)");
        return saved;
    }

    @Transactional
    public PurchaseOrder createManual(UpdateRequest req) {
        User actor = currentUserService.getCurrentUser();
        PurchaseOrder o = PurchaseOrder.builder()
                .branchId(currentUserService.currentBranchId())
                .source("MANUAL")
                .createdBy(actor.getId())
                .createdAt(LocalDateTime.now())
                .build();
        applyUpdate(o, req);
        return repository.save(o);
    }

    /** Sửa đơn nháp: NCC, ghi chú, ngày dự kiến, danh sách mặt hàng (thay toàn bộ khi lines khác null) */
    @Transactional
    public PurchaseOrder update(Long id, UpdateRequest req) {
        PurchaseOrder o = find(id);
        checkBranch(o, currentUserService.getCurrentUser());
        if (o.getStatus() != PurchaseOrderStatus.DRAFT) {
            throw new BusinessException("PO_LOCKED", "Chỉ sửa được đơn nháp", HttpStatus.CONFLICT);
        }
        applyUpdate(o, req);
        return repository.save(o);
    }

    @Transactional
    public PurchaseOrder send(Long id) {
        PurchaseOrder o = find(id);
        checkBranch(o, currentUserService.getCurrentUser());
        if (o.getStatus() != PurchaseOrderStatus.DRAFT) {
            throw new BusinessException("PO_LOCKED", "Đơn đã gửi hoặc đã đóng", HttpStatus.CONFLICT);
        }
        if (o.getSupplierId() == null) {
            throw new BusinessException("PO_SUPPLIER_REQUIRED", "Chọn nhà cung cấp trước khi gửi đơn");
        }
        if (o.getLines().isEmpty()) {
            throw new BusinessException("PO_EMPTY", "Đơn chưa có mặt hàng nào");
        }
        o.setStatus(PurchaseOrderStatus.SENT);
        o.setSentAt(LocalDateTime.now());
        audit(AuditAction.PURCHASE_ORDER, o.getId(), o.getBranchId(), "Gửi đơn đặt hàng #" + o.getId() + " cho "
                + o.getSupplierName() + ": " + o.getLines().size() + " mặt hàng, " + AuditService.money(o.getTotalAmount()));
        return repository.save(o);
    }

    /**
     * Nhận hàng theo đơn (đủ hoặc một phần). Tạo phiếu nhập kho (cộng tồn, cập nhật giá vốn) cho đúng số nhận,
     * cập nhật số đã nhận từng dòng; nhận đủ mọi dòng thì đơn chuyển sang Đã nhận đủ.
     */
    @Transactional
    public ReceiveResult receive(Long id, ReceiveRequest req) {
        User actor = currentUserService.getCurrentUser();
        PurchaseOrder o = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException("PO_NOT_FOUND", "Không tìm thấy đơn đặt hàng", HttpStatus.NOT_FOUND));
        checkBranch(o, actor);
        if (o.getStatus() != PurchaseOrderStatus.SENT && o.getStatus() != PurchaseOrderStatus.PARTIALLY_RECEIVED) {
            throw new BusinessException("PO_NOT_RECEIVABLE", "Chỉ nhận hàng cho đơn đã gửi", HttpStatus.CONFLICT);
        }
        // Gửi lại cùng mã sau khi mất phản hồi: phiếu nhập đã tạo rồi, không cộng số nhận lần hai
        String requestId = req != null && StringUtils.hasText(req.clientRequestId()) ? req.clientRequestId().trim() : null;
        if (requestId != null) {
            StockIn existing = stockInRepository.findByClientRequestId(requestId).orElse(null);
            if (existing != null) return new ReceiveResult(o, existing);
        }
        Map<Long, PurchaseOrderLine> byProduct = new HashMap<>();
        o.getLines().forEach(l -> byProduct.put(l.getProductId(), l));

        List<ReceiveLine> lines = req != null && req.lines() != null && !req.lines().isEmpty()
                ? req.lines()
                : o.getLines().stream().map(l -> new ReceiveLine(l.getProductId(), l.getRemainingQty(), l.getUnitCost())).toList();

        List<StockInRequest.Item> items = new ArrayList<>();
        for (ReceiveLine r : lines) {
            if (r == null || r.quantity() == null || r.quantity() <= 0) continue;
            PurchaseOrderLine l = byProduct.get(r.productId());
            if (l == null) {
                throw new BusinessException("PO_INVALID_ITEM", "Mặt hàng không có trong đơn đặt");
            }
            if (r.quantity() > l.getRemainingQty()) {
                throw new BusinessException("PO_QTY_EXCEEDED", l.getProductName() + ": đơn chỉ còn " + l.getRemainingQty() + " chưa nhận");
            }
            BigDecimal cost = r.unitCost() != null ? r.unitCost() : l.getUnitCost();
            StockInRequest.Item item = new StockInRequest.Item();
            item.setProductId(r.productId());
            item.setQuantity(r.quantity());
            item.setUnitCost(cost);
            items.add(item);
            l.setReceivedQty(l.getReceivedQty() + r.quantity());
            if (cost != null) l.setUnitCost(cost);
        }
        if (items.isEmpty()) {
            throw new BusinessException("PO_RECEIVE_EMPTY", "Nhập số lượng nhận cho ít nhất một mặt hàng");
        }

        StockInRequest stockReq = new StockInRequest();
        stockReq.setBranchId(o.getBranchId());
        stockReq.setSupplierId(o.getSupplierId());
        stockReq.setEmployeeId(actor.getId());
        stockReq.setNote(truncate("Nhận hàng theo đơn đặt #" + o.getId()
                + (req != null && StringUtils.hasText(req.note()) ? " - " + req.note().trim() : ""), 255));
        stockReq.setClientRequestId(requestId);
        stockReq.setItems(items);
        StockIn stockIn = stockInService.createStockIn(stockReq);

        boolean all = o.getLines().stream().allMatch(l -> l.getRemainingQty() == 0);
        o.setStatus(all ? PurchaseOrderStatus.RECEIVED : PurchaseOrderStatus.PARTIALLY_RECEIVED);
        if (all) o.setClosedAt(LocalDateTime.now());
        PurchaseOrder saved = repository.save(o);
        audit(AuditAction.PURCHASE_ORDER, o.getId(), o.getBranchId(), "Nhận hàng đơn #" + o.getId() + " → phiếu nhập #"
                + stockIn.getId() + " (" + items.size() + " mặt hàng, " + (all ? "đủ đơn" : "một phần") + ")");
        return new ReceiveResult(saved, stockIn);
    }

    @Transactional
    public PurchaseOrder cancel(Long id) {
        PurchaseOrder o = find(id);
        checkBranch(o, currentUserService.getCurrentUser());
        if (o.getStatus() == PurchaseOrderStatus.RECEIVED || o.getStatus() == PurchaseOrderStatus.CANCELLED) {
            throw new BusinessException("PO_LOCKED", "Đơn đã đóng", HttpStatus.CONFLICT);
        }
        o.setStatus(PurchaseOrderStatus.CANCELLED);
        o.setClosedAt(LocalDateTime.now());
        audit(AuditAction.PURCHASE_ORDER, o.getId(), o.getBranchId(), "Hủy đơn đặt hàng #" + o.getId());
        return repository.save(o);
    }

    // ---------- nội bộ ----------

    private void applyUpdate(PurchaseOrder o, UpdateRequest req) {
        if (req == null) return;
        if (req.supplierId() != null) {
            Supplier s = supplierRepository.findById(req.supplierId())
                    .orElseThrow(() -> new BusinessException("SUPPLIER_NOT_FOUND", "Nhà cung cấp không tồn tại"));
            o.setSupplierId(s.getId());
            o.setSupplierName(s.getName());
        }
        if (req.note() != null) o.setNote(StringUtils.hasText(req.note()) ? truncate(req.note().trim(), 255) : null);
        if (req.expectedDate() != null) o.setExpectedDate(req.expectedDate());
        if (req.lines() != null) {
            Map<Long, PurchaseOrderLine> existing = new HashMap<>();
            o.getLines().forEach(l -> existing.put(l.getProductId(), l));
            List<PurchaseOrderLine> next = new ArrayList<>();
            for (LineRequest r : req.lines()) {
                if (r == null || r.productId() == null || r.orderedQty() == null || r.orderedQty() <= 0) continue;
                PurchaseOrderLine l = existing.get(r.productId());
                if (l == null) {
                    Product p = productRepository.findById(r.productId())
                            .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Sản phẩm không tồn tại"));
                    l = PurchaseOrderLine.builder().purchaseOrder(o).productId(p.getId()).productName(p.getName())
                            .barcode(p.getBarcode()).unitCost(p.getCostPrice()).build();
                }
                l.setOrderedQty(r.orderedQty());
                if (r.unitCost() != null) l.setUnitCost(r.unitCost());
                next.add(l);
            }
            o.getLines().clear();
            o.getLines().addAll(next);
        }
    }

    private static String forecastNote(ForecastResponse s) {
        StringBuilder b = new StringBuilder();
        b.append("Tồn ").append(s.getCurrentStock() != null ? s.getCurrentStock() : 0);
        if (s.getTotalPredictedDemand() != null) b.append(", cần ~").append(s.getTotalPredictedDemand()).append(" trong ").append(s.getForecastDays()).append(" ngày");
        if (s.getReorderPoint() != null) b.append(", ROP ").append(Math.round(s.getReorderPoint()));
        if (s.getUrgencyLabel() != null) b.append(" (").append(s.getUrgencyLabel()).append(")");
        return b.toString();
    }

    private PurchaseOrder find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException("PO_NOT_FOUND", "Không tìm thấy đơn đặt hàng", HttpStatus.NOT_FOUND));
    }

    private void checkBranch(PurchaseOrder o, User actor) {
        if (actor.getRole() == Role.ADMIN) return;
        if (!Objects.equals(o.getBranchId(), actor.getCurrentBranchId())) {
            throw new BusinessException("BRANCH_FORBIDDEN", "Đơn đặt hàng thuộc chi nhánh khác", HttpStatus.FORBIDDEN);
        }
    }

    private void audit(AuditAction action, Long id, Long branchId, String summary) {
        if (auditService != null) auditService.log(action, "PURCHASE_ORDER", id, branchId, summary);
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
