package com.pos.forecast;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ForecastResultRepository extends JpaRepository<ForecastResult, Long> {

    List<ForecastResult> findByBranchIdOrderByUrgencyAscProductProductNameAsc(Long branchId);

    /**
     * Xóa toàn bộ kết quả dự báo cũ của 1 chi nhánh trước khi ghi batch mới.
     */
    @Modifying
    @Query("DELETE FROM ForecastResult fr WHERE fr.branch.id = :branchId")
    void deleteByBranchId(@Param("branchId") Long branchId);
}
