package com.pos.inventory;

import com.pos.branch.Branch;
import com.pos.branch.BranchRepository;
import com.pos.common.BusinessException;
import com.pos.product.Product;
import com.pos.product.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private InventoryService inventoryService;

    @Test
    @DisplayName("Trừ kho nguyên tử thành công và gửi thông báo WebSocket")
    void testDeductStockAtomicSuccess() {
        when(inventoryRepository.decrementStockAtomic(1L, 1L, 5)).thenReturn(1);

        assertDoesNotThrow(() -> inventoryService.deductStockAtomic(1L, 1L, 5));
        verify(inventoryRepository).decrementStockAtomic(1L, 1L, 5);
        verify(messagingTemplate).convertAndSend(eq("/topic/inventory-updates"), any(Object.class));
    }

    @Test
    @DisplayName("Ném BusinessException khi tồn kho không đủ (cập nhật thất bại = 0 rows)")
    void testDeductStockAtomicInsufficient() {
        when(inventoryRepository.decrementStockAtomic(1L, 1L, 100)).thenReturn(0);
        when(productRepository.findById(1L))
                .thenReturn(Optional.of(Product.builder().id(1L).name("Coca Cola").build()));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                inventoryService.deductStockAtomic(1L, 1L, 100)
        );

        assertEquals("INSUFFICIENT_STOCK", ex.getCode());
        assertTrue(ex.getMessage().contains("Không đủ tồn kho"));
    }

    @Test
    @DisplayName("Lấy danh sách sản phẩm sắp hết hàng theo ngưỡng an toàn")
    void testGetLowStockByBranch() {
        Inventory lowStockItem = Inventory.builder()
                .id(1L)
                .quantity(3)
                .minThreshold(10)
                .build();

        when(inventoryRepository.findLowStockByBranchId(1L)).thenReturn(List.of(lowStockItem));

        List<Inventory> list = inventoryService.getLowStockByBranch(1L);
        assertNotNull(list);
        assertEquals(1, list.size());
        assertTrue(list.get(0).getQuantity() <= list.get(0).getMinThreshold());
    }
}
