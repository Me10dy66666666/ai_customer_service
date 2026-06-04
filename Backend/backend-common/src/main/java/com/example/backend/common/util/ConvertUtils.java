package com.example.backend.common.util;

public final class ConvertUtils {

    private ConvertUtils() {
    }

    public static Long parseLong(Object value) {
        if (value instanceof Long longVal) {
            return longVal;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String str) {
            try {
                return Long.parseLong(str.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    public static Integer parseInteger(Object value) {
        if (value instanceof Integer intVal) {
            return intVal;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String str) {
            try {
                return Integer.parseInt(str.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
