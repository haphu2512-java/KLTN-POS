package com.pos.purchaseorder;

/** Nháp → Đã gửi NCC → Đã nhận một phần / Đã nhận đủ; hoặc Đã hủy */
public enum PurchaseOrderStatus {
    DRAFT, SENT, PARTIALLY_RECEIVED, RECEIVED, CANCELLED
}
