package com.example.backend.common.enums;

public enum WorkOrderPriority {

    HIGH("high"),
    MEDIUM("medium"),
    LOW("low");

    private final String value;

    WorkOrderPriority(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static final String HIGH_VAL = "high";
    public static final String MEDIUM_VAL = "medium";
    public static final String LOW_VAL = "low";
}
