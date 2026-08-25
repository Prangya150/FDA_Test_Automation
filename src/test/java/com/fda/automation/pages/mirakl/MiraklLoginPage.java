package com.fda.automation.pages.mirakl;

import com.fda.automation.base.BasePage;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.time.Duration;

/**
 * Mirakl operator front office login.
 *
 * LOCATOR NOTE: farmaciasdelahorromx2-dev.mirakl.net redirects unauthenticated users to an
 * Auth0-hosted "identifier-first" login flow (confirmed via the /login/oauth2/mirakl-sso ->
 * login.mirakl.net/authorize redirect chain). The username/password screens use Auth0's standard
 * Universal Login element ids - CONFIRMED working against a real login on 2026-08-19.
 *
 * That real login also surfaced an email MFA challenge ("Verify Your Identity", URL contains
 * "mfa-email-challenge") that this framework cannot complete on its own - it requires reading a
 * one-time code from the account's inbox. There is no reusable email-fetch utility in this
 * framework, so per team direction the code is entered manually in the (non-headless) browser
 * window while the test waits; the challenge screen has "Remember this device for 30 days"
 * checked by default, so this should only be needed once per Chrome profile within that window.
 */
public class MiraklLoginPage extends BasePage {

    private static final By USERNAME_INPUT = By.cssSelector("input[name='username'], input#username");
    private static final By CONTINUE_BUTTON = By.cssSelector("button[type='submit']");
    private static final By PASSWORD_INPUT = By.cssSelector("input[name='password'], input#password");
    private static final By SIGN_IN_BUTTON = By.cssSelector("button[type='submit']");
    private static final String MFA_URL_MARKER = "mfa-email-challenge";

    public MiraklLoginPage(WebDriver driver) {
        super(driver);
    }

    public boolean isDisplayed() {
        return waitForVisible(USERNAME_INPUT).isDisplayed();
    }

    /**
     * Fast, non-throwing check for whether the login form is actually on screen. Used instead of
     * a URL-based "already authenticated" heuristic: the login form is served from the same
     * origin as the operator front office, so a URL prefix match can't distinguish the two.
     */
    public boolean isLoginFormPresent(Duration timeout) {
        try {
            new org.openqa.selenium.support.ui.WebDriverWait(driver, timeout)
                    .until(ExpectedConditions.visibilityOfElementLocated(USERNAME_INPUT));
            return true;
        } catch (org.openqa.selenium.TimeoutException e) {
            return false;
        }
    }

    public void enterUsernameAndContinue(String username) {
        type(USERNAME_INPUT, username);
        click(CONTINUE_BUTTON);
    }

    public void enterPasswordAndSignIn(String password) {
        waitForVisible(PASSWORD_INPUT);
        type(PASSWORD_INPUT, password);
        click(SIGN_IN_BUTTON);
    }

    public void login(String username, String password) {
        log.info("Logging into Mirakl as: {}", username);
        enterUsernameAndContinue(username);
        enterPasswordAndSignIn(password);
    }

    /**
     * Waits for the post-login navigation to resolve to either the MFA challenge or the operator
     * front office directly. Checking the URL synchronously right after {@link #login} is a race:
     * the browser may not have navigated off the sign-in page yet.
     */
    public void waitForPostLoginNavigation(String miraklBaseUrl, Duration timeout) {
        new org.openqa.selenium.support.ui.WebDriverWait(driver, timeout)
                .until(d -> d.getCurrentUrl().contains(miraklBaseUrl) || d.getCurrentUrl().contains(MFA_URL_MARKER));
    }

    public boolean isMfaChallengeDisplayed() {
        return driver.getCurrentUrl().contains(MFA_URL_MARKER);
    }

    /**
     * Pauses until a human enters the emailed one-time code in the visible browser window and
     * submits it (detected by the URL moving off the MFA challenge page).
     */
    public void waitForManualMfaCompletion(Duration timeout) {
        log.info("Mirakl MFA challenge displayed - waiting up to {} for the code to be entered manually in the browser window", timeout);
        new org.openqa.selenium.support.ui.WebDriverWait(driver, timeout)
                .until(d -> !d.getCurrentUrl().contains(MFA_URL_MARKER));
    }

    /** Waits for the post-login redirect back to the Mirakl operator front office to complete. */
    public void waitForRedirectToOperatorFrontOffice(String miraklBaseUrl) {
        wait.until(ExpectedConditions.urlContains(miraklBaseUrl));
    }
}
