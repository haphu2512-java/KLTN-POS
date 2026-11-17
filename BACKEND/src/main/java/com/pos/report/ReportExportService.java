package com.pos.report;

import com.pos.branch.Branch;
import com.pos.branch.BranchRepository;
import com.pos.sale.Invoice;
import com.pos.sale.InvoiceRepository;
import com.pos.sale.InvoiceStatus;
import com.pos.stockcount.StockCountService;
import com.pos.user.User;
import com.pos.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Xuất báo cáo ra file Excel nhiều trang: Tổng quan, Doanh thu theo ngày, Sản phẩm bán chạy, Hóa đơn, Hao hụt kho.
 * Số liệu lấy từ cùng các hàm của màn hình Báo cáo nên khớp với những gì quản lý thấy trên giao diện.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportExportService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ReportService reportService;
    private final InvoiceRepository invoiceRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final StockCountService stockCountService;

    /** branchId null = tất cả chi nhánh (chỉ ADMIN; controller đã kiểm tra quyền) */
    public byte[] exportXlsx(Long branchId, int days) {
        int period = Math.max(1, Math.min(days, 366));
        LocalDate firstDay = LocalDate.now().minusDays(period - 1L);
        String branchLabel = branchId == null ? "Tất cả chi nhánh"
                : branchRepository.findById(branchId).map(Branch::getName).orElse("Chi nhánh " + branchId);

        RevenueSummaryResponse summary = reportService.getRevenueSummary(branchId, period);
        List<TopProductResponse> top = reportService.getTopSellingProducts(branchId, period, 200);
        List<Invoice> invoices = invoiceRepository.findInvoicesByFilter(branchId, firstDay.atStartOfDay(), LocalDateTime.now());
        StockCountService.Shrinkage shrinkage = branchId == null ? null : stockCountService.shrinkage(branchId, firstDay, LocalDate.now());

        Set<Long> userIds = new HashSet<>();
        invoices.forEach(i -> userIds.add(i.getEmployeeId()));
        userIds.remove(null);
        Map<Long, String> userNames = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u.getFullName() != null && !u.getFullName().isBlank() ? u.getFullName() : u.getUsername()));

        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles st = new Styles(wb);
            String period1 = firstDay.format(DATE) + " - " + LocalDate.now().format(DATE);

            // 1. Tổng quan
            Sheet s = wb.createSheet("Tổng quan");
            int r = title(s, st, "BÁO CÁO DOANH THU - " + branchLabel.toUpperCase(), "Kỳ báo cáo: " + period1
                    + " · Xuất lúc " + LocalDateTime.now().format(DATE_TIME), 2);
            BigDecimal refunded = invoices.stream().map(i -> i.getRefundedAmount() != null ? i.getRefundedAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            Object[][] kpis = {
                    {"Doanh thu thuần (đã trừ hoàn trả)", summary.getTotalRevenue()},
                    {"Giá vốn hàng bán", summary.getTotalCost()},
                    {"Lãi gộp", summary.getGrossProfit()},
                    {"Biên lãi gộp (%)", summary.getProfitMarginPercent()},
                    {"Số hóa đơn", summary.getTotalOrders()},
                    {"Giá trị trung bình / hóa đơn", summary.getAverageOrderValue()},
                    {"Tiền đã hoàn trả cho khách (trả hàng một phần)", refunded},
            };
            header(s, st, r++, "Chỉ tiêu", "Giá trị");
            for (Object[] k : kpis) {
                Row row = s.createRow(r++);
                text(row, 0, (String) k[0], st.text);
                if (k[1] instanceof Double d) num(row, 1, d, st.decimal);
                else if (k[1] instanceof Integer n) num(row, 1, n, st.integer);
                else money(row, 1, (BigDecimal) k[1], st.money);
            }
            widths(s, 48, 22);

            // 2. Doanh thu theo ngày
            s = wb.createSheet("Theo ngày");
            r = title(s, st, "DOANH THU THEO NGÀY", branchLabel + " · " + period1, 5);
            int firstData = r + 2; // dòng dữ liệu đầu tiên (đánh số từ 1 như Excel), ngay dưới dòng tiêu đề cột
            header(s, st, r++, "Ngày", "Số hóa đơn", "Doanh thu", "Giá vốn", "Lãi gộp");
            for (RevenueSummaryResponse.DailyRevenueItem d : summary.getDailyTrend()) {
                Row row = s.createRow(r++);
                text(row, 0, d.getDate(), st.text);
                num(row, 1, d.getOrderCount(), st.integer);
                money(row, 2, d.getRevenue(), st.money);
                money(row, 3, d.getCost(), st.money);
                money(row, 4, d.getProfit(), st.money);
            }
            totalRow(s, st, r, firstData, r, 1, 4);
            s.createFreezePane(0, firstData - 1);
            widths(s, 12, 12, 18, 18, 18);

            // 3. Sản phẩm bán chạy
            s = wb.createSheet("Sản phẩm bán chạy");
            r = title(s, st, "SẢN PHẨM BÁN CHẠY", branchLabel + " · " + period1, 6);
            firstData = r + 2; // dòng dữ liệu đầu tiên (đánh số từ 1 như Excel), ngay dưới dòng tiêu đề cột
            header(s, st, r++, "#", "Mã vạch", "Sản phẩm", "Số lượng bán", "Doanh thu", "Lãi gộp ước tính");
            int i = 1;
            for (TopProductResponse p : top) {
                Row row = s.createRow(r++);
                num(row, 0, i++, st.integer);
                text(row, 1, p.getBarcode(), st.text);
                text(row, 2, p.getProductName(), st.text);
                num(row, 3, p.getQuantitySold(), st.integer);
                money(row, 4, p.getTotalRevenue(), st.money);
                money(row, 5, p.getEstimatedProfit(), st.money);
            }
            totalRow(s, st, r, firstData, r, 3, 5);
            s.createFreezePane(0, firstData - 1);
            widths(s, 6, 16, 44, 14, 18, 18);

            // 4. Hóa đơn
            s = wb.createSheet("Hóa đơn");
            r = title(s, st, "DANH SÁCH HÓA ĐƠN", branchLabel + " · " + period1, 12);
            firstData = r + 2; // dòng dữ liệu đầu tiên (đánh số từ 1 như Excel), ngay dưới dòng tiêu đề cột
            header(s, st, r++, "Mã HĐ", "Thời gian", "Chi nhánh", "Thu ngân", "Khách hàng", "Tạm tính", "Khuyến mãi",
                    "Giảm tay", "Tổng thanh toán", "Đã hoàn", "Thực thu", "Thanh toán", "Trạng thái");
            for (Invoice inv : invoices) {
                Row row = s.createRow(r++);
                text(row, 0, "HD" + String.format("%06d", inv.getId()), st.text);
                text(row, 1, inv.getCreatedAt() != null ? inv.getCreatedAt().format(DATE_TIME) : "", st.text);
                text(row, 2, inv.getBranch() != null ? inv.getBranch().getName() : "", st.text);
                text(row, 3, userNames.getOrDefault(inv.getEmployeeId(), ""), st.text);
                text(row, 4, inv.getCustomer() != null ? inv.getCustomer().getName() : "Khách lẻ", st.text);
                money(row, 5, inv.getSubtotal(), st.money);
                money(row, 6, inv.getPromotionDiscount(), st.money);
                money(row, 7, inv.getDiscount(), st.money);
                money(row, 8, inv.getTotalAmount(), st.money);
                money(row, 9, inv.getRefundedAmount(), st.money);
                money(row, 10, inv.getNetAmount(), st.money);
                text(row, 11, paymentLabel(inv), st.text);
                text(row, 12, statusLabel(inv.getStatus()), st.text);
            }
            totalRow(s, st, r, firstData, r, 5, 10);
            s.createFreezePane(1, firstData - 1);
            s.setAutoFilter(new CellRangeAddress(firstData - 2, Math.max(firstData - 2, r - 1), 0, 12));
            widths(s, 11, 17, 22, 20, 20, 14, 13, 13, 15, 13, 15, 13, 16);

            // 5. Hao hụt kho (theo phiếu kiểm kê đã duyệt), chỉ khi xuất cho một chi nhánh
            if (shrinkage != null) {
                s = wb.createSheet("Hao hụt kho");
                r = title(s, st, "HAO HỤT KHO THEO LÝ DO", branchLabel + " · " + period1 + " · từ phiếu kiểm kê đã duyệt", 3);
                header(s, st, r++, "Lý do", "Số lượng chênh lệch", "Giá trị (giá vốn)");
                for (StockCountService.ReasonTotal t : shrinkage.byReason()) {
                    Row row = s.createRow(r++);
                    text(row, 0, t.label(), st.text);
                    num(row, 1, t.quantity(), st.integer);
                    money(row, 2, t.value(), st.money);
                }
                Row loss = s.createRow(r++);
                text(loss, 0, "Tổng hao hụt", st.bold);
                money(loss, 2, shrinkage.totalLoss(), st.moneyBold);
                Row gain = s.createRow(r);
                text(gain, 0, "Tổng dư so với sổ", st.bold);
                money(gain, 2, shrinkage.totalGain(), st.moneyBold);
                widths(s, 30, 20, 20);
            }

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ---------- định dạng ----------

    private static final class Styles {
        final CellStyle title, subtitle, header, text, bold, money, moneyBold, integer, integerBold, decimal;

        Styles(Workbook wb) {
            DataFormat fmt = wb.createDataFormat();
            Font titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            Font boldFont = wb.createFont();
            boldFont.setBold(true);
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            Font mutedFont = wb.createFont();
            mutedFont.setItalic(true);
            mutedFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());

            title = wb.createCellStyle();
            title.setFont(titleFont);
            subtitle = wb.createCellStyle();
            subtitle.setFont(mutedFont);
            header = wb.createCellStyle();
            header.setFont(headerFont);
            header.setFillForegroundColor(IndexedColors.SEA_GREEN.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setAlignment(HorizontalAlignment.CENTER);
            header.setVerticalAlignment(VerticalAlignment.CENTER);
            header.setWrapText(true);
            text = wb.createCellStyle();
            bold = wb.createCellStyle();
            bold.setFont(boldFont);
            money = wb.createCellStyle();
            money.setDataFormat(fmt.getFormat("#,##0"));
            moneyBold = wb.createCellStyle();
            moneyBold.cloneStyleFrom(money);
            moneyBold.setFont(boldFont);
            integer = wb.createCellStyle();
            integer.setDataFormat(fmt.getFormat("#,##0"));
            integerBold = wb.createCellStyle();
            integerBold.cloneStyleFrom(integer);
            integerBold.setFont(boldFont);
            decimal = wb.createCellStyle();
            decimal.setDataFormat(fmt.getFormat("0.0"));
        }
    }

    /** Tiêu đề + dòng phụ, trả về chỉ số dòng tiếp theo (để trống một dòng) */
    private static int title(Sheet s, Styles st, String title, String subtitle, int cols) {
        Row r0 = s.createRow(0);
        text(r0, 0, title, st.title);
        Row r1 = s.createRow(1);
        text(r1, 0, subtitle, st.subtitle);
        if (cols > 1) {
            s.addMergedRegion(new CellRangeAddress(0, 0, 0, cols - 1));
            s.addMergedRegion(new CellRangeAddress(1, 1, 0, cols - 1));
        }
        return 3;
    }

    private static void header(Sheet s, Styles st, int rowIdx, String... titles) {
        Row row = s.createRow(rowIdx);
        row.setHeightInPoints(30);
        for (int i = 0; i < titles.length; i++) text(row, i, titles[i], st.header);
    }

    /** Dòng tổng dùng công thức SUM để người dùng sửa số liệu trong Excel vẫn tự cộng lại */
    private static void totalRow(Sheet s, Styles st, int rowIdx, int firstDataRow1Based, int lastRowExclusive, int fromCol, int toCol) {
        Row row = s.createRow(rowIdx);
        text(row, 0, "Tổng cộng", st.bold);
        if (lastRowExclusive < firstDataRow1Based) return;
        for (int c = fromCol; c <= toCol; c++) {
            String col = org.apache.poi.ss.util.CellReference.convertNumToColString(c);
            Cell cell = row.createCell(c);
            cell.setCellFormula("SUM(" + col + firstDataRow1Based + ":" + col + lastRowExclusive + ")");
            cell.setCellStyle(st.moneyBold);
        }
    }

    private static void widths(Sheet s, int... chars) {
        for (int i = 0; i < chars.length; i++) s.setColumnWidth(i, chars[i] * 256);
    }

    private static void text(Row row, int col, String value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value != null ? value : "");
        c.setCellStyle(style);
    }

    private static void money(Row row, int col, BigDecimal value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value != null ? value.doubleValue() : 0);
        c.setCellStyle(style);
    }

    private static void num(Row row, int col, double value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value);
        c.setCellStyle(style);
    }

    private static String paymentLabel(Invoice inv) {
        if (inv.getPaymentMethod() == null) return "";
        return switch (inv.getPaymentMethod()) {
            case CASH -> "Tiền mặt";
            case VIETQR -> "Chuyển khoản";
            case CARD -> "Thẻ";
        };
    }

    private static String statusLabel(InvoiceStatus status) {
        if (status == null) return "";
        return switch (status) {
            case PAID -> "Đã thanh toán";
            case PARTIALLY_REFUNDED -> "Trả hàng một phần";
            case REFUNDED -> "Đã hoàn trả";
            default -> status.name();
        };
    }
}
