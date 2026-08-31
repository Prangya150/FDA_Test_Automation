package com.fda.automation.utils;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.Status;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;
import com.aventstack.extentreports.reporter.configuration.Theme;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Wraps a single shared {@link ExtentReports} instance (Spark HTML reporter) for the whole
 * suite, at target/SparkReport/SparkReport_&lt;timestamp&gt;.html - one file per run (rather than
 * overwriting a fixed name) so past runs' reports stay around for comparison. Each stage of the
 * seller onboarding flow gets its own top-level {@link ExtentTest} (see
 * SellerOnboardingEndToEndTest.stage()) even though they all run inside one TestNG @Test method,
 * so the report's test list reads as one entry per stage rather than one entry for the whole
 * suite. ThreadLocal for the same reason BaseTest's WebDriver is ThreadLocal - parallel test
 * methods must not share "the current test".
 */
public final class ExtentReportManager {
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private static volatile ExtentReports extent;
    private static final ThreadLocal<ExtentTest> currentTest = new ThreadLocal<>();

    private ExtentReportManager() {
    }

    public static ExtentReports getInstance() {
        if (extent == null) {
            synchronized (ExtentReportManager.class) {
                if (extent == null) {
                    String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
                    ExtentSparkReporter spark = new ExtentSparkReporter("target/SparkReport/SparkReport_" + timestamp + ".html");
                    spark.config().setTheme(Theme.STANDARD);
                    spark.config().setDocumentTitle("FDA Automation Test Results");
                    spark.config().setReportName("FDA Automation Test Results - " + timestamp);
                    extent = new ExtentReports();
                    extent.attachReporter(spark);
                }
            }
        }
        return extent;
    }

    public static ExtentTest startTest(String name, String description) {
        ExtentTest test = getInstance().createTest(name, description);
        currentTest.set(test);
        return test;
    }

    public static ExtentTest getTest() {
        return currentTest.get();
    }

    public static void log(Status status, String message) {
        ExtentTest test = currentTest.get();
        if (test != null) {
            test.log(status, message);
        }
    }

    public static void flush() {
        if (extent != null) {
            extent.flush();
        }
    }
}
