package com.pos.auth;

import com.pos.branch.Branch;
import com.pos.branch.BranchAccessService;
import com.pos.branch.BranchRepository;
import com.pos.common.BusinessException;
import com.pos.security.JwtTokenProvider;
import com.pos.shift.ShiftSession;
import com.pos.shift.ShiftSessionRepository;
import com.pos.shift.ShiftStatus;
import com.pos.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Chọn chi nhánh lúc đăng nhập. Mật khẩu đã được kiểm tra trước khi vào đây.
 * <ul>
 *   <li>Chỉ được chọn chi nhánh mình được gán (ADMIN: mọi chi nhánh đang hoạt động).</li>
 *   <li>Được gán đúng một chi nhánh thì tự chọn chi nhánh đó.</li>
 *   <li>Nhiều chi nhánh mà chưa chọn: trả về danh sách để màn hình đăng nhập hỏi.</li>
 *   <li>Đang có ca chưa đóng ở chi nhánh A thì không vào được chi nhánh B: tiền mặt của ca nằm
 *       trong két ở A, phải đăng nhập vào A để đóng ca (hoặc nhờ quản trị đóng hộ).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LoginService {

    private final BranchAccessService branchAccessService;
    private final BranchRepository branchRepository;
    private final ShiftSessionRepository shiftSessionRepository;
    private final JwtTokenProvider tokenProvider;

    public AuthResponse login(User user, UserDetails userDetails, Long requestedBranchId) {
        List<Branch> allowed = branchAccessService.allowedBranches(user);
        if (allowed.isEmpty()) {
            throw new BusinessException("NO_BRANCH_ASSIGNED",
                    "Tài khoản chưa được gán chi nhánh nào. Vui lòng liên hệ quản trị.", HttpStatus.FORBIDDEN);
        }

        ShiftSession openShift = shiftSessionRepository.findByEmployeeIdAndStatus(user.getId(), ShiftStatus.OPEN).orElse(null);

        Branch chosen;
        if (requestedBranchId == null) {
            if (allowed.size() > 1) {
                Map<String, Object> data = new HashMap<>();
                data.put("branches", allowed.stream().map(BranchOption::of).toList());
                data.put("openShiftBranchId", openShift != null ? openShift.getBranchId() : null);
                throw new BusinessException("BRANCH_SELECTION_REQUIRED",
                        "Vui lòng chọn chi nhánh làm việc", HttpStatus.CONFLICT, data);
            }
            chosen = allowed.get(0);
        } else {
            chosen = allowed.stream().filter(b -> b.getId().equals(requestedBranchId)).findFirst()
                    .orElseThrow(() -> new BusinessException("BRANCH_NOT_ALLOWED",
                            "Bạn không được phân công làm việc tại chi nhánh này", HttpStatus.FORBIDDEN));
        }

        if (openShift != null && !openShift.getBranchId().equals(chosen.getId())) {
            String where = branchRepository.findById(openShift.getBranchId()).map(Branch::getName)
                    .orElse("chi nhánh #" + openShift.getBranchId());
            throw new BusinessException("SHIFT_OPEN_AT_OTHER_BRANCH",
                    "Bạn đang có " + openShift.getWorkShift().getName() + " chưa đóng tại " + where
                            + ". Hãy đăng nhập vào chi nhánh đó để đóng ca, hoặc nhờ quản trị đóng hộ.",
                    HttpStatus.CONFLICT, Map.of("openShiftBranchId", openShift.getBranchId()));
        }

        String token = tokenProvider.generateToken(userDetails, chosen.getId(), user.getRole().name(), user.getFullName());
        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .userId(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .branchId(chosen.getId())
                .branchName(chosen.getName())
                .build();
    }
}
