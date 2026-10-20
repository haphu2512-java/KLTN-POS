package com.pos.shift;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

class ShiftServiceTest {

    private WorkShift shift(String start, String end) {
        return WorkShift.builder().name("Ca").startTime(LocalTime.parse(start)).endTime(LocalTime.parse(end))
                .sortOrder(1).active(true).build();
    }

    @Test
    @DisplayName("Ca trong ngày: tính cả giờ bắt đầu, không tính giờ kết thúc")
    void dayShiftBoundaries() {
        WorkShift ws = shift("06:00", "09:00");
        assertTrue(ws.contains(LocalTime.parse("06:00")));
        assertTrue(ws.contains(LocalTime.parse("08:59")));
        assertFalse(ws.contains(LocalTime.parse("09:00")));
        assertFalse(ws.contains(LocalTime.parse("05:59")));
    }

    @Test
    @DisplayName("Ca qua đêm 22:00 - 06:00 chứa cả trước và sau nửa đêm, ngày ca là ngày bắt đầu")
    void overnightShift() {
        WorkShift ws = shift("22:00", "06:00");
        assertTrue(ws.contains(LocalTime.parse("23:30")));
        assertTrue(ws.contains(LocalTime.parse("02:00")));
        assertFalse(ws.contains(LocalTime.parse("12:00")));

        assertEquals(LocalDate.of(2026, 10, 19),
                ShiftService.shiftDateOf(ws, LocalDateTime.of(2026, 10, 20, 2, 0)));
        assertEquals(LocalDate.of(2026, 10, 20),
                ShiftService.shiftDateOf(ws, LocalDateTime.of(2026, 10, 20, 22, 5)));
        assertEquals(LocalDate.of(2026, 10, 20),
                ShiftService.shiftDateOf(shift("06:00", "09:00"), LocalDateTime.of(2026, 10, 20, 7, 0)));
    }
}
