package com.pos.report;

import com.pos.sale.Invoice;
import com.pos.sale.InvoiceDetail;
import com.pos.sale.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ReportService {

    private final InvoiceRepository invoiceRepository;

    public RevenueSummaryResponse getRevenueSummary(Long branchId, int days) {
        // Khoảng đúng `days` ngày lịch, tính cả hôm nay
        LocalDate firstDay = LocalDate.now().minusDays(Math.max(days, 1) - 1L);
        LocalDateTime start = firstDay.atStartOfDay();
        LocalDateTime end = LocalDateTime.now();

        List<Invoice> invoices = invoiceRepository.findInvoicesByFilter(branchId, start, end);

        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        int totalOrders = invoices.size();

        // Nhóm theo LocalDate (không phải chuỗi "dd/MM") để thứ tự ngày đúng khi qua tháng/năm mới
        Map<LocalDate, List<Invoice>> groupedByDate = new TreeMap<>();
        DateTimeFormatter df = DateTimeFormatter.ofPattern("dd/MM");

        for (Invoice inv : invoices) {
            totalRevenue = totalRevenue.add(inv.getTotalAmount() != null ? inv.getTotalAmount() : BigDecimal.ZERO);
            totalCost = totalCost.add(invoiceCost(inv));

            LocalDate dateKey = inv.getCreatedAt() != null ? inv.getCreatedAt().toLocalDate() : LocalDate.now();
            groupedByDate.computeIfAbsent(dateKey, k -> new ArrayList<>()).add(inv);
        }

        BigDecimal grossProfit = totalRevenue.subtract(totalCost);
        double profitMargin = totalRevenue.compareTo(BigDecimal.ZERO) > 0
                ? grossProfit.divide(totalRevenue, 4, RoundingMode.HALF_UP).doubleValue() * 100.0
                : 0.0;

        BigDecimal averageOrderValue = totalOrders > 0
                ? totalRevenue.divide(BigDecimal.valueOf(totalOrders), 0, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Điền đủ mọi ngày trong khoảng (ngày không có đơn = 0) để biểu đồ liên tục
        for (LocalDate d = firstDay; !d.isAfter(LocalDate.now()); d = d.plusDays(1)) {
            groupedByDate.putIfAbsent(d, new ArrayList<>());
        }

        List<RevenueSummaryResponse.DailyRevenueItem> dailyTrend = new ArrayList<>();
        for (Map.Entry<LocalDate, List<Invoice>> entry : groupedByDate.entrySet()) {
            BigDecimal dayRev = BigDecimal.ZERO;
            BigDecimal dayCost = BigDecimal.ZERO;

            for (Invoice i : entry.getValue()) {
                dayRev = dayRev.add(i.getTotalAmount() != null ? i.getTotalAmount() : BigDecimal.ZERO);
                dayCost = dayCost.add(invoiceCost(i));
            }

            dailyTrend.add(RevenueSummaryResponse.DailyRevenueItem.builder()
                    .date(entry.getKey().format(df))
                    .revenue(dayRev)
                    .cost(dayCost)
                    .profit(dayRev.subtract(dayCost))
                    .orderCount(entry.getValue().size())
                    .build());
        }

        return RevenueSummaryResponse.builder()
                .totalRevenue(totalRevenue)
                .totalCost(totalCost)
                .grossProfit(grossProfit)
                .profitMarginPercent(Math.round(profitMargin * 10.0) / 10.0)
                .totalOrders(totalOrders)
                .averageOrderValue(averageOrderValue)
                .dailyTrend(dailyTrend)
                .build();
    }

    /** Giá vốn của hóa đơn: ưu tiên giá vốn lưu lúc bán, hóa đơn cũ chưa có thì dùng giá vốn hiện tại */
    private BigDecimal invoiceCost(Invoice inv) {
        BigDecimal cost = BigDecimal.ZERO;
        if (inv.getDetails() == null) return cost;
        for (InvoiceDetail item : inv.getDetails()) {
            BigDecimal unitCost = item.getCostPrice();
            if (unitCost == null && item.getProduct() != null) {
                unitCost = item.getProduct().getCostPrice();
            }
            if (unitCost != null && item.getQuantity() != null) {
                cost = cost.add(unitCost.multiply(BigDecimal.valueOf(item.getQuantity())));
            }
        }
        return cost;
    }

    public List<TopProductResponse> getTopSellingProducts(Long branchId, int days, int limit) {
        LocalDateTime start = LocalDateTime.now().minusDays(days);
        LocalDateTime end = LocalDateTime.now();

        List<Object[]> rawList = invoiceRepository.getTopSellingProducts(branchId, start, end);
        List<TopProductResponse> result = new ArrayList<>();

        for (Object[] row : rawList) {
            Long prodId = row[0] != null ? ((Number) row[0]).longValue() : null;
            String name = (String) row[1];
            String barcode = (String) row[2];
            int qty = row[3] != null ? ((Number) row[3]).intValue() : 0;
            BigDecimal rev = row[4] != null ? (BigDecimal) row[4] : BigDecimal.ZERO;
            BigDecimal cost = row[5] != null ? new BigDecimal(row[5].toString()) : BigDecimal.ZERO;

            result.add(TopProductResponse.builder()
                    .productId(prodId)
                    .productName(name)
                    .barcode(barcode)
                    .quantitySold(qty)
                    .totalRevenue(rev)
                    .estimatedProfit(rev.subtract(cost))
                    .build());

            if (result.size() >= limit) break;
        }

        return result;
    }
}
