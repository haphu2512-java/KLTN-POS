package com.pos.shift;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class CloseShiftRequest {

    @NotNull(message = "Tiền mặt cuối ca không được để trống")
    @DecimalMin(value = "0", message = "Tiền mặt cuối ca không được âm")
    private BigDecimal closingCash;

    /** Số tờ theo mệnh giá (vd. {"500000": 2, "1000": 15}); nếu gửi thì tổng phải bằng closingCash */
    private Map<Integer, Integer> denominations;

    /** Tiền trên sao kê VietQR; để trống là khớp với số hệ thống */
    @DecimalMin(value = "0", message = "Tiền VietQR không được âm")
    private BigDecimal countedVietqr;

    /** Tiền trên phiếu chốt máy POS ngân hàng; để trống là khớp với số hệ thống */
    @DecimalMin(value = "0", message = "Tiền thẻ không được âm")
    private BigDecimal countedCard;

    /** Bắt buộc khi có dòng lệch quá ngưỡng */
    @Size(max = 500, message = "Lý do chênh lệch tối đa 500 ký tự")
    private String differenceReason;

    /** Tiền mặt để lại trong két cho ca sau; để trống là để lại toàn bộ tiền đếm được */
    @DecimalMin(value = "0", message = "Tiền để lại không được âm")
    private BigDecimal leftInDrawer;

    @Size(max = 500, message = "Ghi chú bàn giao tối đa 500 ký tự")
    private String handoverNote;
}
