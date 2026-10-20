package com.pos.shift;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

/**
 * Ca làm việc cố định trong ngày (mặc định 5 ca, ADMIN chỉnh được giờ).
 * Ca có giờ kết thúc nhỏ hơn giờ bắt đầu là ca qua đêm (vd. 22:00 - 06:00).
 */
@Entity
@Table(name = "work_shift")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkShift {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    /** Giờ {@code time} có nằm trong ca này không (tính cả ca qua đêm) */
    public boolean contains(LocalTime time) {
        if (startTime.isBefore(endTime)) {
            return !time.isBefore(startTime) && time.isBefore(endTime);
        }
        return !time.isBefore(startTime) || time.isBefore(endTime);
    }
}
