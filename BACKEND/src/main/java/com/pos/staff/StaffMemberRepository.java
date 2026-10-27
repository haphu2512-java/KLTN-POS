package com.pos.staff;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StaffMemberRepository extends JpaRepository<StaffMember, Long> {

    List<StaffMember> findByBranchIdOrderByActiveDescFullNameAsc(Long branchId);

    List<StaffMember> findAllByOrderByBranchIdAscFullNameAsc();
}
