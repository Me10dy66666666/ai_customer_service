package com.example.backend.common.enums;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public enum SlaPauseReason {

    CUSTOMER_WAITING("CUSTOMER_WAITING", "\u7b49\u5f85\u5ba2\u6237\u56de\u590d"),
    THIRD_PARTY("THIRD_PARTY", "\u7b49\u5f85\u7b2c\u4e09\u65b9"),
    MANUAL_HOLD("MANUAL_HOLD", "\u624b\u52a8\u6302\u8d77");

    private final String code;
    private final String label;

    SlaPauseReason(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static final String CUSTOMER_WAITING_CODE = "CUSTOMER_WAITING";
    public static final String THIRD_PARTY_CODE = "THIRD_PARTY";
    public static final String MANUAL_HOLD_CODE = "MANUAL_HOLD";

    private static final Set<String> VALID_CODES;

    static {
        Set<String> codes = new HashSet<>();
        for (SlaPauseReason reason : values()) {
            codes.add(reason.code);
        }
        VALID_CODES = Collections.unmodifiableSet(codes);
    }

    public static Set<String> getValidCodes() {
        return VALID_CODES;
    }

    public static boolean isValid(String code) {
        return code != null && VALID_CODES.contains(code);
    }
}
