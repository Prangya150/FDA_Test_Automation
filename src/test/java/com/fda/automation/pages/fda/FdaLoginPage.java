package com.fda.automation.pages.fda;

import com.fda.automation.base.BasePage;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;

/**
 * FDA storefront (Magento 2 + Amasty menu + Empathy/Infinite search) header account menu + login form.
 *
 * LOCATOR NOTE: verified against the live DOM (dumped outerHTML of the header and the
 * /customer/account/login/ page on 2026-08-19). The account entry point in the header is an
 * icon-only button with no visible text (styled via CSS, labelled only via aria-label), which is
 * why a "Mi cuenta" text-based locator never matched anything.
 */
public class FdaLoginPage extends BasePage {

    // Confirmed: <button type="button" class="action switch" data-action="customer-menu-toggle" aria-label="Mi cuenta"></button>
    private static final By ACCOUNT_MENU_TOGGLE = By.cssSelector("button[data-action='customer-menu-toggle']");

    // Confirmed: <a class="customer-sign-in-link" href="https://mcstaging.fahorro.com/customer/account/login/">
    private static final By LOGIN_LINK = By.cssSelector("a.customer-sign-in-link");

    // Confirmed via dumped login page markup. The page renders a second, hidden login-form
    // template with duplicate ids (id="pass", id="send2", id="login-form" both occur twice), so
    // the unique `name="login[...]"` attributes are used instead of id for the password field and
    // the submit button is scoped to the form that actually owns the real username field.
    private static final By EMAIL_INPUT = By.name("login[username]");
    private static final By PASSWORD_INPUT = By.name("login[password]");
    private static final By LOGIN_SUBMIT_BUTTON =
            By.xpath("//input[@name='login[password]']/ancestor::form//button[@type='submit']");

    public FdaLoginPage(WebDriver driver) {
        super(driver);
    }

    public FdaLoginPage openAccountMenu() {
        click(ACCOUNT_MENU_TOGGLE);
        return this;
    }

    /**
     * The account menu dropdown can occasionally fail to stay open (or fail to open) after the
     * toggle click - observed live on 2026-08-20 as a one-off flake after two prior clean runs.
     * Retries the toggle click rather than failing the whole checkout on a transient miss.
     */
    public FdaLoginPage clickLoginLink() {
        org.openqa.selenium.TimeoutException lastFailure = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                wait.until(ExpectedConditions.elementToBeClickable(LOGIN_LINK)).click();
                return this;
            } catch (org.openqa.selenium.TimeoutException e) {
                lastFailure = e;
                log.debug("Login link not clickable on attempt {}, retrying account menu toggle", attempt);
                click(ACCOUNT_MENU_TOGGLE);
            }
        }
        throw lastFailure;
    }

    public boolean isLoginFormDisplayed() {
        return waitForVisible(EMAIL_INPUT).isDisplayed();
    }

    public void login(String username, String password) {
        log.info("Logging into FDA as: {}", username);
        type(EMAIL_INPUT, username);
        type(PASSWORD_INPUT, password);
        click(LOGIN_SUBMIT_BUTTON);
    }
}
