package com.example.backend.interfaces.controller;

import com.example.backend.application.service.WorkCalendarService;
import com.example.backend.common.Result;
import com.example.backend.infrastructure.persistence.entity.SlaCalendarSpecialDate;
import com.example.backend.infrastructure.persistence.entity.SlaWorkCalendar;
import com.example.backend.infrastructure.persistence.mapper.SlaCalendarSpecialDateMapper;
import com.example.backend.infrastructure.persistence.mapper.SlaWorkCalendarMapper;
import com.example.backend.interfaces.security.RequireRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin/work-calendar")
@RequiredArgsConstructor
@RequireRole({"ADMIN"})
public class WorkCalendarController {

    private final SlaWorkCalendarMapper slaWorkCalendarMapper;
    private final SlaCalendarSpecialDateMapper slaCalendarSpecialDateMapper;
    private final WorkCalendarService workCalendarService;

    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        List<SlaWorkCalendar> calendars = slaWorkCalendarMapper.findAllActive();
        List<Map<String, Object>> result = new ArrayList<>();
        for (SlaWorkCalendar cal : calendars) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", cal.getId());
            item.put("calendarName", cal.getCalendarName());
            item.put("workDays", cal.getWorkDays());
            item.put("workTimeSegments", cal.getWorkTimeSegments());
            item.put("isActive", cal.getIsActive());
            result.add(item);
        }
        return Result.success(result);
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable Long id) {
        SlaWorkCalendar cal = slaWorkCalendarMapper.selectById(id);
        if (cal == null) {
            return Result.error(404, "Calendar not found");
        }
        List<SlaCalendarSpecialDate> specialDates = slaCalendarSpecialDateMapper.findByCalendarId(id);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", cal.getId());
        result.put("calendarName", cal.getCalendarName());
        result.put("workDays", cal.getWorkDays());
        result.put("workTimeSegments", cal.getWorkTimeSegments());
        result.put("isActive", cal.getIsActive());
        result.put("createdAt", cal.getCreatedAt());
        result.put("updatedAt", cal.getUpdatedAt());

        List<Map<String, Object>> specialDateList = new ArrayList<>();
        for (SlaCalendarSpecialDate sd : specialDates) {
            Map<String, Object> sdItem = new LinkedHashMap<>();
            sdItem.put("id", sd.getId());
            sdItem.put("calendarId", sd.getCalendarId());
            sdItem.put("specialDate", sd.getSpecialDate() != null ? sd.getSpecialDate().toString() : null);
            sdItem.put("dayType", sd.getDayType());
            sdItem.put("workSegments", sd.getWorkSegments());
            sdItem.put("description", sd.getDescription());
            sdItem.put("createdAt", sd.getCreatedAt());
            specialDateList.add(sdItem);
        }
        result.put("specialDates", specialDateList);

        return Result.success(result);
    }

    @PutMapping("/{id}")
    public Result<Map<String, Object>> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        SlaWorkCalendar cal = slaWorkCalendarMapper.selectById(id);
        if (cal == null) {
            return Result.error(404, "Calendar not found");
        }

        if (body.containsKey("calendarName")) {
            cal.setCalendarName((String) body.get("calendarName"));
        }
        if (body.containsKey("workDays")) {
            cal.setWorkDays((String) body.get("workDays"));
        }
        if (body.containsKey("workTimeSegments")) {
            cal.setWorkTimeSegments((String) body.get("workTimeSegments"));
        }

        cal.setUpdatedAt(LocalDateTime.now());
        slaWorkCalendarMapper.update(cal);
        workCalendarService.refreshCache();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("id", cal.getId());
        return Result.success(result);
    }

    @GetMapping("/{id}/special-dates")
    public Result<List<Map<String, Object>>> getSpecialDates(@PathVariable Long id) {
        List<SlaCalendarSpecialDate> specialDates = slaCalendarSpecialDateMapper.findByCalendarId(id);
        List<Map<String, Object>> resultList = new ArrayList<>();
        for (SlaCalendarSpecialDate sd : specialDates) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", sd.getId());
            item.put("specialDate", sd.getSpecialDate() != null ? sd.getSpecialDate().toString() : null);
            item.put("dayType", sd.getDayType());
            item.put("workSegments", sd.getWorkSegments());
            item.put("description", sd.getDescription());
            resultList.add(item);
        }
        return Result.success(resultList);
    }

    @PostMapping("/{id}/special-dates")
    public Result<Map<String, Object>> addSpecialDate(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        SlaCalendarSpecialDate entity = new SlaCalendarSpecialDate();
        entity.setCalendarId(id);

        String specialDateStr = (String) body.get("specialDate");
        if (specialDateStr != null && !specialDateStr.isBlank()) {
            entity.setSpecialDate(LocalDate.parse(specialDateStr));
        }

        if (body.containsKey("dayType")) {
            entity.setDayType((String) body.get("dayType"));
        }
        if (body.containsKey("workSegments")) {
            entity.setWorkSegments((String) body.get("workSegments"));
        }
        if (body.containsKey("description")) {
            entity.setDescription((String) body.get("description"));
        }

        entity.setCreatedAt(LocalDateTime.now());
        slaCalendarSpecialDateMapper.insert(entity);
        workCalendarService.refreshCache();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("created", true);
        result.put("id", entity.getId());
        return Result.success(result);
    }

    @DeleteMapping("/{id}/special-dates/{dateId}")
    public Result<Map<String, Object>> deleteSpecialDate(@PathVariable Long id, @PathVariable Long dateId) {
        slaCalendarSpecialDateMapper.deleteById(dateId);
        workCalendarService.refreshCache();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deleted", true);
        return Result.success(result);
    }
}
