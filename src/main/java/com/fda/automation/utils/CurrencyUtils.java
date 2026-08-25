package com.fda.automation.utils;

import java.math.BigDecimal;

/**
 * Shared currency-string parsing so FDA and Mirakl order totals can be compared numerically
 * (formatting such as "$1,234.50 MXN" vs "1234,50 MXN" can differ between the two systems).
 */
public class CurrencyUtils {

    private CurrencyUtils() {}

    public static BigDecimal parseCurrency(String rawTotal) {
        String normalized = rawTotal.replaceAll("[^0-9.,]", "");
        // Keep only the decimal separator: drop thousands separators, treat the last '.' or ',' as decimal point.
        int lastDot = normalized.lastIndexOf('.');
        int lastComma = normalized.lastIndexOf(',');
        int decimalIndex = Math.max(lastDot, lastComma);
        if (decimalIndex >= 0) {
            String integerPart = normalized.substring(0, decimalIndex).replaceAll("[.,]", "");
            String decimalPart = normalized.substring(decimalIndex + 1);
            normalized = integerPart + "." + decimalPart;
        }
        return new BigDecimal(normalized);
    }
}
