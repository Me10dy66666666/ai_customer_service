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
}
