package com.pos.branch;

import com.pos.user.Role;
import com.pos.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * Ai được làm việc ở chi nhánh nào: ADMIN mọi chi nhánh đang hoạt động,
 * các vai trò khác chỉ những chi nhánh đã được gán và còn hoạt động.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BranchAccessService {

    private final BranchRepository branchRepository;

    public List<Branch> allowedBranches(User user) {
        List<Branch> active = branchRepository.findByActiveTrue();
        return active.stream()
                .filter(b -> user.getRole() == Role.ADMIN || user.getBranchIds().contains(b.getId()))
                .sorted(Comparator.comparing(Branch::getId))
                .toList();
    }

    public boolean canUse(User user, Long branchId) {
        if (branchId == null) {
            return false;
        }
        if (user.getRole() != Role.ADMIN && !user.getBranchIds().contains(branchId)) {
            return false;
        }
        return branchRepository.findById(branchId).map(b -> Boolean.TRUE.equals(b.getActive())).orElse(false);
    }
}
