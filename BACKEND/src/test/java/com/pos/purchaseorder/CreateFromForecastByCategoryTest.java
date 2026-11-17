package com.pos.purchaseorder;

import com.pos.forecast.ForecastResponse;
import com.pos.forecast.ForecastService;
import com.pos.product.ProductRepository;
import com.pos.purchase.StockInRepository;
import com.pos.purchase.StockInService;
import com.pos.purchase.Supplier;
import com.pos.purchase.SupplierRepository;
import com.pos.security.CurrentUserService;
import com.pos.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CreateFromForecastByCategoryTest {

    private static ForecastResponse suggestion(long productId, String name, String category) {
        return ForecastResponse.builder().productId(productId).productName(name).category(category)
                .suggestedRestockQty(5).currentStock(0).costPrice(new BigDecimal("1000")).build();
    }

    private static Supplier supplier(long id, String name, String... categories) {
        return Supplier.builder().id(id).name(name).categories(new LinkedHashSet<>(List.of(categories))).build();
    }

    @Test
    @DisplayName("Lập đơn từ AI: mặt hàng chỉ vào đơn NCC bán đúng danh mục, kể cả khi lần nhập gần nhất là NCC khác")
    @SuppressWarnings("unchecked")
    void splitsByCategory() {
        PurchaseOrderRepository repo = mock(PurchaseOrderRepository.class);
        ForecastService forecast = mock(ForecastService.class);
        SupplierRepository suppliers = mock(SupplierRepository.class);
        StockInRepository stockIns = mock(StockInRepository.class);
        CurrentUserService users = mock(CurrentUserService.class);
        PurchaseOrderService service = new PurchaseOrderService(repo, forecast, mock(ProductRepository.class), suppliers,
                stockIns, mock(StockInService.class), users);

        User admin = new User();
        admin.setId(1L);
        when(users.getCurrentUser()).thenReturn(admin);
        when(users.currentBranchId()).thenReturn(1L);
        when(forecast.getCachedOrComputeForecast(anyLong(), anyInt(), anyString())).thenReturn(List.of(
                suggestion(10, "Sữa tươi Vinamilk", "Sữa tươi"),
                suggestion(11, "Bánh gạo Oh Ya", "Bánh gạo"),
                suggestion(12, "Coca-Cola lon", "nước ngọt"),
                suggestion(13, "Nạm bò", null)));
        when(suppliers.findAll()).thenReturn(List.of(
                supplier(1, "Vinamilk", "Sữa tươi", "Sữa chua"),
                supplier(2, "Coca-Cola", "Nước ngọt")));
        // Bánh gạo lần trước (nhầm) nhập từ Vinamilk: không được vì Vinamilk không bán danh mục này
        List<Object[]> recent = new ArrayList<>();
        recent.add(new Object[]{11L, 1L, "Vinamilk"});
        when(stockIns.recentSuppliersByProduct(1L)).thenReturn(recent);
        when(repo.saveAll(any())).thenAnswer(inv -> new ArrayList<>((java.util.Collection<PurchaseOrder>) inv.getArgument(0)));

        List<PurchaseOrder> orders = service.createFromForecast(7);

        Map<String, List<String>> bySupplier = orders.stream().collect(Collectors.toMap(
                o -> o.getSupplierName() == null ? "(chưa có)" : o.getSupplierName(),
                o -> o.getLines().stream().map(PurchaseOrderLine::getProductName).toList()));
        assertThat(bySupplier).containsOnlyKeys("Vinamilk", "Coca-Cola", "(chưa có)");
        assertThat(bySupplier.get("Vinamilk")).containsExactly("Sữa tươi Vinamilk");
        assertThat(bySupplier.get("Coca-Cola")).containsExactly("Coca-Cola lon");
        assertThat(bySupplier.get("(chưa có)")).containsExactly("Bánh gạo Oh Ya", "Nạm bò");
        PurchaseOrder unassigned = orders.stream().filter(o -> o.getSupplierId() == null).findFirst().orElseThrow();
        assertThat(unassigned.getNote()).contains("Bánh gạo").contains("chưa có danh mục");
        assertThat(unassigned.getLines().get(0).getCategory()).isEqualTo("Bánh gạo");
    }
}
