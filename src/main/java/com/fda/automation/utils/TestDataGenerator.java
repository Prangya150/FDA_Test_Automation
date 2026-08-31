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

    private static final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    /**
     * Builds a syntactically valid 13-char "persona f&iacute;sica" RFC (4-letter prefix +
     * YYMMDD + 3-char alphanumeric homoclave, e.g. "GODE041211GA3") with a homoclave
     * derived from System.nanoTime(), so repeated test runs don't collide with an RFC
     * already registered by a previous run.
     *
     * nextUniqueValue() % 1000 was tried here originally, but nextUniqueValue() is
     * epochMilli * 1000 + (counter % 1000) - the epochMilli term is always an exact multiple
     * of 1000, so "% 1000" discarded it entirely and left only the call counter. Since this
     * is always the 3rd nextUniqueValue()-based call in buildValidSellerData() (after email,
     * then tradeName), every run produced the exact same homoclave and therefore the exact
     * same RFC, which the backend silently rejected as a duplicate seller on the 2nd+ run
     * with a generic "No pudimos enviar la información" error.
     */
    public static String uniqueRfc(String fourLetterPrefix) {
        long seed = Math.floorMod(System.nanoTime() + CALL_COUNTER.incrementAndGet(), 1_000_000L);
        char first = LETTERS.charAt((int) (seed % 26));
        char second = LETTERS.charAt((int) ((seed / 26) % 26));
        int digit = (int) ((seed / (26 * 26)) % 10);
        String homoclave = "" + first + second + digit;
        return fourLetterPrefix + "041211" + homoclave;
    }
}
