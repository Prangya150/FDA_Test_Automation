package com.fda.automation.pages;

import com.fda.automation.base.BasePage;
import com.fda.automation.config.ConfigManager;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Mirakl marketplace back-office login at farmaciasdelahorromx2-dev.mirakl.net.
 *
 * Confirmed live: this is a completely different flow from MiddlewareLoginPage's
 * single-page MUI form - it redirects to login.mirakl.net, Auth0's hosted "New Universal
 * Login", as a 3-step sequence:
 *   1. identifier page (/u/login/identifier): email only, "Siguiente" advances
 *   2. password page (/u/login/password): password, "Sign in" submits
 *   3. (in this session, every time - see below) an email MFA challenge
 *      (/u/mfa-email-challenge): a 6-digit code emailed to the same address by sender
 *      "Mirakl SSO" ("Your verification code is: NNNNNN"), landing in the Focused tab
 *      (not Other, unlike the FDA seller-onboarding emails this suite otherwise reads
 *      from the Other tab)
 * The identifier/password/code field name="..." attributes are Auth0's own standard New
 * Universal Login markup (not Mirakl-specific), driven interactively via Playwright to
 * discover this flow rather than from a captured page source - re-verify if Auth0 ever
 * changes them. "Remember this device for 30 days" is checked by default on the MFA page
 * but has no effect for automation: each test run gets a fresh browser profile with no
 * carried-over cookies, so the MFA challenge is completed fresh on every run.
 */
public class MiraklLoginPage extends BasePage {

    private static final By IDENTIFIER_INPUT = By.cssSelector("input[name='username'], input[name='email']");
    private static final By NEXT_BUTTON = By.xpath("//button[normalize-space()='Siguiente']");
    private static final By PASSWORD_INPUT = By.cssSelector("input[name='password']");
    private static final By SIGN_IN_BUTTON = By.xpath("//button[normalize-space()='Sign in']");
    private static final By MFA_HEADING = By.xpath("//h1[contains(normalize-space(.),'Verify Your Identity')]");
    private static final By MFA_CODE_INPUT = By.cssSelector("input[name='code']");
    private static final By MFA_CONTINUE_BUTTON = By.xpath("//button[normalize-space()='Continue']");

    private static final Pattern OTP_PATTERN = Pattern.compile("verification code is:\\s*(\\d{4,8})");

    public MiraklLoginPage(WebDriver driver) {
        super(driver);
    }

    public MiraklLoginPage open() {
        String url = ConfigManager.getInstance().getMiraklUrl();
        log.info("Navigate to: {}", url);
        driver.get(url);
        return this;
    }

    /**
     * Logs into the Mirakl marketplace back-office. Needs Outlook credentials too because
     * Auth0 always challenges this login with an emailed one-time code in this environment
     * - handled by opening Outlook in a separate browser tab to read the code, then closing
     * that tab and switching back to the still-open Mirakl MFA tab to submit it.
     */
    public MiraklShopSearchPage login(String username, String password, String outlookUsername, String outlookPassword) {
        log.info("Logging into Mirakl marketplace as {}", username);
        type(IDENTIFIER_INPUT, username);
        click(NEXT_BUTTON);
        type(PASSWORD_INPUT, password);

        // Snapshot already-present Mirakl SSO OTP codes *before* clicking Sign In (the
        // action that triggers Auth0 to send the challenge email) - see
        // OutlookInboxPage.waitForOtpCode's javadoc for why snapshotting any later than
        // this races email delivery.
        String miraklTab = openNewTab();
        OutlookInboxPage inbox = new OutlookLoginPage(driver).open().login(outlookUsername, outlookPassword);
        java.util.Set<String> codesSeenBeforeSignIn = inbox.currentOtpCodes("Mirakl SSO", OTP_PATTERN);
        closeTabAndSwitchBack(miraklTab);

        click(SIGN_IN_BUTTON);
        completeEmailMfaChallenge(outlookUsername, outlookPassword, codesSeenBeforeSignIn);
        return new MiraklShopSearchPage(driver);
    }

    /**
     * MFA is always challenged in this environment (see class javadoc), so this waits for
     * it unconditionally rather than branching on an instant isDisplayed() check - that
     * races the page transition away from the password page (the same class of bug fixed
     * once already in OutlookLoginPage's KMSI handling) and can return false before the
     * MFA page has rendered, silently skipping the whole MFA flow and leaving the browser
     * stuck on it while the rest of the test proceeds as if login had succeeded.
     */
    private void completeEmailMfaChallenge(String outlookUsername, String outlookPassword,
            java.util.Set<String> codesSeenBeforeSignIn) {
        log.info("Waiting for Mirakl's email MFA challenge");
        waitForVisible(MFA_HEADING);
        log.info("MFA challenge shown; fetching the code from Outlook in a new tab");

        String miraklTab = openNewTab();
        OutlookInboxPage inbox = new OutlookLoginPage(driver).open().login(outlookUsername, outlookPassword);
        String code = inbox.waitForOtpCode(Duration.ofMinutes(2), "Mirakl SSO", OTP_PATTERN, codesSeenBeforeSignIn);
        closeTabAndSwitchBack(miraklTab);

        type(MFA_CODE_INPUT, code);
        click(MFA_CONTINUE_BUTTON);
    }
}
