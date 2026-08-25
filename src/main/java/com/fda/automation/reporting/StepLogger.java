package com.fda.automation.reporting;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Thread-local step tracker. Call step() at the start of each logical test action.
 * The previous open step is auto-completed as PASS when the next step() is called.
 * onTestFailure in the listener calls failCurrent() to annotate the failing step.
 */
public final class StepLogger {
    private static final Logger log = LogManager.getLogger(StepLogger.class);

    private static final ThreadLocal<List<StepRecord>> STEPS  = ThreadLocal.withInitial(ArrayList::new);
    private static final ThreadLocal<StepRecord>       CURRENT = new ThreadLocal<>();

    private StepLogger() {}

    /** Reset state at the start of each test. Called from TestListener.onTestStart. */
    public static void init() {
        STEPS.get().clear();
        CURRENT.set(null);
    }

    /**
     * Begin a new step. Auto-completes the previous open step as PASS.
     * Add one call at the top of each business-level helper method in the test.
     */
    public static void step(int number, String description) {
        autoCompletePreviousAsPassed();
        StepRecord s = new StepRecord(number, description);
        STEPS.get().add(s);
        CURRENT.set(s);
        log.info("[STEP {}] {}", String.format("%02d", number), description);
        System.out.printf("[STEP %02d] %s%n", number, description);
    }

    /**
     * Explicitly complete the current open step as PASS.
     * Called from TestListener.onTestSuccess to close the last step.
     */
    public static void pass() {
        autoCompletePreviousAsPassed();
        CURRENT.set(null);
    }

    /**
     * Annotate the currently open step as FAIL.
     * Called from TestListener.onTestFailure with the captured screenshot.
     */
    public static void failCurrent(String errorMessage, String expected, String actual, String screenshotBase64) {
        StepRecord s = CURRENT.get();
        if (s != null) {
            s.setErrorMessage(errorMessage);
            s.setExpected(expected);
            s.setActual(actual);
            s.setScreenshotBase64(screenshotBase64);
            s.complete(StepStatus.FAIL);
            log.error("[FAIL] Step {} - {} | Error: {}",
                    String.format("%02d", s.getNumber()), s.getDescription(), errorMessage);
            System.out.printf("[FAIL] Step %02d - %s%n", s.getNumber(), s.getDescription());
            if (expected != null) System.out.printf("  Expected: %s%n", expected);
            if (actual   != null) System.out.printf("  Actual  : %s%n", actual);
            System.out.printf("  Error   : %s%n", errorMessage);
        }
    }

    public static StepRecord      getCurrentStep() { return CURRENT.get(); }
    public static List<StepRecord> getSteps()       { return new ArrayList<>(STEPS.get()); }

    /** Called after the test record has been built to free thread-local memory. */
    public static void clear() {
        STEPS.get().clear();
        CURRENT.set(null);
    }

    // -------------------------------------------------------------------------

    private static void autoCompletePreviousAsPassed() {
        StepRecord prev = CURRENT.get();
        if (prev != null && prev.getStatus() == StepStatus.RUNNING) {
            prev.complete(StepStatus.PASS);
            log.info("[PASS] Step {} - {}",
                    String.format("%02d", prev.getNumber()), prev.getDescription());
            System.out.printf("[PASS] Step %02d - %s%n", prev.getNumber(), prev.getDescription());
        }
    }
}
