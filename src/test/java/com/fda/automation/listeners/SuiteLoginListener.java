package com.fda.automation.listeners;

import com.fda.automation.base.SuiteSession;
import com.fda.automation.config.ConfigManager;
import com.fda.automation.pages.fda.FdaHomePage;
import com.fda.automation.pages.fda.FdaLoginPage;
import com.fda.automation.pages.mirakl.MiraklLoginPage;
import com.fda.automation.utils.DriverFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WindowType;
import org.testng.ISuite;
import org.testng.ISuiteListener;

import java.time.Duration;

/**
 * Logs into FDA and Mirakl exactly once for the whole suite, instead of every TC_FBS_00N_Test
 * logging in (and clearing its cart, and potentially hitting Mirakl's manual MFA challenge)
 * independently.
 *
 * Implemented as an {@link ISuiteListener} rather than {@code @BeforeSuite}/{@code @AfterSuite} on
 * the shared {@code BaseTest} base class: TestNG guarantees {@code onStart}/{@code onFinish} run
 * exactly once per suite, whereas {@code @BeforeSuite} inherited by all seven TC_FBS_00N_Test
 * subclasses in the same suite is not guaranteed to run only once. Lives in src/test/java (not
 * alongside {@link SuiteSession} in src/main/java) because it needs the FDA/Mirakl page objects,
 * which main sources cannot depend on.
 *
 * Requires the suite to run sequentially (no parallel="methods" in testng.xml) - a single shared
 * WebDriver cannot safely be driven by more than one thread at a time.
 */
public class SuiteLoginListener implements ISuiteListener {

    private static final Logger log = LogManager.getLogger(SuiteLoginListener.class);

    @Override
    public void onStart(ISuite suite) {
        ConfigManager config = ConfigManager.getInstance();

        WebDriver driver = DriverFactory.createDriver();
        driver.manage().window().maximize();
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(0)); // rely on explicit waits only
        SuiteSession.setDriver(driver);
        log.info("Suite browser session started");

        loginToFda(driver, config);
        SuiteSession.setFdaWindowHandle(driver.getWindowHandle());

        driver.switchTo().newWindow(WindowType.TAB);
        SuiteSession.setMiraklWindowHandle(driver.getWindowHandle());
        driver.get(config.getMiraklBaseUrl());
        loginToMirakl(driver, config);

        driver.switchTo().window(SuiteSession.getFdaWindowHandle());
    }

    @Override
    public void onFinish(ISuite suite) {
        WebDriver driver = SuiteSession.getDriver();
        if (driver == null) {
            return;
        }
        try {
            driver.switchTo().window(SuiteSession.getFdaWindowHandle());
            driver.get(ConfigManager.getInstance().getFdaBaseUrl() + "/customer/account/logout/");
            log.info("Logged out of FDA");
        } catch (RuntimeException e) {
            log.warn("FDA logout failed, closing the browser anyway", e);
        } finally {
            driver.quit();
            SuiteSession.clear();
            log.info("Suite browser session closed");
        }
    }

    private void loginToFda(WebDriver driver, ConfigManager config) {
        // Log out first so this always starts from a known-logged-out state regardless of what a
        // previous run (or a persistent Chrome profile, see chrome.user.data.dir) left behind.
        driver.get(config.getFdaBaseUrl() + "/customer/account/logout/");
        driver.get(config.getFdaBaseUrl());

        FdaLoginPage loginPage = new FdaLoginPage(driver);
        loginPage.openAccountMenu();
        loginPage.clickLoginLink();
        loginPage.login(config.getFdaUsername(), config.getFdaPassword());

        new FdaHomePage(driver).waitUntilLoaded();
        log.info("Logged into FDA once for the whole suite as: {}", config.getFdaUsername());
    }

    private void loginToMirakl(WebDriver driver, ConfigManager config) {
        MiraklLoginPage loginPage = new MiraklLoginPage(driver);

        // With a persistent Chrome profile (chrome.user.data.dir), a previous run's authenticated
        // Mirakl session can still be active, landing directly on the dashboard instead of the
        // login form - check for the form itself rather than a URL prefix (login is served from
        // the same origin as the operator front office).
        if (!loginPage.isLoginFormPresent(Duration.ofSeconds(10))) {
            log.info("Mirakl session already authenticated from a previous run; skipping login");
            return;
        }

        loginPage.login(config.getMiraklUsername(), config.getMiraklPassword());
        loginPage.waitForPostLoginNavigation(config.getMiraklBaseUrl(), Duration.ofSeconds(20));

        // Mirakl's Auth0 login can present an email MFA challenge; this framework has no email
        // integration to read the code, so it is entered manually in the visible browser window.
        if (loginPage.isMfaChallengeDisplayed()) {
            loginPage.waitForManualMfaCompletion(Duration.ofMinutes(5));
        }

        loginPage.waitForRedirectToOperatorFrontOffice(config.getMiraklBaseUrl());
        log.info("Logged into Mirakl once for the whole suite as: {}", config.getMiraklUsername());
    }

    /**
     * Re-authenticates Mirakl if its Auth0 session expired in the background - CONFIRMED live
     * (TC_FBS_006) to happen after just a few minutes on the FDA tab. Now that Mirakl login
     * happens once at the very start of the whole suite rather than fresh before each test's
     * Mirakl step, later tests in the suite can be tens of minutes past that login, making this
     * check load-bearing for every test rather than an edge case for just the last one or two.
     * Called from every TC_FBS_00N_Test.switchToMiraklTab() so it's covered automatically.
     */
    public static void reauthenticateIfExpired() {
        WebDriver driver = SuiteSession.getDriver();
        MiraklLoginPage loginPage = new MiraklLoginPage(driver);
        if (!loginPage.isLoginFormPresent(Duration.ofSeconds(5))) {
            return;
        }

        log.info("Mirakl session expired mid-suite; logging back in");
        ConfigManager config = ConfigManager.getInstance();
        loginPage.login(config.getMiraklUsername(), config.getMiraklPassword());
        loginPage.waitForPostLoginNavigation(config.getMiraklBaseUrl(), Duration.ofSeconds(20));
        if (loginPage.isMfaChallengeDisplayed()) {
            loginPage.waitForManualMfaCompletion(Duration.ofMinutes(5));
        }
        loginPage.waitForRedirectToOperatorFrontOffice(config.getMiraklBaseUrl());
    }
}
