package com.pos.purchaseorder;

import com.pos.common.BusinessException;
import com.pos.forecast.ForecastService;
import com.pos.product.Product;
import com.pos.product.ProductRepository;
import com.pos.purchase.StockInRepository;
import com.pos.purchase.StockInService;
import com.pos.purchase.Supplier;
import com.pos.purchase.SupplierRepository;
import com.pos.security.CurrentUserService;
import com.pos.user.Role;
import com.pos.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SplitByCategoryTest {

    private final PurchaseOrderRepository repo = mock(PurchaseOrderRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final SupplierRepository suppliers = mock(SupplierRepository.class);
    private final StockInRepository stockIns = mock(StockInRepository.class);
    private final CurrentUserService users = mock(CurrentUserService.class);
    private final PurchaseOrderService service = new PurchaseOrderService(repo, mock(ForecastService.class), products,
            suppliers, stockIns, mock(StockInService.class), users);

    private static Supplier supplier(long id, String name, String... categories) {
        return Supplier.builder().id(id).name(name).categories(new LinkedHashSet<>(List.of(categories))).build();
    }

    private static PurchaseOrderLine line(PurchaseOrder o, long productId, String name, String category) {
        return PurchaseOrderLine.builder().purchaseOrder(o).productId(productId).productName(name).category(category)
                .orderedQty(3).suggestedQty(3).unitCost(new BigDecimal("1000")).build();
    }

    @SuppressWarnings("unchecked")
    private PurchaseOrder setup(Long supplierId) {
        User admin = new User();
        admin.setId(1L);
        admin.setRole(Role.ADMIN);
        when(users.getCurrentUser()).thenReturn(admin);
        Supplier vinamilk = supplier(1, "Vinamilk", "Sữa tươi");
        Supplier banh = supplier(2, "NCC bánh", "Bánh gạo", "Bánh quy");
        when(suppliers.findAll()).thenReturn(List.of(vinamilk, banh));
        when(suppliers.findById(1L)).thenReturn(Optional.of(vinamilk));
        when(suppliers.findById(2L)).thenReturn(Optional.of(banh));
        when(stockIns.recentSuppliersByProduct(anyLong())).thenReturn(new ArrayList<>());
        when(repo.saveAll(any())).thenAnswer(inv -> new ArrayList<>((java.util.Collection<PurchaseOrder>) inv.getArgument(0)));
        PurchaseOrder o = PurchaseOrder.builder().id(40L).branchId(1L).supplierId(supplierId)
                .supplierName(supplierId == null ? null : "Vinamilk").source("FORECAST").note("Lập từ đề xuất AI")
                .createdAt(LocalDateTime.now()).build();
        o.getLines().add(line(o, 10, "Bánh gạo Oh Ya", "Bánh gạo"));
        o.getLines().add(line(o, 11, "Sữa tươi", "Sữa tươi"));
        o.getLines().add(line(o, 12, "Bánh gạo Want Want", "Bánh gạo"));
        o.getLines().add(line(o, 13, "Nạm bò", null)); // dòng cũ chưa lưu danh mục: lấy theo sản phẩm
        o.getLines().add(line(o, 14, "Kẹo dẻo", "Kẹo dẻo"));
        when(repo.findById(40L)).thenReturn(Optional.of(o));
        when(products.findById(13L)).thenReturn(Optional.of(Product.builder().id(13L).category("Thịt & Món mặn").build()));
        return o;
    }

    @Test
    @DisplayName("Tách đơn theo danh mục: mỗi đơn một danh mục, đơn gốc giữ nhóm NCC đang chọn bán, nhóm khác tự gán NCC")
    void splitsKeepingSupplierGroup() {
        PurchaseOrder o = setup(1L);
        List<PurchaseOrder> out = service.splitByCategory(40L);

        assertThat(out).hasSize(4);
        assertThat(out.get(0)).isSameAs(o);
        assertThat(o.getSupplierName()).isEqualTo("Vinamilk");
        assertThat(o.getLines()).extracting(PurchaseOrderLine::getProductName).containsExactly("Sữa tươi");
        PurchaseOrder banh = out.stream().filter(x -> "NCC bánh".equals(x.getSupplierName())).findFirst().orElseThrow();
        assertThat(banh.getLines()).extracting(PurchaseOrderLine::getProductName).containsExactly("Bánh gạo Oh Ya", "Bánh gạo Want Want");
        assertThat(banh.getNote()).contains("Bánh gạo").contains("#40");
        assertThat(banh.getSource()).isEqualTo("FORECAST");
        List<PurchaseOrder> noSupplier = out.stream().filter(x -> x.getSupplierId() == null).toList();
        assertThat(noSupplier).hasSize(2);
        assertThat(noSupplier).allSatisfy(x -> assertThat(x.getNote()).contains("Chưa có NCC"));
        assertThat(noSupplier).flatExtracting(PurchaseOrder::getLines).extracting(PurchaseOrderLine::getCategory)
                .containsExactlyInAnyOrder("Thịt & Món mặn", "Kẹo dẻo");
        assertThat(out).flatExtracting(PurchaseOrder::getLines).hasSize(5);
    }

    @Test
    @DisplayName("Tách đơn chưa chọn NCC: đơn gốc giữ nhóm đầu tiên; đơn một danh mục thì báo không cần tách")
    void splitsUnassignedAndRejectsSingle() {
        PurchaseOrder o = setup(null);
        List<PurchaseOrder> out = service.splitByCategory(40L);
        assertThat(out.get(0).getLines()).extracting(PurchaseOrderLine::getCategory).containsOnly("Bánh gạo");
        assertThat(out.get(0).getSupplierName()).isEqualTo("NCC bánh");

        o.getLines().removeIf(l -> !"Bánh gạo".equals(l.getCategory()));
        assertThatThrownBy(() -> service.splitByCategory(40L)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("một danh mục");
    }
}
