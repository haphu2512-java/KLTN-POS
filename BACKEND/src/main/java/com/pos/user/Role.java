package com.pos.user;

/**
 * Hệ thống có 3 vai trò. Dữ liệu cũ CASHIER (thu ngân) và WAREHOUSE (thủ kho) được
 * DatabaseInitializer chuyển thành STAFF khi khởi động; việc kho do MANAGER / ADMIN đảm nhận.
 */
public enum Role {
    ADMIN,      // Quản trị viên toàn hệ thống
    MANAGER,    // Quản lý chi nhánh (gồm cả nghiệp vụ kho: nhập, điều chuyển, kiểm kê, đặt hàng)
    STAFF       // Nhân viên bán hàng tại quầy
}
