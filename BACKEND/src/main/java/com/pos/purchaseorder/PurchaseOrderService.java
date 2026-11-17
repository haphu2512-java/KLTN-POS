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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

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

    public record ReceiveLine(Long productId, Integer quantity, BigDecimal unitCost, String lotNumber, LocalDate expiryDate) {}

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
     * Mỗi mặt hàng chỉ vào đơn của NCC có bán danh mục của nó (Supplier.categories). Nhiều NCC cùng bán thì ưu tiên
     * NCC của lần nhập gần nhất, sau đó NCC đã có đơn trong đợt này (để ít đơn hơn), cuối cùng NCC có id nhỏ nhất.
     * Mặt hàng không NCC nào bán danh mục đó gom vào một đơn chưa chọn NCC, ghi chú nêu các danh mục cần gán.
     * Số lượng làm tròn lên theo đề xuất; đơn giá là giá vốn hiện tại.
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

        Map<Long, Long> recentSupplierOf = new HashMap<>();
        for (Object[] row : stockInRepository.recentSuppliersByProduct(branchId)) {
            recentSupplierOf.putIfAbsent((Long) row[0], (Long) row[1]);
        }
        Map<String, List<Supplier>> suppliersByCategory = suppliersByCategory();

        String baseNote = "Lập từ đề xuất AI dự báo " + days + " ngày";
        Map<Long, PurchaseOrder> bySupplier = new LinkedHashMap<>();
        Set<String> unmatchedCategories = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (ForecastResponse s : suggestions) {
            String category = StringUtils.hasText(s.getCategory()) ? s.getCategory().trim() : null;
            Supplier sup = pickSupplier(suppliersByCategory.getOrDefault(categoryKey(category), List.of()),
                    recentSupplierOf.get(s.getProductId()), bySupplier.keySet());
            if (sup == null) unmatchedCategories.add(category != null ? category : "(sản phẩm chưa có danh mục)");
            PurchaseOrder order = bySupplier.computeIfAbsent(sup == null ? -1L : sup.getId(), k -> PurchaseOrder.builder()
                    .branchId(branchId)
                    .supplierId(sup != null ? sup.getId() : null)
                    .supplierName(sup != null ? sup.getName() : null)
                    .source("FORECAST")
                    .note(baseNote)
                    .createdBy(actor.getId())
                    .createdAt(LocalDateTime.now())
                    .build());
            order.getLines().add(PurchaseOrderLine.builder()
                    .purchaseOrder(order)
                    .productId(s.getProductId())
                    .productName(s.getProductName())
                    .barcode(s.getBarcode())
                    .category(truncate(category, 100))
                    .orderedQty(s.getSuggestedRestockQty())
                    .suggestedQty(s.getSuggestedRestockQty())
                    .unitCost(s.getCostPrice())
                    .forecastNote(truncate(forecastNote(s), 255))
                    .build());
        }
        PurchaseOrder unassigned = bySupplier.get(-1L);
        if (unassigned != null) {
            unassigned.setNote(truncate(baseNote + ". Chưa có NCC nào bán danh mục: " + String.join(", ", unmatchedCategories)
                    + ". Gán danh mục cho NCC ở trang Đối tác rồi lập lại, hoặc chọn NCC cho đơn này.", 255));
        }
        List<PurchaseOrder> saved = repository.saveAll(bySupplier.values());
        audit(AuditAction.PURCHASE_ORDER, null, branchId, "Lập " + saved.size() + " đơn đặt hàng nháp từ đề xuất AI ("
                + suggestions.size() + " mặt hàng"
                + (unassigned != null ? ", " + unassigned.getLines().size() + " mặt hàng chưa có NCC theo danh mục" : "") + ")");
        return saved;
    }

    /** Chọn NCC trong số NCC bán danh mục: NCC nhập gần nhất → NCC đã có đơn trong đợt → NCC đầu tiên; không có thì null */
    static Supplier pickSupplier(List<Supplier> candidates, Long recentSupplierId, Set<Long> alreadyUsed) {
        if (candidates.isEmpty()) return null;
        if (recentSupplierId != null) {
            for (Supplier c : candidates) if (c.getId().equals(recentSupplierId)) return c;
        }
        for (Supplier c : candidates) if (alreadyUsed.contains(c.getId())) return c;
        return candidates.get(0);
    }

    private static String categoryKey(String category) {
        return category == null ? "" : category.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Tách một đơn nháp có nhiều danh mục thành nhiều đơn nháp, mỗi đơn một danh mục, để người nhập không nhầm.
     * Đơn gốc giữ nhóm danh mục mà NCC đang chọn có bán (không có thì nhóm đầu tiên); mỗi nhóm được gán NCC bán
     * danh mục đó theo cùng quy tắc lập đơn từ AI, không ai bán thì để trống NCC. Trả về đơn gốc trước, các đơn mới sau.
     */
    @Transactional
    public List<PurchaseOrder> splitByCategory(Long id) {
        User actor = currentUserService.getCurrentUser();
        PurchaseOrder o = find(id);
        checkBranch(o, actor);
        if (o.getStatus() != PurchaseOrderStatus.DRAFT) {
            throw new BusinessException("PO_LOCKED", "Chỉ tách được đơn nháp", HttpStatus.CONFLICT);
        }
        // Dòng cũ chưa lưu danh mục: lấy theo sản phẩm
        for (PurchaseOrderLine l : o.getLines()) {
            if (!StringUtils.hasText(l.getCategory())) {
                productRepository.findById(l.getProductId())
                        .ifPresent(p -> l.setCategory(truncate(StringUtils.hasText(p.getCategory()) ? p.getCategory().trim() : null, 100)));
            }
        }
        Map<String, List<PurchaseOrderLine>> groups = new LinkedHashMap<>();
        Map<String, String> labelOf = new HashMap<>();
        for (PurchaseOrderLine l : o.getLines()) {
            String key = categoryKey(l.getCategory());
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(l);
            labelOf.putIfAbsent(key, StringUtils.hasText(l.getCategory()) ? l.getCategory().trim() : "Chưa có danh mục");
        }
        if (groups.size() < 2) {
            throw new BusinessException("PO_SINGLE_CATEGORY", "Đơn chỉ có một danh mục, không cần tách");
        }

        Map<String, List<Supplier>> suppliersByCategory = suppliersByCategory();
        Map<Long, Long> recentSupplierOf = new HashMap<>();
        for (Object[] row : stockInRepository.recentSuppliersByProduct(o.getBranchId())) {
            recentSupplierOf.putIfAbsent((Long) row[0], (Long) row[1]);
        }
        Supplier current = o.getSupplierId() == null ? null : supplierRepository.findById(o.getSupplierId()).orElse(null);
        String keepKey = groups.keySet().iterator().next();
        if (current != null) {
            for (String k : groups.keySet()) {
                if (current.getCategories().stream().anyMatch(c -> categoryKey(c).equals(k))) { keepKey = k; break; }
            }
        }

        List<PurchaseOrder> result = new ArrayList<>();
        Set<Long> used = new java.util.HashSet<>();
        if (current != null) used.add(current.getId());
        for (Map.Entry<String, List<PurchaseOrderLine>> g : groups.entrySet()) {
            List<Supplier> candidates = suppliersByCategory.getOrDefault(g.getKey(), List.of());
            Supplier sup = null;
            if (current != null && candidates.stream().anyMatch(c -> c.getId().equals(current.getId()))) {
                sup = current;
            } else {
                Long recent = g.getValue().stream().map(l -> recentSupplierOf.get(l.getProductId()))
                        .filter(r -> r != null && candidates.stream().anyMatch(c -> c.getId().equals(r)))
                        .findFirst().orElse(null);
                sup = pickSupplier(candidates, recent, used);
            }
            if (sup != null) used.add(sup.getId());
            boolean keep = g.getKey().equals(keepKey);
            String note = truncate("Danh mục " + labelOf.get(g.getKey())
                    + (keep ? ", các danh mục khác đã tách sang đơn mới" : ", tách từ đơn #" + o.getId())
                    + (sup == null ? ". Chưa có NCC bán danh mục này" : ""), 255);
            if (keep) {
                o.getLines().retainAll(g.getValue());
                o.setSupplierId(sup != null ? sup.getId() : null);
                o.setSupplierName(sup != null ? sup.getName() : null);
                o.setNote(note);
                result.add(0, o);
            } else {
                PurchaseOrder n = PurchaseOrder.builder()
                        .branchId(o.getBranchId())
                        .supplierId(sup != null ? sup.getId() : null)
                        .supplierName(sup != null ? sup.getName() : null)
                        .source(o.getSource())
                        .note(note)
                        .expectedDate(o.getExpectedDate())
                        .createdBy(actor.getId())
                        .createdAt(LocalDateTime.now())
                        .build();
                for (PurchaseOrderLine l : g.getValue()) {
                    n.getLines().add(PurchaseOrderLine.builder()
                            .purchaseOrder(n)
                            .productId(l.getProductId())
                            .productName(l.getProductName())
                            .barcode(l.getBarcode())
                            .category(l.getCategory())
                            .orderedQty(l.getOrderedQty())
                            .suggestedQty(l.getSuggestedQty())
                            .unitCost(l.getUnitCost())
                            .forecastNote(l.getForecastNote())
                            .build());
                }
                result.add(n);
            }
        }
        List<PurchaseOrder> saved = repository.saveAll(result);
        audit(AuditAction.PURCHASE_ORDER, o.getId(), o.getBranchId(), "Tách đơn đặt hàng #" + o.getId()
                + " thành " + saved.size() + " đơn theo danh mục");
        return saved;
    }

    private Map<String, List<Supplier>> suppliersByCategory() {
        Map<String, List<Supplier>> m = new HashMap<>();
        supplierRepository.findAll().stream()
                .sorted(Comparator.comparing(Supplier::getId))
                .forEach(sup -> sup.getCategories().forEach(c ->
                        m.computeIfAbsent(categoryKey(c), k -> new ArrayList<>()).add(sup)));
        return m;
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
                : o.getLines().stream().map(l -> new ReceiveLine(l.getProductId(), l.getRemainingQty(), l.getUnitCost(), null, null)).toList();

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
            item.setLotNumber(r.lotNumber());
            item.setExpiryDate(r.expiryDate());
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
                            .barcode(p.getBarcode()).category(truncate(p.getCategory(), 100))
                            .unitCost(p.getCostPrice()).build();
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
