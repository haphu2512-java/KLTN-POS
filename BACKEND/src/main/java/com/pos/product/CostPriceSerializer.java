package com.pos.product;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.math.BigDecimal;

/**
 * Ẩn giá vốn với thu ngân. Product được trả về ở nhiều API (sản phẩm, tồn kho, chi tiết hóa đơn...),
 * nên lọc ngay lúc serialize thay vì sửa từng endpoint.
 */
public class CostPriceSerializer extends JsonSerializer<BigDecimal> {

    @Override
    public void serialize(BigDecimal value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (isCashier()) {
            gen.writeNull();
        } else {
            gen.writeNumber(value);
        }
    }

    private static boolean isCashier() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_CASHIER".equals(a.getAuthority()));
    }
}
