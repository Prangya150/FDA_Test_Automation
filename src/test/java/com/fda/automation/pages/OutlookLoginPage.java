package com.fda.automation.pages;

import com.fda.automation.base.BasePage;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

/**
 * Microsoft/Office 365 login flow at https://outlook.office365.com/mail/
 * (TC_SOB_001 steps 42-45).
 *
 * Confirmed live against the real login.microsoftonline.com page reached from
 * https://outlook.office365.com/mail/: the email field is name="loginfmt" (id="i0116")
 * and the Next button is id="idSIButton9" (an &lt;input type="submit"&gt;, not a
 * &lt;button&gt;). The password field (name="passwd") and the "Stay signed in?" Yes button
 * reusing id="idSIButton9" are NOT independently verified - going further would mean
 * typing a real password into a tool call, which isn't done from an automated session.
 * Re-verify those two once this runs with real credentials.
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
        type(EMAIL_INPUT, username);
        click(PRIMARY_BUTTON);
        type(PASSWORD_INPUT, password);
        click(PRIMARY_BUTTON);
        // "Stay signed in?" prompt is optional; confirm it if Microsoft shows it.
        if (isDisplayed(PRIMARY_BUTTON)) {
            click(PRIMARY_BUTTON);
        }
        return new OutlookInboxPage(driver);
    }
}
