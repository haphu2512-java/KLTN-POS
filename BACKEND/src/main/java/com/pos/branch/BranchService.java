package com.pos.branch;

import com.pos.common.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BranchService {

    private final BranchRepository branchRepository;

    public List<Branch> getAllBranches() {
        return branchRepository.findAll();
    }

    public List<Branch> getActiveBranches() {
        return branchRepository.findByActiveTrue();
    }

    public Branch getBranchById(Long id) {
        return branchRepository.findById(id)
                .orElseThrow(() -> new BusinessException("BRANCH_NOT_FOUND", "Không tìm thấy chi nhánh với ID: " + id));
    }

    @Transactional
    public Branch createBranch(Branch branch) {
        branch.setId(null);
        branch.setActive(true);
        return branchRepository.save(branch);
    }

    @Transactional
    public Branch updateBranch(Long id, Branch updated) {
        Branch existing = getBranchById(id);
        existing.setName(updated.getName());
        existing.setAddress(updated.getAddress());
        existing.setPhone(updated.getPhone());
        if (updated.getActive() != null) {
            existing.setActive(updated.getActive());
        }
        return branchRepository.save(existing);
    }
}
