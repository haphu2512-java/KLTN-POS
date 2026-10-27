package com.pos.purchase;

import com.pos.branch.Branch;
import com.pos.branch.BranchRepository;
import com.pos.common.BusinessException;
import com.pos.inventory.InventoryService;
import com.pos.product.Product;
import com.pos.product.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockInServiceTest {

    @Mock
    private StockInRepository stockInRepository;

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private InventoryService inventoryService;

    @InjectMocks
    private StockInService stockInService;

    private StockInRequest request(String clientRequestId, Long employeeId) {
        StockInRequest.Item item = new StockInRequest.Item();
        item.setProductId(1L);
        item.setQuantity(10);
        item.setUnitCost(BigDecimal.valueOf(5000));

        StockInRequest req = new StockInRequest();
        req.setBranchId(1L);
        req.setEmployeeId(employeeId);
        req.setClientRequestId(clientRequestId);
        req.setItems(List.of(item));
        return req;
    }

    @Test
    @DisplayName("Phiếu nhập mới có clientRequestId: lưu mã và cộng kho")
    void testNewStockInStoresClientRequestId() {
        when(stockInRepository.findByClientRequestId("si-1")).thenReturn(Optional.empty());
        when(branchRepository.findById(1L)).thenReturn(Optional.of(Branch.builder().id(1L).build()));
        when(productRepository.findById(1L)).thenReturn(Optional.of(Product.builder().id(1L).name("Sữa").build()));
        when(stockInRepository.save(any(StockIn.class))).thenAnswer(inv -> inv.getArgument(0));

        StockIn saved = stockInService.createStockIn(request(" si-1 ", 7L));

        assertEquals("si-1", saved.getClientRequestId());
        verify(inventoryService).addStockAtomic(1L, 1L, 10);
    }

    @Test
    @DisplayName("Gửi lại cùng clientRequestId: trả phiếu cũ, không cộng kho lần nữa")
    void testRetryWithSameClientRequestIdReturnsExistingStockIn() {
        StockIn existing = StockIn.builder().id(42L).employeeId(7L).clientRequestId("si-2").build();
        when(stockInRepository.findByClientRequestId("si-2")).thenReturn(Optional.of(existing));

        assertSame(existing, stockInService.createStockIn(request("si-2", 7L)));
        verify(inventoryService, never()).addStockAtomic(anyLong(), anyLong(), anyInt());
        verify(stockInRepository, never()).save(any());
    }

    @Test
    @DisplayName("clientRequestId của nhân viên khác: từ chối, không trả phiếu của người khác")
    void testClientRequestIdOfAnotherEmployeeIsRejected() {
        StockIn existing = StockIn.builder().id(42L).employeeId(8L).clientRequestId("si-3").build();
        when(stockInRepository.findByClientRequestId("si-3")).thenReturn(Optional.of(existing));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                stockInService.createStockIn(request("si-3", 7L)));
        assertEquals("DUPLICATE_REQUEST", ex.getCode());
        verify(inventoryService, never()).addStockAtomic(anyLong(), anyLong(), anyInt());
    }
}
