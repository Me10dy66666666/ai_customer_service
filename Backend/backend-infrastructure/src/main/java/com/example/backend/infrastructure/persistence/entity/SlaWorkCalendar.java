package com.example.backend.infrastructure.persistence.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SlaWorkCalendar {
    private Long id;
    private String calendarName;
    private String workDays;
    private String workTimeSegments;
    private Integer isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
