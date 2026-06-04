package com.example.backend.common.enums;

public enum AgentTransferIntent {

    TRANSFER_TO_HUMAN("转人工");

    private final String value;

    AgentTransferIntent(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static final String TRANSFER_VAL = "转人工";
}
