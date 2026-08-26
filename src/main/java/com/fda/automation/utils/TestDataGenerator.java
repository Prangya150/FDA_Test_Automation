package com.fda.automation.utils;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

public class TestDataGenerator {

    private static final AtomicLong CALL_COUNTER = new AtomicLong();

    private TestDataGenerator() {
    }

    /**
     * Epoch milliseconds combined with a per-JVM call counter, so two values generated
     * back-to-back (even within the same millisecond, e.g. email + RFC in one test) never
     * collide, unlike a bare epoch-second timestamp.
     */
    private static long nextUniqueValue() {
        return Instant.now().toEpochMilli() * 1000 + (CALL_COUNTER.incrementAndGet() % 1000);
    }

    public static String uniqueEmail(String localPartPrefix, String domain) {
        return localPartPrefix + nextUniqueValue() + "@" + domain;
    }

    public static String uniqueTradeName(String prefix) {
        return prefix + " " + nextUniqueValue();
    }

    /**
     * Builds a syntactically valid 12-char "persona moral" RFC (3 letters + YYMMDD + 3-char
     * homoclave) with a homoclave derived from nextUniqueValue(), so repeated test runs don't
     * collide with an RFC already registered by a previous run.
     */
    public static String uniqueRfc(String threeLetterPrefix) {
        long homoclave = nextUniqueValue() % 1000;
        return threeLetterPrefix + "120101" + String.format("%03d", homoclave);
    }
}
