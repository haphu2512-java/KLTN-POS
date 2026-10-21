package com.pos.security;

import com.pos.common.BusinessException;
import com.pos.user.Role;
import com.pos.user.User;
import com.pos.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Lấy tài khoản đang đăng nhập từ SecurityContext (đã được JwtAuthFilter xác thực).
 * Dùng để server tự quyết định nhân viên / chi nhánh thay vì tin dữ liệu client gửi lên.
 */
@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final UserRepository userRepository;

    /** Tài khoản đang đăng nhập, kèm chi nhánh của phiên (currentBranchId) lấy từ token */
    public User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
            throw new BusinessException("UNAUTHORIZED", "Chưa đăng nhập", HttpStatus.UNAUTHORIZED);
        }
        User user = userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new BusinessException("UNAUTHORIZED", "Tài khoản không tồn tại", HttpStatus.UNAUTHORIZED));
        if (auth.getDetails() instanceof BranchSession session) {
            user.setCurrentBranchId(session.branchId());
        }
        return user;
    }

    /** Chi nhánh của phiên đăng nhập; dùng cho mọi thao tác ghi (bán, nhập kho, mở ca...) */
    public Long currentBranchId() {
        Long branchId = getCurrentUser().getCurrentBranchId();
        if (branchId == null) {
            throw new BusinessException("UNAUTHORIZED", "Phiên đăng nhập chưa chọn chi nhánh", HttpStatus.UNAUTHORIZED);
        }
        return branchId;
    }

    /**
     * Chi nhánh dùng để xem dữ liệu (tồn kho, báo cáo, hóa đơn...). ADMIN xem được chi nhánh bất kỳ,
     * để trống là xem tất cả (với API hỗ trợ) hoặc chi nhánh đang đăng nhập khi {@code allowAll} = false.
     * Vai trò khác chỉ xem chi nhánh đang đăng nhập; hỏi chi nhánh khác thì bị từ chối.
     */
    public Long branchForRead(Long requested, boolean allowAll) {
        User user = getCurrentUser();
        if (user.getRole() == Role.ADMIN) {
            if (requested != null) return requested;
            return allowAll ? null : user.getCurrentBranchId();
        }
        Long session = user.getCurrentBranchId();
        if (requested != null && !requested.equals(session)) {
            throw new BusinessException("BRANCH_FORBIDDEN",
                    "Bạn chỉ xem được dữ liệu của chi nhánh đang đăng nhập", HttpStatus.FORBIDDEN);
        }
        return session;
    }

    /**
     * Chi nhánh cho thao tác ghi: luôn là chi nhánh của phiên. Client cũ còn gửi branchId thì phải khớp,
     * lệch là dấu hiệu dữ liệu cũ (ví dụ đơn chờ gửi từ phiên trước) nên từ chối thay vì ghi nhầm chi nhánh.
     */
    public Long branchForWrite(Long requested) {
        Long session = currentBranchId();
        if (requested != null && !requested.equals(session)) {
            throw new BusinessException("BRANCH_MISMATCH",
                    "Phiên đăng nhập đang ở chi nhánh khác. Hãy đăng nhập đúng chi nhánh để thao tác.", HttpStatus.FORBIDDEN);
        }
        return session;
    }
}
