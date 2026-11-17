package com.pos.sale;

public enum InvoiceStatus {
    PAID,       // Đã thanh toán thành công
    PARTIALLY_REFUNDED, // Đã hoàn trả một phần (một số món / một phần số lượng)
    REFUNDED,   // Đã hoàn trả hàng & hoàn tiền
    CANCELLED   // Đã hủy hóa đơn
}
