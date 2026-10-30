package com.pos.customer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findByPhone(String phone);

    @Query("SELECT c FROM Customer c WHERE " +
           "LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "c.phone LIKE CONCAT('%', :query, '%')")
    List<Customer> searchCustomers(@Param("query") String query);

    @Modifying
    @Query("UPDATE Customer c SET c.points = c.points + :pts WHERE c.id = :id")
    int addPoints(@Param("id") Long id, @Param("pts") int pts);

    /** Trừ điểm chỉ khi đủ số dư; trả 0 nếu không đủ (không để điểm âm khi hai quầy đổi cùng lúc) */
    @Modifying
    @Query("UPDATE Customer c SET c.points = c.points - :pts WHERE c.id = :id AND c.points >= :pts")
    int deductPointsIfEnough(@Param("id") Long id, @Param("pts") int pts);

    /** Đọc số dư mới nhất từ DB (sau các câu UPDATE trên) */
    @Query("SELECT c.points FROM Customer c WHERE c.id = :id")
    Integer findPointsById(@Param("id") Long id);
}

