package com.fda.automation.base;

import org.openqa.selenium.WebDriver;

/**
 * Shared WebDriver plus FDA/Mirakl tab handles for the whole suite run.
 *
 * Populated once by {@code SuiteLoginListener.onStart} (an ISuiteListener wired in testng.xml,
 * which performs the actual FDA/Mirakl login using page objects) and read by every
 * TC_FBS_00N_Test via {@link BaseTest#getDriver()}, so all tests in the suite share one login
 * instead of each logging in independently. Kept as a plain data holder with no page-object
 * dependency because this class lives in src/main/java, which cannot depend on the page objects
 * in src/test/java - the login logic itself lives in the test-scoped listener instead.
 */
public final class SuiteSession {

    private static WebDriver driver;
    private static String fdaWindowHandle;
    private static String miraklWindowHandle;

    private SuiteSession() {}

    public static WebDriver getDriver() {
        return driver;
    }

    public static void setDriver(WebDriver value) {
        driver = value;
    }

    public static String getFdaWindowHandle() {
        return fdaWindowHandle;
    }

    public static void setFdaWindowHandle(String value) {
        fdaWindowHandle = value;
    }

    public static String getMiraklWindowHandle() {
        return miraklWindowHandle;
    }

    public static void setMiraklWindowHandle(String value) {
        miraklWindowHandle = value;
    }

    public static void clear() {
        driver = null;
        fdaWindowHandle = null;
        miraklWindowHandle = null;
    }
}
