package com.example.backend.common.util;

public final class MaskUtils {

    private static final int PHONE_MIN_LENGTH = 7;
    private static final String PHONE_MASK_SYMBOL = "****";
    private static final int PHONE_PREFIX_LENGTH = 3;
    private static final int PHONE_SUFFIX_LENGTH = 4;

    private MaskUtils() {
    }

    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < PHONE_MIN_LENGTH) {
            return phone;
        }
        return phone.substring(0, PHONE_PREFIX_LENGTH)
                + PHONE_MASK_SYMBOL
                + phone.substring(phone.length() - PHONE_SUFFIX_LENGTH);
    }
}
