package com.fda.automation.pages;

import com.fda.automation.base.BasePage;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.List;

/**
 * Office 365 OWA inbox (TC_SOB_001 steps 46-49): the Focused/Other tab strip and the
 * message list used to find the FDA registration confirmation email.
 *
 * PLACEHOLDER LOCATORS: not verified live in this session (see OutlookLoginPage's
 * javadoc). These follow OWA's stable ARIA structure - the tab strip exposes each tab as
 * role="tab" with its visible name ("Focused"/"Other") as the accessible name, and each
 * message row is exposed as role="option" whose accessible name concatenates sender,
 * subject, and a body preview. Re-verify against the real mailbox on first run.
 */
public class OutlookInboxPage extends BasePage {

    private static final By OTHER_TAB = By.xpath("//div[@role='tab'][normalize-space()='Other']");
    private static final By MESSAGE_ROWS = By.xpath("//div[@role='option']");

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
        List<WebElement> rows = findAll(MESSAGE_ROWS);
        for (WebElement row : rows) {
            String text = row.getText().toLowerCase();
            boolean matchesAll = true;
            for (String keyword : keywords) {
                if (!text.contains(keyword.toLowerCase())) {
                    matchesAll = false;
                    break;
                }
            }
            if (matchesAll) {
                return true;
            }
        }
        return false;
    }
}
