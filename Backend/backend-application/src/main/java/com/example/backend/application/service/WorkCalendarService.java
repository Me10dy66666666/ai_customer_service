package com.example.backend.application.service;

import com.example.backend.infrastructure.persistence.entity.SlaWorkCalendar;
import com.example.backend.infrastructure.persistence.entity.SlaCalendarSpecialDate;
import com.example.backend.infrastructure.persistence.mapper.SlaWorkCalendarMapper;
import com.example.backend.infrastructure.persistence.mapper.SlaCalendarSpecialDateMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkCalendarService {

    private final SlaWorkCalendarMapper slaWorkCalendarMapper;
    private final SlaCalendarSpecialDateMapper slaCalendarSpecialDateMapper;
    private final ConcurrentHashMap<Long, CalendarData> cache = new ConcurrentHashMap<>();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @PostConstruct
    public void init() {
        refreshCache();
    }

    public void refreshCache() {
        cache.clear();
        List<SlaWorkCalendar> calendars = slaWorkCalendarMapper.findAllActive();
        for (SlaWorkCalendar cal : calendars) {
            CalendarData data = buildCalendarData(cal);
            cache.put(cal.getId(), data);
        }
        log.info("Work calendar cache refreshed, {} calendars loaded", cache.size());
    }

    public boolean isServiceTime(LocalDateTime time, Long calendarId) {
        CalendarData data = getCalendarData(calendarId);
        return isServiceTimeInternal(time, data);
    }

    public LocalDateTime getNextServiceTime(LocalDateTime from, Long calendarId) {
        CalendarData data = getCalendarData(calendarId);
        return getNextServiceTimeInternal(from, data);
    }

    public LocalDateTime calcEffectiveDeadline(LocalDateTime fromTime, int slaMinutes, Long calendarId) {
        long remainingSeconds = slaMinutes * 60L;
        LocalDateTime current = fromTime;
        CalendarData data = getCalendarData(calendarId);
        while (remainingSeconds > 0L) {
            if (!isServiceTimeInternal(current, data)) {
                current = getNextServiceTimeInternal(current, data);
                continue;
            }
            LocalDate date = current.toLocalDate();
            for (TimeSegment seg : getSegmentsForDate(date, data)) {
                LocalDateTime segStartDt = LocalDateTime.of(date, seg.start);
                LocalDateTime segEndDt = LocalDateTime.of(date, seg.end);
                if (!current.isBefore(segEndDt)) {
                    continue;
                }
                if (current.isBefore(segStartDt)) {
                    current = segStartDt;
                }
                long segAvailable = ChronoUnit.SECONDS.between(current, segEndDt);
                if (remainingSeconds <= segAvailable) {
                    return current.plusSeconds(remainingSeconds);
                }
                remainingSeconds -= segAvailable;
                current = segEndDt;
            }
            current = getNextServiceTimeInternal(current, data);
        }
        return current;
    }

    public long calcEffectiveDuration(LocalDateTime startTime, LocalDateTime endTime, Long calendarId) {
        if (startTime == null) {
            throw new IllegalArgumentException("startTime must not be null");
        }
        if (endTime == null || !startTime.isBefore(endTime)) {
            return 0L;
        }
        CalendarData data = getCalendarData(calendarId);
        long totalSeconds = 0L;
        LocalDateTime current = startTime;
        while (current.isBefore(endTime)) {
            if (!isServiceTimeInternal(current, data)) {
                current = getNextServiceTimeInternal(current, data);
                if (!current.isBefore(endTime)) {
                    break;
                }
            } else {
                LocalDateTime endOfDay = getEndOfServiceDay(current, data);
                LocalDateTime segmentEnd = endOfDay.isBefore(endTime) ? endOfDay : endTime;
                totalSeconds += ChronoUnit.SECONDS.between(current, segmentEnd);
                current = segmentEnd;
            }
        }
        return totalSeconds;
    }

    private CalendarData getCalendarData(Long calendarId) {
        if (calendarId != null && calendarId > 0L) {
            CalendarData data = cache.get(calendarId);
            if (data != null) {
                return data;
            }
        }
        CalendarData fallbackData = cache.get(1L);
        if (fallbackData != null) {
            return fallbackData;
        }
        return CalendarData.EMPTY;
    }

    private boolean isServiceTimeInternal(LocalDateTime time, CalendarData data) {
        LocalDate date = time.toLocalDate();
        if (!isWorkingDay(date, data)) {
            return false;
        }
        List<TimeSegment> segments = getSegmentsForDate(date, data);
        LocalTime t = time.toLocalTime();
        for (TimeSegment seg : segments) {
            if (!t.isBefore(seg.start) && t.isBefore(seg.end)) {
                return true;
            }
        }
        return false;
    }

    private LocalDateTime getNextServiceTimeInternal(LocalDateTime from, CalendarData data) {
        LocalDate date = from.toLocalDate();
        if (isWorkingDay(date, data)) {
            LocalDateTime startOfDay = getStartOfServiceDay(date, data);
            if (startOfDay != null && from.isBefore(startOfDay)) {
                return startOfDay;
            }
        }
        LocalDate nextDate = date;
        while (true) {
            nextDate = nextDate.plusDays(1L);
            if (isWorkingDay(nextDate, data)) {
                LocalDateTime startOfNextDay = getStartOfServiceDay(nextDate, data);
                if (startOfNextDay != null) {
                    return startOfNextDay;
                }
            }
        }
    }

    private boolean isWorkingDay(LocalDate date, CalendarData data) {
        SpecialDayInfo special = data.specialDates.get(date);
        if (special != null) {
            return !"HOLIDAY".equals(special.dayType);
        }
        DayOfWeek dow = date.getDayOfWeek();
        return data.workDays.contains(dow.getValue());
    }

    private List<TimeSegment> getSegmentsForDate(LocalDate date, CalendarData data) {
        SpecialDayInfo special = data.specialDates.get(date);
        if (special != null && special.workSegments != null && !special.workSegments.isEmpty()) {
            return special.workSegments;
        }
        return data.workSegments;
    }

    private LocalDateTime getEndOfServiceDay(LocalDateTime time, CalendarData data) {
        LocalDate date = time.toLocalDate();
        List<TimeSegment> segments = getSegmentsForDate(date, data);
        if (segments.isEmpty()) {
            return LocalDateTime.of(date, LocalTime.MAX);
        }
        TimeSegment last = segments.get(segments.size() - 1);
        return LocalDateTime.of(date, last.end);
    }

    private LocalDateTime getStartOfServiceDay(LocalDate date, CalendarData data) {
        List<TimeSegment> segments = getSegmentsForDate(date, data);
        if (segments.isEmpty()) {
            return null;
        }
        TimeSegment first = segments.get(0);
        return LocalDateTime.of(date, first.start);
    }

    private CalendarData buildCalendarData(SlaWorkCalendar cal) {
        Set<Integer> workDays = parseWorkDays(cal.getWorkDays());
        List<TimeSegment> workSegments = parseTimeSegments(cal.getWorkTimeSegments());
        Map<LocalDate, SpecialDayInfo> specialDates = loadSpecialDates(cal.getId());
        return new CalendarData(workDays, workSegments, specialDates);
    }

    private Set<Integer> parseWorkDays(String workDays) {
        if (workDays == null || workDays.isBlank()) {
            return Set.of(1, 2, 3, 4, 5);
        }
        return Arrays.stream(workDays.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Integer::parseInt)
                .collect(Collectors.toUnmodifiableSet());
    }

    private List<TimeSegment> parseTimeSegments(String json) {
        if (json == null || json.isBlank()) {
            return List.of(new TimeSegment(LocalTime.of(9, 0), LocalTime.of(18, 0)));
        }
        try {
            List<Map<String, String>> raw = OBJECT_MAPPER.readValue(json,
                    new TypeReference<List<Map<String, String>>>() {});
            return raw.stream()
                    .map(m -> new TimeSegment(
                            LocalTime.parse(m.get("start")),
                            LocalTime.parse(m.get("end"))))
                    .sorted(Comparator.comparing(s -> s.start))
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to parse work time segments JSON: {}", json, e);
            return List.of(new TimeSegment(LocalTime.of(9, 0), LocalTime.of(18, 0)));
        }
    }

    private Map<LocalDate, SpecialDayInfo> loadSpecialDates(Long calendarId) {
        List<SlaCalendarSpecialDate> specialDates = slaCalendarSpecialDateMapper.findByCalendarId(calendarId);
        Map<LocalDate, SpecialDayInfo> result = new HashMap<>();
        for (SlaCalendarSpecialDate sd : specialDates) {
            List<TimeSegment> segments = parseTimeSegments(sd.getWorkSegments());
            result.put(sd.getSpecialDate(), new SpecialDayInfo(sd.getDayType(), segments));
        }
        return Collections.unmodifiableMap(result);
    }

    private static class CalendarData {
        static final CalendarData EMPTY = new CalendarData(
                Set.of(1, 2, 3, 4, 5),
                List.of(new TimeSegment(LocalTime.of(9, 0), LocalTime.of(18, 0))),
                Map.of());

        final Set<Integer> workDays;
        final List<TimeSegment> workSegments;
        final Map<LocalDate, SpecialDayInfo> specialDates;

        CalendarData(Set<Integer> workDays, List<TimeSegment> workSegments,
                     Map<LocalDate, SpecialDayInfo> specialDates) {
            this.workDays = workDays;
            this.workSegments = workSegments;
            this.specialDates = specialDates;
        }
    }

    private static class SpecialDayInfo {
        final String dayType;
        final List<TimeSegment> workSegments;

        SpecialDayInfo(String dayType, List<TimeSegment> workSegments) {
            this.dayType = dayType;
            this.workSegments = workSegments;
        }
    }

    private static class TimeSegment {
        final LocalTime start;
        final LocalTime end;

        TimeSegment(LocalTime start, LocalTime end) {
            this.start = start;
            this.end = end;
        }
    }
}
