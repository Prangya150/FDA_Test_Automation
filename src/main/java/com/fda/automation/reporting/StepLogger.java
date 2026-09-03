package com.fda.automation.reporting;

import com.fda.automation.utils.ScreenshotUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebDriver;

import java.util.ArrayList;
import java.util.List;

/**
 * Thread-local step tracker. Call step() at the start of each logical test action.
 * The previous open step is auto-completed as PASS when the next step() is called.
 * onTestFailure in the listener calls failCurrent() to annotate the failing step.
 *
 * Step screenshots are captured on PASS completion when a WebDriver is registered via
 * setDriver(). Disable with -Dstep.screenshots.enabled=false (large suites ~200KB/step).
 */
public final class StepLogger {
    private static final Logger log = LogManager.getLogger(StepLogger.class);

    private static final boolean STEP_SS_ENABLED =
            !"false".equalsIgnoreCase(System.getProperty("step.screenshots.enabled", "true"));

    private static final ThreadLocal<List<StepRecord>> STEPS   = ThreadLocal.withInitial(ArrayList::new);
    private static final ThreadLocal<StepRecord>       CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<WebDriver>        DRIVER  = new ThreadLocal<>();
    private static final ThreadLocal<String>           TEST_ID = new ThreadLocal<>();

    private StepLogger() {}

    // -------------------------------------------------------------------------
    // Driver / test-id registration (called from BaseTest and TestListener)
    // -------------------------------------------------------------------------

    public static void setDriver(WebDriver driver) { DRIVER.set(driver); }
    public static void clearDriver()               { DRIVER.remove(); }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /** Reset step state at the start of each test. Called from TestListener.onTestStart. */
    public static void init(String testId) {
        STEPS.get().clear();
        CURRENT.set(null);
        TEST_ID.set(testId);
    }

    /**
     * Begin a new step. Auto-completes (and screenshots) the previous open step as PASS.
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
     * Explicitly complete the current open step as PASS (closes the last step).
     * Called from TestListener.onTestSuccess.
     */
    public static void pass() {
        autoCompletePreviousAsPassed();
        CURRENT.set(null);
    }

    /**
     * Annotate the currently open step as FAIL with the failure screenshot.
     * Called from TestListener.onTestFailure.
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

    public static StepRecord       getCurrentStep() { return CURRENT.get(); }
    public static List<StepRecord> getSteps()       { return new ArrayList<>(STEPS.get()); }

    /** Free thread-local memory after the test record has been built. */
    public static void clear() {
        STEPS.get().clear();
        CURRENT.set(null);
        DRIVER.remove();
        TEST_ID.remove();
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    private static void autoCompletePreviousAsPassed() {
        StepRecord prev = CURRENT.get();
        if (prev != null && prev.getStatus() == StepStatus.RUNNING) {
            captureStepScreenshot(prev);
            prev.complete(StepStatus.PASS);
            log.info("[PASS] Step {} - {}",
                    String.format("%02d", prev.getNumber()), prev.getDescription());
            System.out.printf("[PASS] Step %02d - %s%n", prev.getNumber(), prev.getDescription());
        }
    }

    private static void captureStepScreenshot(StepRecord step) {
        if (!STEP_SS_ENABLED) return;
        WebDriver driver = DRIVER.get();
        if (driver == null) return;
        try {
            String testId = TEST_ID.get() != null ? TEST_ID.get() : "test";
            String fileName = testId + "_Step_" + String.format("%02d", step.getNumber()) + "_PASS.png";
            String[] ss = ScreenshotUtils.captureAndSave(driver, "steps", fileName);
            if (ss[0] != null) step.setScreenshotBase64(ss[0]);
        } catch (Exception e) {
            log.warn("Step screenshot failed for step {}", step.getNumber(), e);
        }
    }
}
