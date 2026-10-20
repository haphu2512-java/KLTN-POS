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
        LocalDateTime start = LocalDateTime.now().minusDays(days);
        LocalDateTime end = LocalDateTime.now();

        List<Invoice> invoices = invoiceRepository.findInvoicesByFilter(branchId, start, end);

        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        int totalOrders = invoices.size();

        Map<String, List<Invoice>> groupedByDate = new TreeMap<>();
        DateTimeFormatter df = DateTimeFormatter.ofPattern("dd/MM");

        for (Invoice inv : invoices) {
            totalRevenue = totalRevenue.add(inv.getTotalAmount() != null ? inv.getTotalAmount() : BigDecimal.ZERO);

            if (inv.getDetails() != null) {
                for (InvoiceDetail item : inv.getDetails()) {
                    BigDecimal cost = (item.getProduct() != null && item.getProduct().getCostPrice() != null)
                            ? item.getProduct().getCostPrice()
                            : BigDecimal.ZERO;
                    totalCost = totalCost.add(cost.multiply(BigDecimal.valueOf(item.getQuantity())));
                }
            }

            String dateKey = inv.getCreatedAt() != null ? inv.getCreatedAt().format(df) : LocalDate.now().format(df);
            groupedByDate.computeIfAbsent(dateKey, k -> new ArrayList<>()).add(inv);
        }

        BigDecimal grossProfit = totalRevenue.subtract(totalCost);
        double profitMargin = totalRevenue.compareTo(BigDecimal.ZERO) > 0
                ? grossProfit.divide(totalRevenue, 4, RoundingMode.HALF_UP).doubleValue() * 100.0
                : 0.0;

        BigDecimal averageOrderValue = totalOrders > 0
                ? totalRevenue.divide(BigDecimal.valueOf(totalOrders), 0, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        List<RevenueSummaryResponse.DailyRevenueItem> dailyTrend = new ArrayList<>();
        for (Map.Entry<String, List<Invoice>> entry : groupedByDate.entrySet()) {
            BigDecimal dayRev = BigDecimal.ZERO;
            BigDecimal dayCost = BigDecimal.ZERO;

            for (Invoice i : entry.getValue()) {
                dayRev = dayRev.add(i.getTotalAmount() != null ? i.getTotalAmount() : BigDecimal.ZERO);
                if (i.getDetails() != null) {
                    for (InvoiceDetail it : i.getDetails()) {
                        BigDecimal c = (it.getProduct() != null && it.getProduct().getCostPrice() != null)
                                ? it.getProduct().getCostPrice()
                                : BigDecimal.ZERO;
                        dayCost = dayCost.add(c.multiply(BigDecimal.valueOf(it.getQuantity())));
                    }
                }
            }

            dailyTrend.add(RevenueSummaryResponse.DailyRevenueItem.builder()
                    .date(entry.getKey())
                    .revenue(dayRev)
                    .cost(dayCost)
                    .profit(dayRev.subtract(dayCost))
                    .orderCount(entry.getValue().size())
                    .build());
        }

        // If no records in range, populate dummy calendar days with zero
        if (dailyTrend.isEmpty()) {
            for (int i = days - 1; i >= 0; i--) {
                dailyTrend.add(RevenueSummaryResponse.DailyRevenueItem.builder()
                        .date(LocalDate.now().minusDays(i).format(df))
                        .revenue(BigDecimal.ZERO)
                        .cost(BigDecimal.ZERO)
                        .profit(BigDecimal.ZERO)
                        .orderCount(0)
                        .build());
            }
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

            result.add(TopProductResponse.builder()
                    .productId(prodId)
                    .productName(name)
                    .barcode(barcode)
                    .quantitySold(qty)
                    .totalRevenue(rev)
                    .estimatedProfit(rev.multiply(BigDecimal.valueOf(0.25))) // estimated 25% margin
                    .build());

            if (result.size() >= limit) break;
        }

        return result;
    }
}
