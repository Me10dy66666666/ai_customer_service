package com.example.backend.common.enums;

public enum WorkOrderStatus {

    PENDING("pending"),
    PROCESSING("processing"),
    COMPLETED("completed"),
    CANCELLED("cancelled");

    private final String value;

    WorkOrderStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static final String PENDING_VAL = "pending";
    public static final String PROCESSING_VAL = "processing";
    public static final String COMPLETED_VAL = "completed";
    public static final String CANCELLED_VAL = "cancelled";
}
