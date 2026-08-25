package com.fda.automation.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Generic retry/polling helper for cross-system synchronization that cannot be observed
 * through a single WebDriver condition (e.g. waiting for an order placed in FDA to become
 * searchable in Mirakl, or for an order to be visible via the Kibo API).
 *
 * This is intentionally separate from Selenium's {@code WebDriverWait}/{@code FluentWait},
 * which only apply to conditions evaluated against the DOM.
 */
public class PollingUtils {

    private static final Logger log = LogManager.getLogger(PollingUtils.class);

    private PollingUtils() {}

    /**
     * Repeatedly invokes {@code supplier} until {@code condition} is satisfied or the timeout elapses.
     *
     * @throws IllegalStateException with {@code timeoutMessage} if the condition is never satisfied
     */
    public static <T> T pollUntil(Supplier<T> supplier, Predicate<T> condition,
                                   Duration timeout, Duration pollInterval, String timeoutMessage) {
        Instant deadline = Instant.now().plus(timeout);
        T lastResult = null;
        int attempt = 0;

        while (Instant.now().isBefore(deadline)) {
            attempt++;
            try {
                lastResult = supplier.get();
                if (condition.test(lastResult)) {
                    log.info("Polling condition met on attempt {}", attempt);
                    return lastResult;
                }
            } catch (Exception e) {
                log.debug("Polling attempt {} failed, retrying: {}", attempt, e.getMessage());
            }

            try {
                Thread.sleep(pollInterval.toMillis());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Polling interrupted: " + timeoutMessage, ie);
            }
        }

        throw new IllegalStateException(timeoutMessage + " (timed out after " + timeout.getSeconds()
                + "s, last result: " + lastResult + ")");
    }
}
