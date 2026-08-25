package com.fda.automation.listeners;

import com.fda.automation.base.BaseTest;
import com.fda.automation.reporting.*;
import com.fda.automation.utils.ScreenshotUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebDriver;
import org.testng.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Unified TestNG listener:
 *   ITestListener   — per-test hooks (start/pass/fail/skip)
 *   ISuiteListener  — suite-level hooks (HTML report generated in onFinish)
 */
public class TestListener implements ITestListener, ISuiteListener {

    private static final Logger log = LogManager.getLogger(TestListener.class);
    private static final String SEP = "=".repeat(54);
    private static final String DIV = "-".repeat(54);

    // =========================================================================
    // ISuiteListener
    // =========================================================================

    @Override
    public void onStart(ISuite suite) {
        ReportManager.getInstance().suiteStarted();
        log.info(SEP);
        log.info("SUITE STARTED: {}", suite.getName());
        log.info(SEP);
    }

    @Override
    public void onFinish(ISuite suite) {
        ReportManager.getInstance().suiteFinished();
        generateHtmlReport();
        printSuiteSummary();
    }

    // =========================================================================
    // ITestListener — context-level
    // =========================================================================

    @Override
    public void onStart(ITestContext context) {
        log.info("Test context started: {}", context.getName());
    }

    @Override
    public void onFinish(ITestContext context) {
        log.info("Test context finished: {} | pass={} fail={} skip={}",
                context.getName(),
                context.getPassedTests().size(),
                context.getFailedTests().size(),
                context.getSkippedTests().size());
    }

    // =========================================================================
    // ITestListener — per-test
    // =========================================================================

    @Override
    public void onTestStart(ITestResult result) {
        StepLogger.init();
        String testId = extractTestId(result);
        System.out.println();
        System.out.println(SEP);
        System.out.printf("TEST CASE : %s%n", testId);
        System.out.printf("Method    : %s%n", result.getName());
        System.out.println(DIV);
        log.info(">>> START: {} [{}]", testId, result.getName());
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        // Complete the last open step before collecting
        StepLogger.pass();

        String testId = extractTestId(result);
        WebDriver driver = getDriver(result);

        String[] ss = (driver != null)
                ? ScreenshotUtils.captureAndSave(driver, "passed", testId + "_PASS.png")
                : new String[]{null, null};

        long endMs = result.getEndMillis() > 0 ? result.getEndMillis() : System.currentTimeMillis();
        TestRecord record = new TestRecord(
                testId, result.getName(), description(result), "PASS",
                result.getStartMillis(), endMs,
                StepLogger.getSteps(), ss[0], ss[1]);

        ReportManager.getInstance().addRecord(record);
        writeTestLog(record);
        StepLogger.clear();

        System.out.println(DIV);
        System.out.printf("TEST CASE STATUS: PASSED%n");
        System.out.println(SEP);
        log.info(">>> PASS: {} [{}]", testId, result.getName());
    }

    @Override
    public void onTestFailure(ITestResult result) {
        String testId = extractTestId(result);
        WebDriver driver = getDriver(result);

        // Capture failure screenshot immediately at the exact point of failure
        StepRecord currentStep = StepLogger.getCurrentStep();
        String stepLabel = currentStep != null
                ? String.format("Step_%02d", currentStep.getNumber())
                : "Step_XX";
        String[] ss = (driver != null)
                ? ScreenshotUtils.captureAndSave(driver, "failed", testId + "_" + stepLabel + "_FAIL.png")
                : new String[]{null, null};

        // Annotate the failing step
        Throwable t      = result.getThrowable();
        String errorMsg  = t != null ? t.getMessage() : "Unknown error";
        StepLogger.failCurrent(errorMsg, null, null, ss[0]);

        long endMs = result.getEndMillis() > 0 ? result.getEndMillis() : System.currentTimeMillis();
        TestRecord record = new TestRecord(
                testId, result.getName(), description(result), "FAIL",
                result.getStartMillis(), endMs,
                StepLogger.getSteps(), ss[0], ss[1]);

        ReportManager.getInstance().addRecord(record);
        writeTestLog(record);
        StepLogger.clear();

        System.out.println(DIV);
        StepRecord fs = record.getFailedStep();
        if (fs != null) {
            System.out.printf("FAILED STEP : Step %02d - %s%n", fs.getNumber(), fs.getDescription());
        }
        System.out.printf("EXCEPTION   : %s%n", errorMsg);
        System.out.printf("SCREENSHOT  : %s%n", ss[1] != null ? ss[1] : "N/A");
        System.out.printf("TEST CASE STATUS: FAILED%n");
        System.out.println(SEP);
        log.error(">>> FAIL: {} [{}] — {}", testId, result.getName(), errorMsg);
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        String testId = extractTestId(result);
        List<StepRecord> steps = StepLogger.getSteps();
        StepLogger.clear();

        TestRecord record = new TestRecord(
                testId, result.getName(), description(result), "SKIP",
                result.getStartMillis(), System.currentTimeMillis(),
                steps, null, null);
        ReportManager.getInstance().addRecord(record);
        writeTestLog(record);

        System.out.printf("TEST CASE STATUS: SKIPPED: %s%n%n", testId);
        log.warn(">>> SKIP: {} [{}]", testId, result.getName());
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private String extractTestId(ITestResult result) {
        String cn = result.getTestClass().getRealClass().getSimpleName();
        return cn.endsWith("_Test") ? cn.substring(0, cn.length() - 5) : cn;
    }

    private WebDriver getDriver(ITestResult result) {
        Object instance = result.getInstance();
        if (instance instanceof BaseTest baseTest) {
            return baseTest.getDriver();
        }
        return null;
    }

    private String description(ITestResult result) {
        String d = result.getMethod().getDescription();
        return (d != null && !d.isBlank()) ? d : result.getName();
    }

    // -------------------------------------------------------------------------
    // HTML report
    // -------------------------------------------------------------------------

    private void generateHtmlReport() {
        try {
            List<TestRecord> records = ReportManager.getInstance().getRecords();
            String html = HtmlReportGenerator.generate(
                    records,
                    ReportManager.getInstance().getSuiteStartMs(),
                    ReportManager.getInstance().getSuiteEndMs());

            Path reportPath = Paths.get("target/surefire-reports/fda-report.html");
            Files.createDirectories(reportPath.getParent());
            Files.writeString(reportPath, html, StandardCharsets.UTF_8);

            System.out.println();
            System.out.println("HTML report -> " + reportPath.toAbsolutePath());
            log.info("HTML report generated: {}", reportPath.toAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to generate HTML report", e);
        }
    }

    // -------------------------------------------------------------------------
    // Per-test log file
    // -------------------------------------------------------------------------

    private void writeTestLog(TestRecord r) {
        Path logPath = Paths.get("target/logs", r.getTestId() + ".log");
        try {
            Files.createDirectories(logPath.getParent());
            try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(logPath, StandardCharsets.UTF_8))) {
                pw.println("================================================================");
                pw.printf("TEST CASE  : %s%n", r.getTestId());
                pw.printf("DESCRIPTION: %s%n", r.getDescription());
                pw.printf("STATUS     : %s%n", r.getStatus());
                pw.printf("START TIME : %s%n", r.getStartTime());
                pw.printf("END TIME   : %s%n", r.getEndTime());
                pw.printf("DURATION   : %s%n", r.getFormattedDuration());
                pw.println("================================================================");
                pw.println();
                pw.println("EXECUTION STEPS");
                pw.println("----------------------------------------------------------------");

                for (StepRecord step : r.getSteps()) {
                    pw.printf("[%s] [STEP %02d] [%-4s] %s%n",
                            step.getTimestamp(), step.getNumber(),
                            step.getStatus().name(), step.getDescription());
                    if (step.getStatus() == StepStatus.FAIL) {
                        if (step.getExpected()     != null) pw.printf("           Expected  : %s%n", step.getExpected());
                        if (step.getActual()       != null) pw.printf("           Actual    : %s%n", step.getActual());
                        if (step.getErrorMessage() != null) pw.printf("           Error     : %s%n", step.getErrorMessage());
                    }
                }

                pw.println();
                pw.println("================================================================");
                if ("FAIL".equals(r.getStatus())) {
                    StepRecord fs = r.getFailedStep();
                    if (fs != null) {
                        pw.printf("FAILED STEP: Step %02d - %s%n", fs.getNumber(), fs.getDescription());
                        pw.printf("ERROR      : %s%n", fs.getErrorMessage());
                    }
                }
                if (r.getScreenshotPath() != null) {
                    pw.printf("SCREENSHOT : %s%n", r.getScreenshotPath());
                }
                pw.println("================================================================");
            }
            log.info("Test log written: {}", logPath.toAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to write test log for {}", r.getTestId(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Suite summary to console/log
    // -------------------------------------------------------------------------

    private void printSuiteSummary() {
        List<TestRecord> records = ReportManager.getInstance().getRecords();
        int total   = records.size();
        int passed  = (int) records.stream().filter(r -> "PASS".equals(r.getStatus())).count();
        int failed  = (int) records.stream().filter(r -> "FAIL".equals(r.getStatus())).count();
        int skipped = total - passed - failed;
        double passPercent = total > 0 ? (passed * 100.0 / total) : 0;

        System.out.println();
        System.out.println(SEP);
        System.out.println("AUTOMATION EXECUTION SUMMARY");
        System.out.println(SEP);
        System.out.printf("Total Test Cases : %d%n", total);
        System.out.printf("Passed           : %d%n", passed);
        System.out.printf("Failed           : %d%n", failed);
        System.out.printf("Skipped          : %d%n", skipped);
        System.out.printf("Pass %%           : %.0f%%%n", passPercent);
        System.out.println(SEP);

        log.info("=== Suite finished: pass={} fail={} skip={} total={} ===",
                passed, failed, skipped, total);
    }
}
