package com.pos.shift;

import java.time.LocalDate;

/** Bộ lọc lịch sử ca; giá trị null là không lọc theo tiêu chí đó */
public record ShiftFilter(Long branchId, Long staffId, LocalDate from, LocalDate to, ShiftStatus status) {
}
