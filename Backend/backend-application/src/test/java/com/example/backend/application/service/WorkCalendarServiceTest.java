package com.example.backend.application.service;

import com.example.backend.infrastructure.persistence.entity.SlaCalendarSpecialDate;
import com.example.backend.infrastructure.persistence.entity.SlaWorkCalendar;
import com.example.backend.infrastructure.persistence.mapper.SlaCalendarSpecialDateMapper;
import com.example.backend.infrastructure.persistence.mapper.SlaWorkCalendarMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkCalendarServiceTest {

    @Mock
    private SlaWorkCalendarMapper slaWorkCalendarMapper;

    @Mock
    private SlaCalendarSpecialDateMapper slaCalendarSpecialDateMapper;

    @InjectMocks
    private WorkCalendarService workCalendarService;

    private static final Long CALENDAR_ID = 1L;
    private static final String SEGMENTS_JSON = "[{\"start\":\"09:00\",\"end\":\"18:00\"}]";
    private static final String MULTI_SEGMENTS_JSON =
            "[{\"start\":\"09:00\",\"end\":\"12:00\"},{\"start\":\"13:00\",\"end\":\"18:00\"}]";

    @BeforeEach
    void setUp() {
        SlaWorkCalendar calendar = new SlaWorkCalendar();
        calendar.setId(CALENDAR_ID);
        calendar.setCalendarName("标准日历");
        calendar.setWorkDays("1,2,3,4,5");
        calendar.setWorkTimeSegments(SEGMENTS_JSON);
        calendar.setIsActive(1);

        when(slaWorkCalendarMapper.findAllActive()).thenReturn(List.of(calendar));
        when(slaCalendarSpecialDateMapper.findByCalendarId(anyLong())).thenReturn(Collections.emptyList());

        workCalendarService.refreshCache();
    }

    @Test
    void testIsServiceTimeWeekdayWorkingHours() {
        LocalDateTime mon10am = LocalDateTime.of(2026, 6, 1, 10, 0);
        assertTrue(workCalendarService.isServiceTime(mon10am, CALENDAR_ID));
    }

    @Test
    void testIsServiceTimeWeekendNotServiceTime() {
        LocalDateTime sat10am = LocalDateTime.of(2026, 6, 6, 10, 0);
        assertFalse(workCalendarService.isServiceTime(sat10am, CALENDAR_ID));
    }

    @Test
    void testIsServiceTimeOutsideWorkingHours() {
        LocalDateTime mon8am = LocalDateTime.of(2026, 6, 1, 8, 0);
        assertFalse(workCalendarService.isServiceTime(mon8am, CALENDAR_ID));
    }

    @Test
    void testCalcEffectiveDeadlineSameDayWithinWindow() {
        LocalDateTime mon10am = LocalDateTime.of(2026, 6, 1, 10, 0);
        LocalDateTime deadline = workCalendarService.calcEffectiveDeadline(mon10am, 60, CALENDAR_ID);
        assertEquals(LocalDateTime.of(2026, 6, 1, 11, 0), deadline);
    }

    @Test
    void testCalcEffectiveDeadlineCrossDayEndOfDay() {
        LocalDateTime fri1740 = LocalDateTime.of(2026, 6, 5, 17, 40);
        LocalDateTime deadline = workCalendarService.calcEffectiveDeadline(fri1740, 60, CALENDAR_ID);
        assertEquals(LocalDateTime.of(2026, 6, 8, 9, 40), deadline);
    }

    @Test
    void testCalcEffectiveDeadlineCrossHoliday() {
        LocalDate holidayDate = LocalDate.of(2026, 6, 4);
        SlaCalendarSpecialDate holiday = new SlaCalendarSpecialDate();
        holiday.setId(100L);
        holiday.setCalendarId(CALENDAR_ID);
        holiday.setSpecialDate(holidayDate);
        holiday.setDayType("HOLIDAY");
        holiday.setWorkSegments(null);
        holiday.setDescription("端午节");

        when(slaCalendarSpecialDateMapper.findByCalendarId(CALENDAR_ID)).thenReturn(List.of(holiday));
        workCalendarService.refreshCache();

        LocalDateTime wed1700 = LocalDateTime.of(2026, 6, 3, 17, 0);
        LocalDateTime deadline = workCalendarService.calcEffectiveDeadline(wed1700, 120, CALENDAR_ID);
        assertEquals(LocalDateTime.of(2026, 6, 5, 10, 0), deadline);
    }

    @Test
    void testCalcEffectiveDurationWithinWorkingHours() {
        LocalDateTime mon10am = LocalDateTime.of(2026, 6, 1, 10, 0);
        LocalDateTime mon12pm = LocalDateTime.of(2026, 6, 1, 12, 0);
        long seconds = workCalendarService.calcEffectiveDuration(mon10am, mon12pm, CALENDAR_ID);
        assertEquals(7200L, seconds);
    }

    @Test
    void testCalcEffectiveDurationCrossNonWorkingTime() {
        LocalDateTime fri5pm = LocalDateTime.of(2026, 6, 5, 17, 0);
        LocalDateTime mon10am = LocalDateTime.of(2026, 6, 8, 10, 0);
        long seconds = workCalendarService.calcEffectiveDuration(fri5pm, mon10am, CALENDAR_ID);
        assertEquals(7200L, seconds);
    }

    @Test
    void testGetNextServiceTimeAfterHours() {
        LocalDateTime mon7pm = LocalDateTime.of(2026, 6, 1, 19, 0);
        LocalDateTime nextService = workCalendarService.getNextServiceTime(mon7pm, CALENDAR_ID);
        assertEquals(LocalDateTime.of(2026, 6, 2, 9, 0), nextService);
    }

    @Test
    void testGetNextServiceTimeWeekend() {
        LocalDateTime sat10am = LocalDateTime.of(2026, 6, 6, 10, 0);
        LocalDateTime nextService = workCalendarService.getNextServiceTime(sat10am, CALENDAR_ID);
        assertEquals(LocalDateTime.of(2026, 6, 8, 9, 0), nextService);
    }

    // ======================== 多时段算法测试 ========================

    private void setupMultiSegmentCalendar() {
        SlaWorkCalendar multiSegCalendar = new SlaWorkCalendar();
        multiSegCalendar.setId(CALENDAR_ID);
        multiSegCalendar.setCalendarName("多时段日历");
        multiSegCalendar.setWorkDays("1,2,3,4,5");
        multiSegCalendar.setWorkTimeSegments(MULTI_SEGMENTS_JSON);
        multiSegCalendar.setIsActive(1);

        when(slaWorkCalendarMapper.findAllActive()).thenReturn(List.of(multiSegCalendar));
        workCalendarService.refreshCache();
    }

    @Test
    void testCalcEffectiveDeadlineMultiSegmentNoLunchBreak() {
        setupMultiSegmentCalendar();

        // Mon 2026-06-01 10:00 + 240min(4h)
        // 10:00→12:00 consume 2h, 13:00→15:00 consume 2h → deadline = Mon 15:00
        LocalDateTime mon10am = LocalDateTime.of(2026, 6, 1, 10, 0);
        LocalDateTime deadline = workCalendarService.calcEffectiveDeadline(mon10am, 240, CALENDAR_ID);
        assertEquals(LocalDateTime.of(2026, 6, 1, 15, 0), deadline);
    }

    @Test
    void testCalcEffectiveDeadlineMultiSegmentCrossDay() {
        setupMultiSegmentCalendar();

        // Mon 2026-06-01 10:00 + 540min(9h)
        // Day1: 10:00→12:00=2h, 13:00→18:00=5h → remaining 2h
        // Day2: Tue 09:00+2h=11:00
        LocalDateTime mon10am = LocalDateTime.of(2026, 6, 1, 10, 0);
        LocalDateTime deadline = workCalendarService.calcEffectiveDeadline(mon10am, 540, CALENDAR_ID);
        assertEquals(LocalDateTime.of(2026, 6, 2, 11, 0), deadline);
    }

    @Test
    void testCalcEffectiveDeadlineFridayAfternoonCrossWeekend() {
        setupMultiSegmentCalendar();

        // Fri 2026-06-05 17:00 + 480min(8h)
        // Day1: 17:00→18:00=1h → remaining 7h
        // Mon 09:00→12:00=3h, skip lunch, 13:00→17:00=4h → Mon 17:00
        LocalDateTime fri17 = LocalDateTime.of(2026, 6, 5, 17, 0);
        LocalDateTime deadline = workCalendarService.calcEffectiveDeadline(fri17, 480, CALENDAR_ID);
        assertEquals(LocalDateTime.of(2026, 6, 8, 17, 0), deadline);
    }

    @Test
    void testCalcEffectiveDeadlineCreatedOnNonServiceTime() {
        setupMultiSegmentCalendar();

        // Sat 2026-06-06 10:00 is non-service time → jumps to Mon 09:00
        // SLA=60min → Mon 09:00+1h=10:00
        LocalDateTime sat10am = LocalDateTime.of(2026, 6, 6, 10, 0);
        LocalDateTime deadline = workCalendarService.calcEffectiveDeadline(sat10am, 60, CALENDAR_ID);
        assertEquals(LocalDateTime.of(2026, 6, 8, 10, 0), deadline);
    }

    @Test
    void testIsServiceTimeMultiSegment() {
        setupMultiSegmentCalendar();

        // Mon 2026-06-01 10:30 → in segment [09:00,12:00) → true
        LocalDateTime mon1030 = LocalDateTime.of(2026, 6, 1, 10, 30);
        assertTrue(workCalendarService.isServiceTime(mon1030, CALENDAR_ID));

        // Mon 2026-06-01 12:30 → lunch break (12:00-13:00) → false
        LocalDateTime mon1230 = LocalDateTime.of(2026, 6, 1, 12, 30);
        assertFalse(workCalendarService.isServiceTime(mon1230, CALENDAR_ID));

        // Sat 2026-06-06 10:00 → weekend → false
        LocalDateTime sat10am = LocalDateTime.of(2026, 6, 6, 10, 0);
        assertFalse(workCalendarService.isServiceTime(sat10am, CALENDAR_ID));
    }
}
