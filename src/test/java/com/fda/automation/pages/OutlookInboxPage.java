package com.fda.automation.pages;

import com.fda.automation.base.BasePage;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Office 365 OWA inbox (TC_SOB_001 steps 46-49): the Focused/Other tab strip and the
 * message list used to find the FDA registration confirmation email.
 *
 * Confirmed live: the tab strip is a Fluent UI TabList - each tab is a
 * {@code <button role="tab" value="focused|other">}, not a {@code <div>}, and its label
 * is duplicated across two child {@code <span>}s (one visible, one a reserved-space
 * clone for layout), so matching on the button's own normalize-space() text ("Other")
 * fails - it concatenates to "OtherOther". Matched on the stable {@code value} attribute
 * instead. Each message row is a {@code <div role="option">} whose aria-label
 * concatenates sender, subject, and a body preview - this part of the original
 * placeholder was already correct.
 */
public class OutlookInboxPage extends BasePage {

    private static final By OTHER_TAB = By.cssSelector("button[role='tab'][value='other']");
    private static final By MESSAGE_ROWS = By.xpath("//div[@role='option']");

    // Not confirmed live like OTHER_TAB/MESSAGE_ROWS above (see class javadoc) - OWA
    // renders the opened message's body in a div carrying this aria-label. Used only to
    // pull the body text for reporting, so a miss here degrades to an empty string rather
    // than failing the stage - see getEmailBodyText().
    private static final By READING_PANE_BODY = By.cssSelector("div[aria-label='Message body']");

    public OutlookInboxPage(WebDriver driver) {
        super(driver);
    }

    public void openOtherTab() {
        click(OTHER_TAB);
    }

    /**
     * Scans the currently loaded message rows (Other tab) for one whose sender/subject/
     * preview text matches all of the given keywords (case-insensitive), e.g.
     * hasEmailContaining("FDA", "exitosa") for a successful-registration notification.
     */
    public boolean hasEmailContaining(String... keywords) {
        return findMatchingRow(keywords) != null;
    }

    /**
     * Polls for a matching row instead of checking once. Each stage's notification email
     * (registration ack, pre-approval, final approval) is sent asynchronously after the
     * triggering action, so it is not necessarily in the inbox yet the instant this page
     * loads - OWA updates the message list live as mail arrives, so this just re-queries
     * the DOM periodically rather than reloading the page.
     */
    public boolean waitForEmailContaining(Duration timeout, String... keywords) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (true) {
            if (findMatchingRow(keywords) != null) {
                return true;
            }
            if (System.currentTimeMillis() >= deadline) {
                return false;
            }
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    /** Opens (clicks) the first message row matching all keywords, into the reading pane. */
    public void openEmailContaining(String... keywords) {
        WebElement row = findMatchingRow(keywords);
        if (row == null) {
            throw new NoSuchElementException("No message found matching: " + String.join(", ", keywords));
        }
        row.click();
    }

    /**
     * Reads the href of a link (by its visible text, e.g. "Ingresa al link") from the
     * currently open email's reading pane. Call openEmailContaining(...) first.
     */
    public String extractLinkHref(String linkText) {
        By link = By.xpath("//a[normalize-space()=\"" + linkText + "\"]");
        return waitForVisible(link).getAttribute("href");
    }

    /**
     * Reads the visible text of the currently open email's body (reading pane). Call
     * openEmailContaining(...) first. Best-effort for reporting purposes only - returns
     * an empty string instead of throwing if READING_PANE_BODY doesn't match (see its
     * javadoc), so a report-only lookup can never fail a stage that already passed its
     * real assertion (waitForEmailContaining).
     */
    public String getEmailBodyText() {
        try {
            return waitForVisible(READING_PANE_BODY).getText();
        } catch (TimeoutException e) {
            log.debug("Could not locate reading pane body to capture for reporting");
            return "";
        }
    }

    /**
     * Polls the currently displayed tab (Focused by default - unlike the FDA
     * seller-onboarding notification emails this suite otherwise reads from the Other
     * tab, one-time MFA codes such as Mirakl's "Mirakl SSO" sender land in Focused) for a
     * message from senderKeyword whose text matches codePattern, returning the text
     * captured by the pattern's first capture group (e.g. the digits of "Your
     * verification code is: 123456").
     *
     * Confirmed live: unlike the trade-name-scoped waits elsewhere in this suite, OTP codes
     * carry no business identifier to filter on, and this mailbox accumulates every past
     * run's "Mirakl SSO" codes too - grabbing the first matching row regardless of when it
     * arrived returned a stale code left over from an earlier run, which Auth0 then rejected
     * (data-error-code="invalid-code"). Only accepting a code that isn't in
     * {@code codesSeenBeforeTrigger} avoids that.
     *
     * That baseline must be captured by the caller via {@link #currentOtpCodes} *before*
     * performing the action that triggers the challenge email (e.g. before clicking Mirakl's
     * "Sign in"), not after - snapshotting it here, after this method starts, raced email
     * delivery: on a fast delivery, the code could already be in the inbox by the time this
     * method got around to snapshotting, making it wrongly look "already present" and get
     * rejected as stale, timing out even though the correct code was sitting right there
     * (observed live).
     */
    public String waitForOtpCode(Duration timeout, String senderKeyword, Pattern codePattern,
            java.util.Set<String> codesSeenBeforeTrigger) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (true) {
            for (String code : currentOtpCodes(senderKeyword, codePattern)) {
                if (!codesSeenBeforeTrigger.contains(code)) {
                    return code;
                }
            }
            if (System.currentTimeMillis() >= deadline) {
                throw new NoSuchElementException("No NEW OTP email found from \"" + senderKeyword
                        + "\" matching " + codePattern + " within " + timeout
                        + " (codes already present before the triggering action don't count - see javadoc)");
            }
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Snapshots the OTP codes from senderKeyword currently in the inbox matching
     * codePattern - call this *before* triggering a new challenge email, then pass the
     * result to {@link #waitForOtpCode} as the baseline to exclude. See that method's
     * javadoc for why the timing of this snapshot matters.
     */
    public java.util.Set<String> currentOtpCodes(String senderKeyword, Pattern codePattern) {
        java.util.Set<String> codes = new java.util.LinkedHashSet<>();
        for (WebElement row : findAll(MESSAGE_ROWS)) {
            // OWA updates the message list live as mail arrives - which is exactly what
            // this is polling for - so a row can go stale between findAll() locating it and
            // getText() reading it. Skip it rather than letting the whole poll attempt blow
            // up: it's either been replaced by a fresher row findAll() will pick up on the
            // next call, or removed outright, neither of which should abort polling.
            try {
                String text = row.getText();
                if (text.toLowerCase().contains(senderKeyword.toLowerCase())) {
                    Matcher matcher = codePattern.matcher(text);
                    if (matcher.find()) {
                        codes.add(matcher.group(1));
                    }
                }
            } catch (StaleElementReferenceException e) {
                log.debug("Skipping stale message row while scanning for OTP codes");
            }
        }
        return codes;
    }

    private WebElement findMatchingRow(String... keywords) {
        List<WebElement> rows = findAll(MESSAGE_ROWS);
        for (WebElement row : rows) {
            // Same live-list race as currentOtpCodes() - skip a row that went stale between
            // findAll() and getText() rather than aborting the whole scan.
            try {
                String text = row.getText().toLowerCase();
                boolean matchesAll = true;
                for (String keyword : keywords) {
                    if (!text.contains(keyword.toLowerCase())) {
                        matchesAll = false;
                        break;
                    }
                }
                if (matchesAll) {
                    return row;
                }
            } catch (StaleElementReferenceException e) {
                log.debug("Skipping stale message row while scanning for a matching email");
            }
        }
        return null;
    }
}
