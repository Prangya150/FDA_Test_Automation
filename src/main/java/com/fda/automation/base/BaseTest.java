package com.fda.automation.base;

<<<<<<< Updated upstream
import com.fda.automation.reporting.StepLogger;
import com.fda.automation.utils.DriverFactory;
=======
>>>>>>> Stashed changes
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebDriver;

/**
 * TestNG base for the TC_FBS_00N tests.
 *
 * The WebDriver and the shared FDA/Mirakl login are owned by {@code SuiteLoginListener} (an
 * ISuiteListener wired in testng.xml), which TestNG guarantees runs exactly once per suite -
 * unlike {@code @BeforeSuite}/{@code @AfterSuite} declared here and inherited by all seven
 * TC_FBS_00N_Test subclasses, which would each get their own instance and could re-run per class.
 * This class only exposes the shared driver so every test reuses the same logged-in session
 * instead of each test logging in independently.
 *
 * Requires the suite to run sequentially (no parallel="methods" in testng.xml): a single shared
 * WebDriver cannot safely be driven by more than one thread at a time.
 */
public class BaseTest {
    protected static final Logger log = LogManager.getLogger(BaseTest.class);
<<<<<<< Updated upstream
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
=======
>>>>>>> Stashed changes

    public WebDriver getDriver() {
        return SuiteSession.getDriver();
    }
}
