package com.example.backend.infrastructure.persistence.entity;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class SlaCalendarSpecialDate {
    private Long id;
    private Long calendarId;
    private LocalDate specialDate;
    private String dayType;
    private String workSegments;
    private String description;
    private LocalDateTime createdAt;
}
