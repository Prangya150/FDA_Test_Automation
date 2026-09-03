package com.fda.automation.pages.fda;

import com.fda.automation.base.BasePage;
import com.fda.automation.config.ConfigManager;
import org.openqa.selenium.By;
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;

/**
 * FDA checkout - payment step.
 *
 * LOCATOR NOTE: verified against a real, completed checkout run on 2026-08-19 (order placed
 * successfully with a real test card). The payment gateway is Adyen ("methodCode":"adyen_cc"
 * confirmed from the payment-method JSON config embedded in the page). Adyen renders card
 * number / expiry / security code as three separate "Secured Fields" iframes for PCI compliance,
 * each identified by a stable field-wrapper class (locale-independent, unlike the iframe title
 * text which is Spanish). Inside each iframe, the visible input is NOT simply "the first
 * &lt;input&gt;" - there are hidden decoy/autocomplete inputs (e.g. id="shiftTabField") that sort
 * earlier in the DOM, so the real field must be targeted via its `data-fieldtype` attribute.
 */
public class FdaPaymentPage extends BasePage {

    // Confirmed: <input type="radio" id="adyen_cc" value="adyen_cc" name="payment[method]">
    private static final By CREDIT_DEBIT_CARD_RADIO = By.id("adyen_cc");

    // NOT YET CONFIRMED live (no PayPal run has happened yet, unlike adyen_cc above) - matched by
    // id/value containing "paypal" rather than a guessed exact id, same defensive reasoning as
    // PLACE_ORDER_BUTTON's text match below. Adjust once run against the real payment method list.
    private static final By PAYPAL_RADIO =
            By.xpath("//input[@type='radio' and (contains(@id,'paypal') or contains(@value,'paypal'))]");
    // The test case's own "PayPal Pagar" button - distinct from PLACE_ORDER_BUTTON ("Completar
    // pago"), which is clicked later, after control returns from the PayPal-hosted pages.
    // NOT YET CONFIRMED live: a first real run (2026-09-03) timed out on this exact-text,
    // main-document-only version - case-insensitive matched on "pagar"/"paypal" instead of the
    // literal "Pagar" text, and also tried inside iframes in clickPayPalPagarButton() below, since
    // PayPal's own checkout-button widgets are commonly iframe-embedded (unlike Adyen's card fields,
    // whose iframe-per-field structure is separately confirmed above).
    private static final By PAYPAL_PAGAR_BUTTON = By.xpath(
            "//button[contains(translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'pagar')"
                    + " or contains(translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'paypal')]");

    private static final By CARD_NUMBER_IFRAME = By.cssSelector(".adyen-checkout__field--cardNumber iframe");
    private static final By CARD_EXPIRY_IFRAME = By.cssSelector(".adyen-checkout__field--expiryDate iframe");
    private static final By CARD_CVC_IFRAME = By.cssSelector(".adyen-checkout__field--securityCode iframe");

    private static final String CARD_NUMBER_FIELDTYPE = "encryptedCardNumber";
    private static final String CARD_EXPIRY_FIELDTYPE = "encryptedExpiryDate";
    private static final String CARD_CVC_FIELDTYPE = "encryptedSecurityCode";

    // Text-matched rather than by class: the button's own class list also includes "disabled"
    // (toggled by Adyen's paymentEnabled() state) until the card fields validate.
    private static final By PLACE_ORDER_BUTTON = By.xpath("//button[contains(normalize-space(.),'Completar pago')]");
    // Same checkout-wide ajax loading overlay seen on the shipping step; can briefly intercept clicks.
    private static final By LOADING_MASK = By.cssSelector("div.loading-mask[data-role='loader']");
    // Adyen's own card-form spinner, shown briefly while the secured-field iframes initialize.
    private static final By ADYEN_SPINNER = By.cssSelector(".adyen-checkout__spinner");

    // Adyen's test-environment 3D Secure authentication challenge: a cross-origin iframe with a
    // "Password" field (placeholder "Enter the word 'password'") and OK/Cancel buttons. Confirmed
    // live on 2026-08-20 with card 5454545454545454 - without completing this, the order is never
    // actually placed and the wait for '.checkout-success' times out.
    private static final By THREE_DS_PASSWORD_INPUT =
            By.xpath("//input[contains(translate(@placeholder,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),\"enter the word\")]");
    private static final By THREE_DS_OK_BUTTON =
            By.xpath("//button[translate(normalize-space(.),'abcdefghijklmnopqrstuvwxyz','ABCDEFGHIJKLMNOPQRSTUVWXYZ')='OK']");

    public FdaPaymentPage(WebDriver driver) {
        super(driver);
    }

    public boolean isDisplayed() {
        By paymentStepHeading = By.xpath("//*[contains(normalize-space(.),'Método de pago')]");
        return waitForVisible(paymentStepHeading).isDisplayed();
    }

    /**
     * CONFIRMED live on 2026-09-01 (TC_FBS_006, a two-seller x quantity-2 cart): the "Método de
     * pago" heading checked by {@link #isDisplayed()} renders before the payment method list
     * itself, which is populated by a follow-up ajax call - same skeleton-then-ajax pattern as the
     * Adyen secured fields below, whose own doc notes this gap widens with more line items/sellers
     * on the order. The default explicit wait (10s) wasn't enough for this bigger cart; use a
     * longer, dedicated wait instead of the shared default.
     */
    public void selectCreditDebitCardPayment() {
        WebElement radio = new WebDriverWait(driver, Duration.ofSeconds(60))
                .until(ExpectedConditions.presenceOfElementLocated(CREDIT_DEBIT_CARD_RADIO));
        // A plain click can miss this Magento-styled radio; the diagnostic run that worked used a JS click.
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", radio);
    }

    /** Selects the PayPal radio button on the payment method list. */
    public void selectPayPalPayment() {
        WebElement radio = new WebDriverWait(driver, Duration.ofSeconds(60))
                .until(ExpectedConditions.presenceOfElementLocated(PAYPAL_RADIO));
        // Same JS-click fallback as selectCreditDebitCardPayment - a plain click can miss this
        // Magento-styled radio.
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", radio);
    }

    /**
     * Clicks the "PayPal Pagar" button, which redirects (same tab or a new popup) to PayPal.
     *
     * NOT YET CONFIRMED live which of three shapes this button actually takes, so all three are
     * tried in order: (1) a distinct "Pagar"/"PayPal" button on the main checkout page, same as
     * PLACE_ORDER_BUTTON's text-match approach; (2) the same button, but rendered inside an iframe
     * (PayPal's own widgets are commonly iframe-embedded); (3) no distinct button at all - selecting
     * the PayPal radio just changes what PLACE_ORDER_BUTTON ("Completar pago") itself does, same as
     * every other payment method on this page.
     */
    public void clickPayPalPagarButton() {
        wait.until(d -> d.findElements(LOADING_MASK).stream().noneMatch(WebElement::isDisplayed));
        WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(15));

        try {
            shortWait.until(ExpectedConditions.elementToBeClickable(PAYPAL_PAGAR_BUTTON)).click();
            return;
        } catch (TimeoutException e) {
            log.debug("No 'Pagar'/'PayPal' button on the main checkout document; scanning iframes");
        }

        for (WebElement frame : driver.findElements(By.tagName("iframe"))) {
            driver.switchTo().frame(frame);
            try {
                List<WebElement> matches = driver.findElements(PAYPAL_PAGAR_BUTTON);
                if (!matches.isEmpty() && matches.get(0).isDisplayed()) {
                    matches.get(0).click();
                    return;
                }
            } finally {
                driver.switchTo().defaultContent();
            }
        }

        log.warn("No distinct PayPal 'Pagar' button found (main document or iframes); "
                + "falling back to the generic 'Completar pago' button");
        new WebDriverWait(driver, Duration.ofSeconds(30))
                .until(ExpectedConditions.elementToBeClickable(PLACE_ORDER_BUTTON)).click();
    }

    /** Fills the three Adyen secured-field iframes with the given card details. */
    public void enterCardDetails(String cardNumber, String expiry, String cvv) {
        fillSecuredField(CARD_NUMBER_IFRAME, CARD_NUMBER_FIELDTYPE, cardNumber);
        fillSecuredField(CARD_EXPIRY_IFRAME, CARD_EXPIRY_FIELDTYPE, expiry);
        fillSecuredField(CARD_CVC_IFRAME, CARD_CVC_FIELDTYPE, cvv);
    }

    /**
     * Adyen re-renders the secured field's inner input shortly after it first becomes clickable
     * (can go stale), and its loading spinner can still visually overlap the iframe for a moment
     * after that (click-intercepted). Waits for the spinner to clear first, then retries the fill
     * on either failure mode instead of failing the whole checkout on a one-off race.
     *
     * CONFIRMED live on 2026-08-31 (TC_FBS_002, a 2-item cart): with more line items the payment
     * step recalculates pricing/marketplace offers before the Adyen form is interactive, so the
     * secured field can still take longer than the default explicit.wait to become clickable -
     * also retrying on that {@link TimeoutException} (previously only StaleElement/ClickIntercepted
     * were retried) covers this instead of failing checkout on the first attempt.
     */
    private void fillSecuredField(By iframeLocator, String fieldType, String value) {
        wait.until(d -> d.findElements(ADYEN_SPINNER).stream().noneMatch(WebElement::isDisplayed));

        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            WebElement iframe = wait.until(ExpectedConditions.presenceOfElementLocated(iframeLocator));
            driver.switchTo().frame(iframe);
            try {
                By realInput = By.cssSelector("input[data-fieldtype='" + fieldType + "']");
                WebElement input = wait.until(ExpectedConditions.elementToBeClickable(realInput));
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", input);
                input.sendKeys(value);
                return;
            } catch (StaleElementReferenceException | ElementClickInterceptedException | TimeoutException e) {
                lastFailure = e;
                log.debug("Secured field '{}' failed on attempt {} ({}), retrying", fieldType, attempt, e.getClass().getSimpleName());
            } finally {
                driver.switchTo().defaultContent();
            }
        }
        throw lastFailure;
    }

    /** Reads the amount rendered on the "Completar pago" button, e.g. "Completar pago (MXN$2.00)". */
    public String getPlaceOrderButtonText() {
        return getText(PLACE_ORDER_BUTTON);
    }

    public FdaOrderSuccessPage completarPago() {
        log.info("Submitting payment / placing order");
        wait.until(d -> d.findElements(LOADING_MASK).stream().noneMatch(WebElement::isDisplayed));
        wait.until(ExpectedConditions.elementToBeClickable(PLACE_ORDER_BUTTON)).click();
        completeThreeDsChallengeIfPresent();
        return new FdaOrderSuccessPage(driver);
    }

    /**
     * Not every test-card submission triggers Adyen's 3D Secure challenge, so this polls for the
     * challenge iframe without failing if it never appears (nothing to do in that case).
     *
     * CONFIRMED live on 2026-08-20: when no challenge is shown, the page can navigate straight to
     * the success page mid-scan, invalidating the iframe references being iterated - catch that
     * instead of letting it fail the whole checkout, and just retry the scan.
     */
    private void completeThreeDsChallengeIfPresent() {
        long deadlineMillis = System.currentTimeMillis()
                + ConfigManager.getInstance().getExplicitWait() * 1000L;
        while (System.currentTimeMillis() < deadlineMillis) {
            try {
                List<WebElement> iframes = driver.findElements(By.tagName("iframe"));
                for (WebElement frame : iframes) {
                    driver.switchTo().frame(frame);
                    if (!driver.findElements(THREE_DS_PASSWORD_INPUT).isEmpty()) {
                        log.info("Completing Adyen 3D Secure test challenge");
                        wait.until(ExpectedConditions.elementToBeClickable(THREE_DS_PASSWORD_INPUT)).sendKeys("password");
                        wait.until(ExpectedConditions.elementToBeClickable(THREE_DS_OK_BUTTON)).click();
                        driver.switchTo().defaultContent();
                        return;
                    }
                    driver.switchTo().defaultContent();
                }
            } catch (StaleElementReferenceException e) {
                log.debug("Page navigated while scanning for a 3DS challenge (likely none shown for this order); retrying scan");
                driver.switchTo().defaultContent();
            }
        }
    }
}
