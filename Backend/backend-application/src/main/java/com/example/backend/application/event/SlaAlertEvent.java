package com.example.backend.application.event;

import lombok.Getter;

@Getter
public class SlaAlertEvent {
    public enum Level { NOTICE, WARNING, CRITICAL, BREACH }

    private final Long workOrderId;
    private final String title;
    private final Level level;
    private final String message;

    public SlaAlertEvent(Long workOrderId, String title, Level level, String message) {
        this.workOrderId = workOrderId;
        this.title = title;
        this.level = level;
        this.message = message;
    }
}
