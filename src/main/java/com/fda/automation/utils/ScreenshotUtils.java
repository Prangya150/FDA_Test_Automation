package com.fda.automation.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

public class ScreenshotUtils {
    private static final Logger log = LogManager.getLogger(ScreenshotUtils.class);
    private static final String SCREENSHOT_DIR = "target/screenshots/";

    private ScreenshotUtils() {}

    /** Original method — unchanged. Saves to target/screenshots/ with timestamp. */
    public static String capture(WebDriver driver, String testName) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
        String fileName  = testName.replaceAll("[^a-zA-Z0-9_\\-]", "_") + "_" + timestamp + ".png";
        Path destPath = Paths.get(SCREENSHOT_DIR, fileName);
        try {
            Files.createDirectories(destPath.getParent());
            byte[] bytes = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            Files.write(destPath, bytes);
            log.info("Screenshot saved: {}", destPath.toAbsolutePath());
            return destPath.toAbsolutePath().toString();
        } catch (IOException e) {
            log.error("Failed to save screenshot for: {}", testName, e);
            return null;
        }
    }

    /**
     * Captures one screenshot, saves it to target/screenshots/{subDir}/{fileName},
     * and returns {base64String, absoluteFilePath}.
     * Both values are null if the capture fails.
     * Use this in listeners to avoid two separate driver.getScreenshotAs() calls.
     */
    public static String[] captureAndSave(WebDriver driver, String subDir, String fileName) {
        Path destPath = Paths.get(SCREENSHOT_DIR, subDir, fileName);
        try {
            Files.createDirectories(destPath.getParent());
            byte[] bytes  = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            Files.write(destPath, bytes);
            String base64 = Base64.getEncoder().encodeToString(bytes);
            log.info("Screenshot saved: {}", destPath.toAbsolutePath());
            return new String[]{base64, destPath.toAbsolutePath().toString()};
        } catch (Exception e) {
            log.error("Failed to capture screenshot: {}/{}", subDir, fileName, e);
            return new String[]{null, null};
        }
    }
}
