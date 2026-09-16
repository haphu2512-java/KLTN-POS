package com.pos.inventory;

import com.pos.branch.Branch;
import com.pos.branch.BranchRepository;
import com.pos.common.BusinessException;
import com.pos.product.Product;
import com.pos.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final BranchRepository branchRepository;
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

        // 1. Deduct from source branch
        deductStockAtomic(req.getProductId(), req.getFromBranchId(), req.getQuantity());

        // 2. Add to destination branch
        addStockAtomic(req.getProductId(), req.getToBranchId(), req.getQuantity());
    }

    private void notifyInventoryUpdate(Long branchId, Long productId) {
        try {
            messagingTemplate.convertAndSend("/topic/inventory-updates",
                    String.format("{\"branchId\": %d, \"productId\": %d}", branchId, productId));
        } catch (Exception ignored) {
            // Ignore messaging errors in offline / test setups
        }
    }
}
