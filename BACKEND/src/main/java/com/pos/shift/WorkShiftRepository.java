package com.pos.shift;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkShiftRepository extends JpaRepository<WorkShift, Long> {
    List<WorkShift> findAllByOrderBySortOrderAsc();
    List<WorkShift> findByActiveTrueOrderBySortOrderAsc();
}
