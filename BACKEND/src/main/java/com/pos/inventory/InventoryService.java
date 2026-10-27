package com.pos.inventory;

import com.pos.branch.Branch;
import com.pos.branch.BranchRepository;
import com.pos.common.BusinessException;
import com.pos.product.Product;
import com.pos.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final BranchRepository branchRepository;
    private final StockTransferRepository stockTransferRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public List<Inventory> getInventoryByBranch(Long branchId) {
        return inventoryRepository.findByBranchId(branchId);
    }

    public List<Inventory> getLowStockByBranch(Long branchId) {
        return inventoryRepository.findLowStockByBranchId(branchId);
    }

    public Inventory getInventory(Long productId, Long branchId) {
        return inventoryRepository.findByProductIdAndBranchId(productId, branchId)
                .orElseGet(() -> {
                    Product product = productRepository.findById(productId)
                            .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Sản phẩm không tồn tại"));
                    Branch branch = branchRepository.findById(branchId)
                            .orElseThrow(() -> new BusinessException("BRANCH_NOT_FOUND", "Chi nhánh không tồn tại"));

                    Inventory inv = Inventory.builder()
                            .product(product)
                            .branch(branch)
                            .quantity(0)
                            .minThreshold(5)
                            .build();
                    return inventoryRepository.save(inv);
                });
    }

    @Transactional
    public void deductStockAtomic(Long productId, Long branchId, int qty) {
        int updatedRows = inventoryRepository.decrementStockAtomic(productId, branchId, qty);
        if (updatedRows == 0) {
            Product product = productRepository.findById(productId).orElse(null);
            String prodName = product != null ? product.getName() : ("ID: " + productId);
            throw new BusinessException("INSUFFICIENT_STOCK", "Không đủ tồn kho cho sản phẩm: " + prodName);
        }
        notifyInventoryUpdate(branchId, productId);
    }

    @Transactional
    public void addStockAtomic(Long productId, Long branchId, int qty) {
        // Ensure inventory record exists
        getInventory(productId, branchId);
        inventoryRepository.incrementStockAtomic(productId, branchId, qty);
        notifyInventoryUpdate(branchId, productId);
    }

    @Transactional
    public void transferStock(StockTransferRequest req) {
        if (req.getFromBranchId().equals(req.getToBranchId())) {
            throw new BusinessException("INVALID_TRANSFER", "Chi nhánh xuất và chi nhánh nhận không được trùng nhau");
        }

        String clientRequestId = req.getClientRequestId() != null && !req.getClientRequestId().isBlank()
                ? req.getClientRequestId().trim() : null;
        if (clientRequestId != null) {
            // Gửi lại sau khi mất phản hồi hoặc bấm 2 lần: lần trước đã điều chuyển xong, không trừ/cộng kho nữa
            StockTransfer existing = stockTransferRepository.findByClientRequestId(clientRequestId).orElse(null);
            if (existing != null) {
                if (!Objects.equals(existing.getEmployeeId(), req.getEmployeeId())) {
                    throw new BusinessException("DUPLICATE_REQUEST",
                            "Mã yêu cầu đã được dùng cho lần điều chuyển khác", HttpStatus.CONFLICT);
                }
                return;
            }
        }

        // 1. Deduct from source branch
        deductStockAtomic(req.getProductId(), req.getFromBranchId(), req.getQuantity());

        // 2. Add to destination branch
        addStockAtomic(req.getProductId(), req.getToBranchId(), req.getQuantity());

        // 3. Lưu phiếu điều chuyển; ràng buộc unique chặn 2 yêu cầu cùng mã tới gần như đồng thời
        stockTransferRepository.save(StockTransfer.builder()
                .productId(req.getProductId())
                .fromBranchId(req.getFromBranchId())
                .toBranchId(req.getToBranchId())
                .quantity(req.getQuantity())
                .employeeId(req.getEmployeeId())
                .note(req.getNote())
                .clientRequestId(clientRequestId)
                .build());
    }

    private void notifyInventoryUpdate(Long branchId, Long productId) {
        try {
            messagingTemplate.convertAndSend("/topic/inventory-updates",
                    Map.of("branchId", branchId, "productId", productId));
        } catch (Exception e) {
            log.warn("WS notify failed branchId={} productId={}: {}", branchId, productId, e.getMessage());
        }
    }
}
