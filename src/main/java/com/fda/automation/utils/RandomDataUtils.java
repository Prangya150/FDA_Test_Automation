package com.fda.automation.utils;

import java.security.SecureRandom;

/**
 * Reusable generators for randomized test data.
 */
public class RandomDataUtils {

    private static final SecureRandom RANDOM = new SecureRandom();

    private RandomDataUtils() {}

    /**
     * Generates a random 8-digit numeric string (always exactly 8 digits, no leading-zero collapse).
     */
    public static String generateTrackingNumber() {
        int number = 10_000_000 + RANDOM.nextInt(90_000_000);
        return String.valueOf(number);
    }
}
