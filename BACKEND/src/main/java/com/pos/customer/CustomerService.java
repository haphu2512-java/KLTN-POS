package com.pos.customer;

import com.pos.common.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    public List<Customer> getAllCustomers(String search) {
        if (StringUtils.hasText(search)) {
            return customerRepository.searchCustomers(search.trim());
        }
        return customerRepository.findAll();
    }

    public Customer getCustomerById(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new BusinessException("CUSTOMER_NOT_FOUND", "Không tìm thấy khách hàng với ID: " + id));
    }

    public Customer getCustomerByPhone(String phone) {
        String normalized = normalizePhone(phone);
        return customerRepository.findByPhone(normalized)
                .orElseThrow(() -> new BusinessException("CUSTOMER_NOT_FOUND", "Không tìm thấy khách hàng với SĐT: " + phone));
    }

    @Transactional
    public Customer createCustomer(CustomerRequest req) {
        String phone = normalizePhone(req.phone());
        ensurePhoneFree(phone, null);
        return customerRepository.save(Customer.builder()
                .name(req.name().trim())
                .phone(phone)
                .address(StringUtils.hasText(req.address()) ? req.address().trim() : null)
                .build());
    }

    @Transactional
    public Customer updateCustomer(Long id, CustomerRequest req) {
        Customer customer = getCustomerById(id);
        String phone = normalizePhone(req.phone());
        ensurePhoneFree(phone, id);
        customer.setName(req.name().trim());
        customer.setPhone(phone);
        customer.setAddress(StringUtils.hasText(req.address()) ? req.address().trim() : null);
        return customerRepository.save(customer);
    }

    private void ensurePhoneFree(String phone, Long selfId) {
        if (phone == null) return;
        customerRepository.findByPhone(phone)
                .filter(other -> !other.getId().equals(selfId))
                .ifPresent(other -> {
                    throw new BusinessException("PHONE_ALREADY_EXISTS", "Số điện thoại này đã được đăng ký cho khách hàng khác");
                });
    }

    /**
     * Chuẩn hóa SĐT để một khách không có hai thẻ: bỏ khoảng trắng, dấu chấm, gạch; +84/84 đầu số thành 0.
     * Trả null nếu rỗng.
     */
    static String normalizePhone(String phone) {
        if (!StringUtils.hasText(phone)) return null;
        String digits = phone.trim().replaceAll("[\\s.\\-()]", "");
        if (digits.startsWith("+84")) {
            digits = "0" + digits.substring(3);
        } else if (digits.startsWith("84") && digits.length() == 11) {
            digits = "0" + digits.substring(2);
        }
        if (!digits.matches("\\d{9,11}")) {
            throw new BusinessException("INVALID_PHONE", "Số điện thoại không hợp lệ: " + phone);
        }
        return digits;
    }
}
