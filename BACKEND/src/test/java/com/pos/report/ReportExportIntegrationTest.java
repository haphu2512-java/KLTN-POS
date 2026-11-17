package com.pos.report;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.ByteArrayInputStream;
import com.pos.inventory.InventoryRepository;
import com.pos.product.ProductRepository;
import com.pos.security.CustomUserDetailsService;
import com.pos.security.JwtTokenProvider;
import com.pos.support.TestAuth;
import com.pos.user.User;
import com.pos.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:posdb;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
@AutoConfigureMockMvc
class ReportExportIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JwtTokenProvider tokenProvider;
    @Autowired private CustomUserDetailsService userDetailsService;
    @Autowired private UserRepository userRepository;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private ProductRepository productRepository;

    private String tokenFor(String username) {
        User user = userRepository.findByUsername(username).orElseThrow();
        UserDetails details = userDetailsService.loadUserByUsername(username);
        return "Bearer " + tokenProvider.generateToken(details, TestAuth.branchOf(user), user.getRole().name(), user.getFullName());
    }

    @Test
    @DisplayName("Xuất Excel: file .xlsx đủ các trang, chỉ quản lý/quản trị được tải")
    void exportsWorkbook() throws Exception {
        byte[] file = mvc.perform(get("/api/reports/export?days=7&branchId=1").header("Authorization", tokenFor("admin")))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Content-Disposition", org.hamcrest.Matchers.containsString(".xlsx")))
                .andReturn().getResponse().getContentAsByteArray();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            assertThat(wb.getSheet("Tổng quan")).isNotNull();
            assertThat(wb.getSheet("Theo ngày").getLastRowNum()).isGreaterThanOrEqualTo(3 + 7);
            assertThat(wb.getSheet("Sản phẩm bán chạy")).isNotNull();
            assertThat(wb.getSheet("Hóa đơn")).isNotNull();
            assertThat(wb.getSheet("Hao hụt kho")).isNotNull();
            assertThat(wb.getSheet("Tổng quan").getRow(4).getCell(0).getStringCellValue()).startsWith("Doanh thu thuần");
        }
        mvc.perform(get("/api/reports/export").header("Authorization", tokenFor("cashier")))
                .andExpect(status().isForbidden());
    }
}
