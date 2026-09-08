package com.umang.bookmyshow.util;

import java.security.SecureRandom;

public final class BookingReferenceGenerator {

    private static final String PREFIX = "BMS";
    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int SUFFIX_LENGTH = 4;
    private static final SecureRandom RANDOM = new SecureRandom();

    private BookingReferenceGenerator() {
    }

    public static String generate() {
        StringBuilder suffix = new StringBuilder(SUFFIX_LENGTH);
        for (int i = 0; i < SUFFIX_LENGTH; i++) {
            suffix.append(ALPHANUMERIC.charAt(RANDOM.nextInt(ALPHANUMERIC.length())));
        }
        return PREFIX + System.currentTimeMillis() + suffix;
    }
}
