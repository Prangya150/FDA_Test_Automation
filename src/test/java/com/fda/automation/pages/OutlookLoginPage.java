package com.fda.automation.pages;

import com.fda.automation.base.BasePage;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

/**
 * Microsoft/Office 365 login flow at https://outlook.office365.com/mail/
 * (TC_SOB_001 steps 42-45).
 *
 * Confirmed live against the real login.microsoftonline.com page reached from
 * https://outlook.office365.com/mail/: the email field is name="loginfmt" (id="i0116"),
 * the password field is name="passwd", and Next/Sign-in/the "Stay signed in?" Yes button
 * all reuse id="idSIButton9" (an &lt;input type="submit"&gt;, not a &lt;button&gt;) - including
 * on the org-branded (KogniVera) KMSI page.
 */
public class OutlookLoginPage extends BasePage {

    private static final String OUTLOOK_URL = "https://outlook.office365.com/mail/";

    private static final By EMAIL_INPUT = By.name("loginfmt");
    private static final By PASSWORD_INPUT = By.name("passwd");
    // Reused by Microsoft across this flow for Next / Sign in / "Stay signed in?" Yes.
    private static final By PRIMARY_BUTTON = By.id("idSIButton9");

    public OutlookLoginPage(WebDriver driver) {
        super(driver);
    }

    public OutlookLoginPage open() {
        log.info("Navigate to: {}", OUTLOOK_URL);
        driver.get(OUTLOOK_URL);
        return this;
    }

    public OutlookInboxPage login(String username, String password) {
        log.info("Logging into Outlook as {}", username);
        if (isLoginFormShown()) {
            type(EMAIL_INPUT, username);
            click(PRIMARY_BUTTON);
            type(PASSWORD_INPUT, password);
            click(PRIMARY_BUTTON);
            dismissStaySignedInPromptIfShown();
        } else {
            log.info("Login form not shown (session already authenticated from an earlier login this run); reusing it");
        }
        return new OutlookInboxPage(driver);
    }

    /**
     * Confirmed live: once "Stay signed in?" is accepted (dismissStaySignedInPromptIfShown),
     * Microsoft persists the session for the rest of the browser profile - a later open()
     * navigation to the same mail URL (e.g. this suite's later stages re-checking the same
     * mailbox for a different email) lands straight on the inbox with no loginfmt field at
     * all, rather than re-showing the login form. Unconditionally typing into a field that
     * may never appear timed out after 10s on every login() call after the first in a run;
     * checking up front (same short-wait-and-catch pattern as
     * dismissStaySignedInPromptIfShown) avoids that.
     */
    private boolean isLoginFormShown() {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(5))
                    .until(ExpectedConditions.visibilityOfElementLocated(EMAIL_INPUT));
            return true;
        } catch (TimeoutException e) {
            return false;
        }
    }

    /**
     * The "Stay signed in?" (KMSI) prompt after a successful sign-in is optional, and its
     * arrival races the page transition away from the password page - a bare isDisplayed()
     * check run immediately after the sign-in click can fire before the KMSI page has
     * rendered, find nothing, and skip the click entirely, leaving the browser stuck on
     * the KMSI page instead of the inbox. Wait briefly for it instead of checking once;
     * the wait is short (vs. the page's normal explicit wait) since this prompt may
     * legitimately never appear.
     */
    private void dismissStaySignedInPromptIfShown() {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(5))
                    .until(ExpectedConditions.elementToBeClickable(PRIMARY_BUTTON))
                    .click();
        } catch (TimeoutException e) {
            log.debug("\"Stay signed in?\" prompt did not appear; continuing");
        }
    }
}
