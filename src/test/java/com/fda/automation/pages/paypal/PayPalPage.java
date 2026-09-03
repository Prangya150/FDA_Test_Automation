package com.fda.automation.pages.paypal;

import com.fda.automation.base.BasePage;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

/**
 * PayPal-hosted checkout, reached from FDA's "PayPal Pagar" button on {@link com.fda.automation.pages.fda.FdaPaymentPage}.
 *
 * LOCATOR NOTE: NOT YET CONFIRMED live - this framework has no prior PayPal coverage, unlike the
 * FDA/Mirakl pages elsewhere in this project (whose locators carry "CONFIRMED live on ..." notes
 * from a real completed run). The email/password fields below follow PayPal's long-stable standard
 * sandbox login page ids; the "Pay with"/"Full purchase" locators on the post-login review page are
 * text-based best guesses. Re-check and correct against the real page the first time TC_FBS_007
 * actually runs, the same way every other page object here was hardened.
 */
public class PayPalPage extends BasePage {

    private static final By EMAIL_INPUT = By.id("email");
    private static final By NEXT_BUTTON = By.id("btnNext");
    private static final By PASSWORD_INPUT = By.id("password");
    private static final By LOGIN_BUTTON = By.id("btnLogin");

    private static final By FULL_PURCHASE_BUTTON =
            By.xpath("//*[self::button or @role='button'][contains(normalize-space(.),'Full purchase')]");

    public PayPalPage(WebDriver driver) {
        super(driver);
    }

    public boolean isDisplayed() {
        return waitForVisible(EMAIL_INPUT).isDisplayed();
    }

    /**
     * Logs into the PayPal sandbox account. Handles both PayPal's two-step login (email, then a
     * "Next" button, then password on a follow-up screen) and a single combined email+password
     * page, since which layout the sandbox actually serves has not been confirmed live yet.
     */
    public void login(String email, String password) {
        type(EMAIL_INPUT, email);
        if (isDisplayed(NEXT_BUTTON)) {
            click(NEXT_BUTTON);
        }
        type(PASSWORD_INPUT, password);
        if (isDisplayed(LOGIN_BUTTON)) {
            click(LOGIN_BUTTON);
        }
    }

    /** Selects a funding source (e.g. "Visa") under the "Pay with" section of PayPal's purchase review page. */
    public void selectPayWithOption(String optionLabel) {
        By option = By.xpath("//*[self::label or self::span or self::div or self::input]"
                + "[contains(normalize-space(.),'" + optionLabel + "')]");
        click(option);
    }

    public void clickFullPurchase() {
        click(FULL_PURCHASE_BUTTON);
    }
}
