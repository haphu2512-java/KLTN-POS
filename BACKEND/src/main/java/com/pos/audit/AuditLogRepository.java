package com.pos.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("SELECT a FROM AuditLog a WHERE a.createdAt >= :from AND a.createdAt < :to "
            + "AND (:action IS NULL OR a.action = :action) "
            + "AND (:branchId IS NULL OR a.branchId = :branchId) "
            + "AND (:username IS NULL OR LOWER(a.actorUsername) LIKE LOWER(CONCAT('%', :username, '%'))) "
            + "ORDER BY a.createdAt DESC, a.id DESC")
    Page<AuditLog> search(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                          @Param("action") AuditAction action, @Param("branchId") Long branchId,
                          @Param("username") String username, Pageable pageable);
}
