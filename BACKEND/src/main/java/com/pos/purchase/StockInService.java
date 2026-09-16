package com.pos.purchase;

import com.pos.branch.Branch;
import com.pos.branch.BranchRepository;
import com.pos.common.BusinessException;
import com.pos.inventory.InventoryService;
import com.pos.product.Product;
import com.pos.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StockInService {

    private final StockInRepository stockInRepository;
    private final BranchRepository branchRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;

    public List<StockIn> getAllStockIn(Long branchId) {
        if (branchId != null) {
            return stockInRepository.findByBranchIdOrderByCreatedAtDesc(branchId);
        }
        return stockInRepository.findAllByOrderByCreatedAtDesc();
    }

    public StockIn getStockInById(Long id) {
        return stockInRepository.findById(id)
                .orElseThrow(() -> new BusinessException("STOCK_IN_NOT_FOUND", "Không tìm thấy phiếu nhập kho với ID: " + id));
    }

    @Transactional
    public StockIn createStockIn(StockInRequest req) {
        Branch branch = branchRepository.findById(req.getBranchId())
                .orElseThrow(() -> new BusinessException("BRANCH_NOT_FOUND", "Chi nhánh không tồn tại"));

        Supplier supplier = null;
        if (req.getSupplierId() != null) {
            supplier = supplierRepository.findById(req.getSupplierId()).orElse(null);
        }

        StockIn stockIn = StockIn.builder()
                .branch(branch)
                .supplier(supplier)
                .employeeId(req.getEmployeeId())
                .note(req.getNote())
                .totalCost(BigDecimal.ZERO)
                .details(new ArrayList<>())
                .build();

        BigDecimal totalCost = BigDecimal.ZERO;

        for (StockInRequest.Item itemReq : req.getItems()) {
            Product product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Sản phẩm không tồn tại: " + itemReq.getProductId()));

            BigDecimal unitCost = itemReq.getUnitCost() != null ? itemReq.getUnitCost() : product.getCostPrice();
            if (unitCost == null) {
                unitCost = BigDecimal.ZERO;
            }

            // 1. Create Detail
            StockInDetail detail = StockInDetail.builder()
                    .stockIn(stockIn)
                    .product(product)
                    .quantity(itemReq.getQuantity())
                    .unitCost(unitCost)
                    .build();

            stockIn.getDetails().add(detail);
            totalCost = totalCost.add(unitCost.multiply(BigDecimal.valueOf(itemReq.getQuantity())));

            // 2. Increment Stock in branch inventory atomically
            inventoryService.addStockAtomic(product.getId(), branch.getId(), itemReq.getQuantity());

            // 3. Update latest cost price on product catalog
            product.setCostPrice(unitCost);
            productRepository.save(product);
        }

        stockIn.setTotalCost(totalCost);
        return stockInRepository.save(stockIn);
    }
}
