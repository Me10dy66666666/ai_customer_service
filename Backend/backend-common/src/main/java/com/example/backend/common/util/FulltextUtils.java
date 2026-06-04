package com.example.backend.common.util;

public final class FulltextUtils {

    private FulltextUtils() {
    }

    public static String escapeBooleanMode(String keyword) {
        if (keyword == null) {
            return null;
        }
        return keyword.replaceAll("([+<>()~*\"@\\\\-])", "\\\\$1");
    }
}
