package com.pos.loyalty;

import com.pos.common.BusinessException;
import com.pos.customer.Customer;
import com.pos.customer.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Cộng, trừ và hoàn điểm thành viên. Số dư (customer.points) đổi bằng câu UPDATE nguyên tử,
 * mỗi lần đổi đều ghi một dòng vào sổ điểm (point_transaction).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LoyaltyService {

    private final CustomerRepository customerRepository;
    private final PointTransactionRepository pointTransactionRepository;
    private final LoyaltyPolicy policy;

    public LoyaltyPolicy getPolicy() {
        return policy;
    }

    public List<PointTransaction> history(Long customerId) {
        return pointTransactionRepository.findByCustomerIdOrderByCreatedAtDescIdDesc(customerId);
    }

    /** Cộng điểm mua hàng cho hóa đơn */
    @Transactional
    public void earn(Customer customer, Long invoiceId, int points, Long actorId) {
        if (points <= 0) return;
        customerRepository.addPoints(customer.getId(), points);
        record(customer, invoiceId, PointType.EARN, points, null, actorId);
    }

    /**
     * Trừ điểm khách dùng để thanh toán. Câu UPDATE chỉ trừ khi đủ điểm,
     * nên hai quầy cùng đổi điểm của một khách không làm số dư âm.
     */
    @Transactional
    public void redeem(Customer customer, Long invoiceId, int points, Long actorId) {
        if (points <= 0) return;
        if (customerRepository.deductPointsIfEnough(customer.getId(), points) == 0) {
            throw new BusinessException("INSUFFICIENT_POINTS", "Khách không đủ điểm để đổi", HttpStatus.CONFLICT);
        }
        record(customer, invoiceId, PointType.REDEEM, -points, null, actorId);
    }

    /**
     * Hoàn trả hóa đơn: trả lại điểm khách đã dùng, rồi thu hồi điểm đã cộng.
     * Nếu khách đã tiêu bớt điểm được cộng thì chỉ thu hồi phần còn lại (số dư không âm).
     */
    @Transactional
    public void reverseForRefund(Customer customer, Long invoiceId, int redeemed, int earned, Long actorId) {
        if (redeemed > 0) {
            customerRepository.addPoints(customer.getId(), redeemed);
            record(customer, invoiceId, PointType.REFUND_REDEEM, redeemed, "Trả lại điểm đã dùng", actorId);
        }
        if (earned > 0) {
            int deduct = Math.min(earned, currentBalance(customer));
            if (deduct > 0 && customerRepository.deductPointsIfEnough(customer.getId(), deduct) > 0) {
                String note = deduct < earned ? "Thu hồi " + deduct + "/" + earned + " điểm (khách đã dùng bớt)" : null;
                record(customer, invoiceId, PointType.REFUND_EARN, -deduct, note, actorId);
            }
        }
    }

    /** Quản lý điều chỉnh điểm tay; bắt buộc có lý do để tra soát */
    @Transactional
    public Customer adjust(Long customerId, int delta, String reason, Long actorId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new BusinessException("CUSTOMER_NOT_FOUND", "Không tìm thấy khách hàng với ID: " + customerId));
        if (delta == 0) {
            throw new BusinessException("INVALID_POINTS", "Số điểm điều chỉnh phải khác 0");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("ADJUST_REASON_REQUIRED", "Nhập lý do điều chỉnh điểm");
        }
        if (delta > 0) {
            customerRepository.addPoints(customerId, delta);
        } else if (customerRepository.deductPointsIfEnough(customerId, -delta) == 0) {
            throw new BusinessException("INSUFFICIENT_POINTS", "Không thể trừ nhiều hơn số điểm khách đang có");
        }
        record(customer, null, PointType.ADJUST, delta, reason.trim(), actorId);
        return customer;
    }

    private int currentBalance(Customer customer) {
        Integer balance = customerRepository.findPointsById(customer.getId());
        return balance != null ? balance : (customer.getPoints() != null ? customer.getPoints() : 0);
    }

    private void record(Customer customer, Long invoiceId, PointType type, int points, String note, Long actorId) {
        int balance = currentBalance(customer);
        customer.setPoints(balance);
        pointTransactionRepository.save(PointTransaction.builder()
                .customerId(customer.getId())
                .invoiceId(invoiceId)
                .type(type)
                .points(points)
                .balanceAfter(balance)
                .note(note)
                .createdBy(actorId)
                .build());
    }
}
