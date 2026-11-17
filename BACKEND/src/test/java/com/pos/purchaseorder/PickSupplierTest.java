package com.pos.purchaseorder;

import com.pos.purchase.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PickSupplierTest {

    private static Supplier sup(long id) {
        return Supplier.builder().id(id).name("NCC " + id).build();
    }

    @Test
    @DisplayName("Chọn NCC theo danh mục: không ai bán thì null, ưu tiên NCC nhập gần nhất, rồi NCC đã có đơn, rồi NCC đầu")
    void pickSupplier() {
        Supplier a = sup(1), b = sup(2), c = sup(3);
        assertThat(PurchaseOrderService.pickSupplier(List.of(), 1L, Set.of())).isNull();
        // NCC nhập gần nhất không bán danh mục này thì không được chọn
        assertThat(PurchaseOrderService.pickSupplier(List.of(b, c), 1L, Set.of())).isEqualTo(b);
        assertThat(PurchaseOrderService.pickSupplier(List.of(a, b, c), 3L, Set.of(2L))).isEqualTo(c);
        assertThat(PurchaseOrderService.pickSupplier(List.of(a, b, c), null, Set.of(2L))).isEqualTo(b);
        assertThat(PurchaseOrderService.pickSupplier(List.of(a, b, c), null, Set.of(-1L))).isEqualTo(a);
    }
}
