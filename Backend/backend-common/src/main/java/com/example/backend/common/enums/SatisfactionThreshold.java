package com.example.backend.common.enums;

public enum SatisfactionThreshold {

    SATISFIED(4),
    VERY_SATISFIED(5);

    private final int level;

    SatisfactionThreshold(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }

    public static final int SATISFIED_LEVEL = 4;
    public static final int VERY_SATISFIED_LEVEL = 5;
}
