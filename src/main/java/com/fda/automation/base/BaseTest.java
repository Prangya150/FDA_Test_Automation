package com.fda.automation.base;

import com.fda.automation.reporting.StepLogger;
import com.fda.automation.utils.DriverFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebDriver;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import java.time.Duration;

public class BaseTest {
    protected static final Logger log = LogManager.getLogger(BaseTest.class);
    private final ThreadLocal<WebDriver> driverHolder = new ThreadLocal<>();

    @BeforeMethod(alwaysRun = true)
    public void setUp() {
        WebDriver driver = DriverFactory.createDriver();
        driver.manage().window().maximize();
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(0)); // rely on explicit waits only
        driverHolder.set(driver);
        StepLogger.setDriver(driver);
        log.info("Browser started [thread={}]", Thread.currentThread().getId());
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        WebDriver driver = driverHolder.get();
        if (driver != null) {
            StepLogger.clearDriver();
            driver.quit();
            driverHolder.remove();
            log.info("Browser closed [thread={}]", Thread.currentThread().getId());
        }
    }

    public WebDriver getDriver() {
        return driverHolder.get();
    }
}
