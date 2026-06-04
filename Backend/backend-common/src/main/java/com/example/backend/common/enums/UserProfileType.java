package com.example.backend.common.enums;

public enum UserProfileType {

    UNREGISTERED("UNREGISTERED"),
    REGISTERED("REGISTERED"),
    MEMBER("MEMBER"),
    HIGH_POTENTIAL("HIGH_POTENTIAL"),
    VISITOR("Visitor"),
    NEW_USER("新用户"),
    HIGH_VALUE("高价值用户"),
    POTENTIAL("潜力用户"),
    NORMAL("普通用户"),
    ALL("All");

    private final String value;

    UserProfileType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static final String MEMBER_VAL = "MEMBER";
    public static final String REGISTERED_VAL = "REGISTERED";
    public static final String UNREGISTERED_VAL = "UNREGISTERED";
    public static final String HIGH_POTENTIAL_VAL = "HIGH_POTENTIAL";
    public static final String ALL_VAL = "All";
}
