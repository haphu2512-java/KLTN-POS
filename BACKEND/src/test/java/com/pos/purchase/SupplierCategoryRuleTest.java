package com.pos.purchase;

import com.pos.branch.Branch;
import com.pos.branch.BranchRepository;
import com.pos.common.BusinessException;
import com.pos.inventory.InventoryService;
import com.pos.product.Product;
import com.pos.product.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Phiếu nhập tự do ở trang Kho hàng phải theo danh mục NCC, giống gửi đơn đặt hàng */
class SupplierCategoryRuleTest {

    private final StockInRepository stockIns = mock(StockInRepository.class);
    private final BranchRepository branches = mock(BranchRepository.class);
    private final SupplierRepository suppliers = mock(SupplierRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final InventoryService inventory = mock(InventoryService.class);
    private final StockInService service = new StockInService(stockIns, branches, suppliers, products, inventory);

    private static Supplier supplier(long id, String name, String... categories) {
        return Supplier.builder().id(id).name(name).categories(new LinkedHashSet<>(List.of(categories))).build();
    }

    private StockInRequest request(long supplierId, long... productIds) {
        StockInRequest req = new StockInRequest();
        req.setBranchId(1L);
        req.setSupplierId(supplierId);
        req.setItems(java.util.Arrays.stream(productIds).mapToObj(id -> {
            StockInRequest.Item it = new StockInRequest.Item();
            it.setProductId(id);
            it.setQuantity(10);
            it.setUnitCost(new BigDecimal("26000"));
            return it;
        }).toList());
        return req;
    }

    private void products() {
        when(products.findById(5L)).thenReturn(Optional.of(Product.builder().id(5L).name("Kẹo socola M&M's Mini ống 35g").category("Socola").build()));
        when(products.findById(6L)).thenReturn(Optional.of(Product.builder().id(6L).name("Dầu ăn Simply 1L").category("Dầu ăn").build()));
        when(products.findById(7L)).thenReturn(Optional.of(Product.builder().id(7L).name("Hàng chưa phân loại").category(null).build()));
        when(branches.findById(1L)).thenReturn(Optional.of(Branch.builder().id(1L).build()));
        when(stockIns.save(any(StockIn.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("NCC dầu ăn không nhập được kẹo socola (trường hợp phiếu NK-25)")
    void rejectsProductOutsideSupplierCategories() {
        products();
        when(suppliers.findById(4L)).thenReturn(Optional.of(supplier(4, "Cái Lân (Simply)", "Dầu ăn", "Gia vị & Dầu ăn")));

        assertThatThrownBy(() -> service.createManualStockIn(request(4, 6, 5)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    assertThat(((BusinessException) e).getCode()).isEqualTo("SUPPLIER_CATEGORY_MISMATCH");
                    assertThat(e.getMessage()).contains("Socola").contains("M&M's");
                });
        verify(inventory, never()).addStockAtomic(anyLong(), anyLong(), anyInt());
    }

    @Test
    @DisplayName("Đúng danh mục thì nhập được; so danh mục không phân biệt hoa thường; hàng chưa có danh mục cho qua")
    void acceptsMatchingCategories() {
        products();
        when(suppliers.findById(4L)).thenReturn(Optional.of(supplier(4, "Cái Lân (Simply)", "DẦU ĂN")));

        StockIn saved = service.createManualStockIn(request(4, 6, 7));

        assertThat(saved.getPurchaseOrderId()).isNull();
        verify(inventory).addStockAtomic(6L, 1L, 10);
        verify(inventory).addStockAtomic(7L, 1L, 10);
    }

    @Test
    @DisplayName("NCC chưa gán danh mục thì không dùng được cho phiếu nhập")
    void rejectsSupplierWithoutCategories() {
        products();
        when(suppliers.findById(9L)).thenReturn(Optional.of(supplier(9, "NCC mới")));

        assertThatThrownBy(() -> service.createManualStockIn(request(9, 6)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("SUPPLIER_NO_CATEGORY");
    }

    @Test
    @DisplayName("Phiếu nhập tự do bắt buộc chọn NCC")
    void requiresSupplier() {
        StockInRequest req = request(1, 6);
        req.setSupplierId(null);
        assertThatThrownBy(() -> service.createManualStockIn(req))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode()).isEqualTo("SUPPLIER_REQUIRED");
    }

    @Test
    @DisplayName("Phiếu sinh ra từ nhận hàng theo đơn ghi lại mã đơn gốc")
    void stockInFromPurchaseOrderKeepsOrderId() {
        products();
        StockIn saved = service.createStockIn(request(4, 5), 12L);
        assertThat(saved.getPurchaseOrderId()).isEqualTo(12L);
    }
}
