package com.pos.config;

import com.pos.user.Role;
import com.pos.user.User;
import com.pos.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Database cũ có cột users.branch_id: khởi động lại thì chi nhánh cố định chuyển thành phân công chi nhánh */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:migratedb;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
class BranchColumnMigrationTest {

    @Autowired
    private DatabaseInitializer initializer;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("Chuyển users.branch_id sang user_branch một lần, bỏ qua ADMIN, chạy lại không chép lại")
    void movesLegacyBranchColumn() {
        jdbcTemplate.execute("ALTER TABLE users ADD COLUMN branch_id BIGINT");
        User old = userRepository.save(User.builder().username("legacy_cashier").password("x")
                .fullName("Thu ngân cũ").role(Role.STAFF).active(true).build());
        User oldAdmin = userRepository.save(User.builder().username("legacy_admin").password("x")
                .fullName("Quản trị cũ").role(Role.ADMIN).active(true).build());
        jdbcTemplate.update("UPDATE users SET branch_id = 2 WHERE id IN (?, ?)", old.getId(), oldAdmin.getId());

        initializer.migrateSingleBranchColumn();
        initializer.migrateSingleBranchColumn();

        assertEquals(Set.of(2L), userRepository.findById(old.getId()).orElseThrow().getBranchIds());
        assertTrue(userRepository.findById(oldAdmin.getId()).orElseThrow().getBranchIds().isEmpty());
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE branch_id IS NOT NULL", Integer.class));
    }

    @Test
    @DisplayName("Gộp vai trò cũ Thu ngân / Thủ kho thành STAFF, giữ nguyên ADMIN / MANAGER")
    void mergesLegacyRolesIntoStaff() {
        jdbcTemplate.execute("ALTER TABLE users ALTER COLUMN role SET DATA TYPE VARCHAR(20)");
        User cashier = userRepository.save(User.builder().username("old_cashier").password("x")
                .fullName("Thu ngân cũ").role(Role.STAFF).active(true).build());
        User warehouse = userRepository.save(User.builder().username("old_warehouse").password("x")
                .fullName("Thủ kho cũ").role(Role.STAFF).active(true).build());
        User manager = userRepository.save(User.builder().username("old_manager").password("x")
                .fullName("Quản lý").role(Role.MANAGER).active(true).build());
        jdbcTemplate.update("UPDATE users SET role = 'CASHIER' WHERE id = ?", cashier.getId());
        jdbcTemplate.update("UPDATE users SET role = 'WAREHOUSE' WHERE id = ?", warehouse.getId());

        initializer.migrateRolesToStaff();
        initializer.migrateRolesToStaff();

        assertEquals(Role.STAFF, userRepository.findById(cashier.getId()).orElseThrow().getRole());
        assertEquals(Role.STAFF, userRepository.findById(warehouse.getId()).orElseThrow().getRole());
        assertEquals(Role.MANAGER, userRepository.findById(manager.getId()).orElseThrow().getRole());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE role IN ('CASHIER', 'WAREHOUSE')", Integer.class));
    }
}
