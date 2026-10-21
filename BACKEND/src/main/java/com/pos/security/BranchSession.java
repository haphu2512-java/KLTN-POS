package com.pos.security;

/**
 * Chi nhánh mà phiên đăng nhập đang làm việc, đọc từ claim "branchId" của token đã kiểm tra.
 * Gắn vào Authentication (details) để mọi request và kết nối WebSocket dùng chung.
 */
public record BranchSession(Long branchId) {
}
