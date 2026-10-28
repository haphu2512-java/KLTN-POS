package com.pos.config;

import com.pos.branch.Branch;
import com.pos.branch.BranchRepository;
import com.pos.inventory.Inventory;
import com.pos.inventory.InventoryRepository;
import com.pos.product.BarcodeSyncService;
import com.pos.product.Product;
import com.pos.product.ProductRepository;
import com.pos.purchase.Supplier;
import com.pos.purchase.SupplierRepository;
import com.pos.shift.WorkShift;
import com.pos.shift.WorkShiftRepository;
import com.pos.user.Role;
import com.pos.user.User;
import com.pos.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final WorkShiftRepository workShiftRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final BarcodeSyncService barcodeSyncService;

    @Value("${app.seed.default-password:}")
    private String seedPassword;

    @Value("${app.barcode.sync-on-startup:true}")
    private boolean syncBarcodesOnStartup;

    @Override
    public void run(String... args) {
        seedWorkShifts();
        migrateSingleBranchColumn();

        if (branchRepository.count() == 0) {
            if (seedPassword == null || seedPassword.isBlank()) {
                throw new IllegalStateException("Database trống nhưng chưa đặt SEED_DEFAULT_PASSWORD trong file .env "
                        + "để tạo tài khoản mẫu admin/cashier (xem .env.example).");
            }
            log.info("Khởi tạo dữ liệu mẫu ban đầu cho hệ thống POS...");

            // 1. Tạo Chi nhánh
            Branch branch1 = branchRepository.save(Branch.builder()
                    .name("Chi nhánh Quận 1 (Trụ sở)")
                    .address("128 Nguyễn Trãi, P. Bến Thành, Q.1, TP.HCM")
                    .phone("02838334455")
                    .active(true)
                    .build());

            Branch branch2 = branchRepository.save(Branch.builder()
                    .name("Chi nhánh Quận 3")
                    .address("245 Cách Mạng Tháng 8, P.4, Q.3, TP.HCM")
                    .phone("02839112233")
                    .active(true)
                    .build());

            // 2. Tạo Tài khoản mẫu (mật khẩu lấy từ SEED_DEFAULT_PASSWORD)
            userRepository.save(User.builder()
                    .username("admin")
                    .password(passwordEncoder.encode(seedPassword))
                    .fullName("Quản Trị Viên")
                    .role(Role.ADMIN)
                    .active(true)
                    .build());

            userRepository.save(User.builder()
                    .username("cashier")
                    .password(passwordEncoder.encode(seedPassword))
                    .fullName("Nguyễn Văn A")
                    .role(Role.CASHIER)
                    .branchIds(new HashSet<>(Set.of(branch1.getId())))
                    .active(true)
                    .build());

            // 3. Tạo Nhà cung cấp
            supplierRepository.saveAll(List.of(
                    Supplier.builder().name("Công ty CP Sữa Việt Nam (Vinamilk)").phone("02854155555").address("Q.7, TP.HCM").build(),
                    Supplier.builder().name("Công ty TNHH Nước Giải Khát Coca-Cola").phone("02838961000").address("Q.Thủ Đức, TP.HCM").build(),
                    Supplier.builder().name("Công ty CP Acecook Việt Nam").phone("02838154064").address("Q.Tân Phú, TP.HCM").build(),
                    Supplier.builder().name("Công ty TNHH Dầu Thực Vật Cái Lân (Simply)").phone("02438641155").address("Nhà Bè, TP.HCM").build(),
                    Supplier.builder().name("Công ty TNHH Quốc Tế Unilever Việt Nam").phone("02854135686").address("Q.7, TP.HCM").build()
            ));

            // 4. Tạo Sản phẩm & Khởi tạo Tồn kho
            List<Product> sampleProducts = List.of(
                    Product.builder().barcode("8934567890123").name("Sữa tươi tiệt trùng Vinamilk 100% Không Đường 1L").category("Sữa & Bơ").unit("Hộp").costPrice(new BigDecimal("28000")).salePrice(new BigDecimal("36000")).build(),
                    Product.builder().barcode("8935001234567").name("Bánh mì sandwich bơ sữa Kinh Đô 275g").category("Bánh kẹo").unit("Gói").costPrice(new BigDecimal("19000")).salePrice(new BigDecimal("24000")).build(),
                    Product.builder().barcode("8936009876543").name("Nước ngọt Coca-Cola Sleek 320ml").category("Nước giải khát").unit("Lon").costPrice(new BigDecimal("8500")).salePrice(new BigDecimal("11000")).build(),
                    Product.builder().barcode("8934673123456").name("Mì gói Hảo Hảo tôm chua cay 75g").category("Thực phẩm khô").unit("Gói").costPrice(new BigDecimal("3800")).salePrice(new BigDecimal("5000")).build(),
                    Product.builder().barcode("8935212345678").name("Dầu ăn đậu nành Simply 1L").category("Gia vị & Dầu ăn").unit("Chai").costPrice(new BigDecimal("48000")).salePrice(new BigDecimal("59000")).build(),
                    Product.builder().barcode("8935049500018").name("Nước tương Maggi Đậm Đặc 700ml").category("Gia vị & Dầu ăn").unit("Chai").costPrice(new BigDecimal("26000")).salePrice(new BigDecimal("32000")).build(),
                    Product.builder().barcode("8934822001017").name("Khăn ướt em bé không mùi Mamamy 80 tờ").category("Hóa mỹ phẩm").unit("Gói").costPrice(new BigDecimal("32000")).salePrice(new BigDecimal("42000")).build(),
                    Product.builder().barcode("8934602001234").name("Cà phê hòa tan G7 3in1 hộp 18 gói x 16g").category("Cà phê & Trà").unit("Hộp").costPrice(new BigDecimal("48000")).salePrice(new BigDecimal("58000")).build()
            );

            int[] initialStocks = {45, 18, 120, 240, 4, 14, 0, 2};

            for (int i = 0; i < sampleProducts.size(); i++) {
                Product savedProd = productRepository.save(sampleProducts.get(i));
                inventoryRepository.save(Inventory.builder()
                        .product(savedProd)
                        .branch(branch1)
                        .quantity(initialStocks[i])
                        .minThreshold(5)
                        .build());

                inventoryRepository.save(Inventory.builder()
                        .product(savedProd)
                        .branch(branch2)
                        .quantity(initialStocks[i] / 2)
                        .minThreshold(5)
                        .build());
            }

            log.info("Đã khởi tạo thành công dữ liệu mẫu cho hệ thống POS!");
        }

        if (syncBarcodesOnStartup) {
            barcodeSyncService.syncLegacyBarcodes();
        }
    }

    /**
     * Database cũ lưu một chi nhánh cố định ở cột users.branch_id. Nay nhân viên được phân công
     * nhiều chi nhánh (bảng user_branch) và chọn chi nhánh lúc đăng nhập: chuyển giá trị cũ sang
     * bảng mới rồi xóa ở cột cũ để lần chạy sau không chép lại. Database mới không có cột này thì bỏ qua.
     */
    void migrateSingleBranchColumn() {
        try {
            int moved = jdbcTemplate.update("INSERT INTO user_branch (user_id, branch_id) "
                    + "SELECT u.id, u.branch_id FROM users u WHERE u.branch_id IS NOT NULL AND u.role <> 'ADMIN' "
                    + "AND NOT EXISTS (SELECT 1 FROM user_branch ub WHERE ub.user_id = u.id AND ub.branch_id = u.branch_id)");
            jdbcTemplate.update("UPDATE users SET branch_id = NULL WHERE branch_id IS NOT NULL");
            if (moved > 0) {
                log.info("Đã chuyển chi nhánh cố định của {} nhân viên sang phân công chi nhánh", moved);
            }
        } catch (DataAccessException e) {
            log.debug("Không có cột users.branch_id cũ, bỏ qua bước chuyển dữ liệu: {}", e.getMessage());
        }
    }

    /** Lịch ca mẫu chung ban đầu (5 ca) khi chưa có ca nào; ADMIN tự đổi số ca, giờ ca hoặc tạo lịch riêng từng chi nhánh qua /api/shifts/work-shifts */
    private void seedWorkShifts() {
        if (workShiftRepository.count() > 0) return;
        String[][] shifts = {
                {"Ca 1 (sáng sớm)", "06:00", "09:00"},
                {"Ca 2 (sáng)", "09:00", "12:00"},
                {"Ca 3 (trưa)", "12:00", "15:00"},
                {"Ca 4 (chiều)", "15:00", "18:00"},
                {"Ca 5 (tối)", "18:00", "22:00"},
        };
        for (int i = 0; i < shifts.length; i++) {
            workShiftRepository.save(WorkShift.builder()
                    .name(shifts[i][0])
                    .startTime(LocalTime.parse(shifts[i][1]))
                    .endTime(LocalTime.parse(shifts[i][2]))
                    .sortOrder(i + 1)
                    .active(true)
                    .build());
        }
        log.info("Đã tạo {} ca làm việc mặc định", shifts.length);
    }
}
