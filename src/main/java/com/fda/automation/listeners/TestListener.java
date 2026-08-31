package com.fda.automation.listeners;

import com.fda.automation.base.BaseTest;
import com.fda.automation.utils.ExtentReportManager;
import com.fda.automation.utils.ScreenshotUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;

public class TestListener implements ITestListener {
    private static final Logger log = LogManager.getLogger(TestListener.class);

    @Override
    public void onTestStart(ITestResult result) {
        log.info(">>> START: {}", result.getName());
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        log.info(">>> PASS:  {}", result.getName());
    }

    @Override
    public void onTestFailure(ITestResult result) {
        log.error(">>> FAIL:  {} — {}", result.getName(),
                result.getThrowable() != null ? result.getThrowable().getMessage() : "unknown");
        captureScreenshot(result);
        capturePageSource(result);
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        log.warn(">>> SKIP:  {}", result.getName());
    }

    @Override
    public void onStart(ITestContext context) {
        log.info("=== Suite started: {} ===", context.getName());
    }

    @Override
    public void onFinish(ITestContext context) {
        log.info("=== Suite finished: {} | pass={} fail={} skip={} ===",
                context.getName(),
                context.getPassedTests().size(),
                context.getFailedTests().size(),
                context.getSkippedTests().size());
        ExtentReportManager.flush();
    }

    private void captureScreenshot(ITestResult result) {
        Object instance = result.getInstance();
        if (instance instanceof BaseTest baseTest) {
            WebDriver driver = baseTest.getDriver();
            if (driver != null) {
                String path = ScreenshotUtils.capture(driver, result.getName());
                if (path != null && ExtentReportManager.getTest() != null) {
                    try {
                        ExtentReportManager.getTest().addScreenCaptureFromPath(path);
                    } catch (Exception e) {
                        log.warn("Failed to attach screenshot to Extent report", e);
                    }
                }
            }
        }
    }

    private void capturePageSource(ITestResult result) {
        Object instance = result.getInstance();
        if (!(instance instanceof BaseTest baseTest)) {
            return;
        }
        WebDriver driver = baseTest.getDriver();
        if (driver == null) {
            return;
        }
        try {
            Path dir = Paths.get("target", "page-source");
            Files.createDirectories(dir);
            String timestamp = java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
            Path file = dir.resolve(result.getName() + "_" + timestamp + ".html");
            // getPageSource() reflects the pre-Knockout template, not live JS-applied attributes
            // (e.g. data-index), so pull the real live DOM via JS instead.
            String liveHtml = (String) ((JavascriptExecutor) driver)
                    .executeScript("return document.documentElement.outerHTML;");
            Files.writeString(file, liveHtml);
            log.info("Page source saved: {}", file.toAbsolutePath());
        } catch (IOException e) {
            log.warn("Failed to save page source on failure", e);
        }
    }
}
