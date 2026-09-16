package com.pos.config;

import com.pos.branch.Branch;
import com.pos.branch.BranchRepository;
import com.pos.inventory.Inventory;
import com.pos.inventory.InventoryRepository;
import com.pos.product.Product;
import com.pos.product.ProductRepository;
import com.pos.purchase.Supplier;
import com.pos.purchase.SupplierRepository;
import com.pos.user.Role;
import com.pos.user.User;
import com.pos.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (branchRepository.count() == 0) {
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

            // 2. Tạo Tài khoản mẫu (Mật khẩu mặc định: 123456)
            userRepository.save(User.builder()
                    .username("admin")
                    .password(passwordEncoder.encode("123456"))
                    .fullName("Quản Trị Viên")
                    .role(Role.ADMIN)
                    .branchId(branch1.getId())
                    .active(true)
                    .build());

            userRepository.save(User.builder()
                    .username("cashier")
                    .password(passwordEncoder.encode("123456"))
                    .fullName("Nguyễn Văn A")
                    .role(Role.CASHIER)
                    .branchId(branch1.getId())
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
    }
}
