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
        return customerRepository.findByPhone(phone.trim())
                .orElseThrow(() -> new BusinessException("CUSTOMER_NOT_FOUND", "Không tìm thấy khách hàng với SĐT: " + phone));
    }

    @Transactional
    public Customer createCustomer(Customer customer) {
        if (StringUtils.hasText(customer.getPhone())) {
            if (customerRepository.findByPhone(customer.getPhone().trim()).isPresent()) {
                throw new BusinessException("PHONE_ALREADY_EXISTS", "Số điện thoại này đã được đăng ký cho khách hàng khác");
            }
        }
        customer.setId(null);
        if (customer.getPoints() == null) {
            customer.setPoints(0);
        }
        return customerRepository.save(customer);
    }
}
