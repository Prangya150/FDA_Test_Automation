package com.fda.automation.pages.fda;

import com.fda.automation.base.BasePage;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

/**
 * FDA product detail page (PDP).
 *
 * LOCATOR NOTE: verified against a real, Mirakl-fulfilled marketplace product's live PDP on
 * 2026-08-19 (identifiable via hidden `mirakl_estimated_delivery_date` / `mirakl_msi_offer` form
 * fields - this storefront sells both first-party and marketplace/FBS items through the same
 * Magento PDP template). Two things confirmed there:
 *  - The right-hand price/stock/add-to-cart panel renders as a skeleton loader first and is
 *    populated by a follow-up client-side data fetch, so the add-to-cart button can briefly be
 *    present-but-disabled; {@link #isAddToCartEnabled()} waits for it to become clickable rather
 *    than just visible, to avoid a false negative while that fetch is still in flight.
 *  - The real `id="qty"` input is `type="hidden"` - the visible +/- stepper UI is a separate
 *    custom control that keeps it in sync via JS. {@link #getQuantity()} therefore waits for
 *    presence, not visibility, or it would time out on a perfectly normal hidden field.
 *  - The qty-stepper skeleton placeholder (`.skeleton-qty-input`) can still be occupying the same
 *    screen position as the real Add to Cart button for a moment during that same layout
 *    settle, which throws ElementClickInterceptedException on a click that lands mid-shift.
 *    {@link #addToCart()} waits for it to disappear first.
 *  - Add-to-cart is ajax-driven: the header minicart badge updates before the server-side session
 *    fully persists the addition, so navigating to /checkout/cart/ immediately after clicking can
 *    land on a page still rendering the pre-add "empty cart" state. {@link #addToCart()} waits for
 *    the minicart badge to reflect a non-zero count before returning, which is a reliable signal
 *    that the add actually completed.
 */
public class FdaProductDetailsPage extends BasePage {

    // Magento 2 Luma default PDP ids
    private static final By PRODUCT_TITLE = By.cssSelector("h1.page-title span.base");
    private static final By ADD_TO_CART_BUTTON = By.id("product-addtocart-button");
    private static final By QTY_INPUT = By.id("qty");
    private static final By SKELETON_LOADERS = By.cssSelector("[class*='skeleton']");
    // Confirmed against live header HTML: <a href="https://mcstaging.fahorro.com/checkout/cart/">Mi carrito</a>
    private static final By MI_CARRITO_LINK = By.cssSelector("a[href*='checkout/cart']");
    private static final By MINICART_COUNTER = By.cssSelector(".minicart-wrapper .counter-number");
    // Standard Magento Luma minicart toggle (the same ".minicart-wrapper" scope as MINICART_COUNTER,
    // already confirmed live) - clicking it again closes the flyout it auto-opens on add-to-cart.
    private static final By MINICART_TOGGLE = By.cssSelector(".minicart-wrapper .action.showcart");
    // TODO: unconfirmed against a live DOM (TC_FBS_003) - the visible qty-stepper "+" control is a
    // custom widget, not the hidden #qty input itself (see class doc). Matched on common signals
    // (aria-label, literal "+" text, an increase/plus class) rather than one guessed class name, so
    // it has a reasonable chance of hitting the real control; adjust once the markup is confirmed.
    private static final By QTY_INCREASE_BUTTON = By.xpath(
            "//*[self::button or self::a or self::span or self::div]"
                    + "[contains(translate(@aria-label,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'increas')"
                    + " or contains(translate(@aria-label,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'aumentar')"
                    + " or normalize-space(.)='+'"
                    + " or contains(concat(' ',normalize-space(@class),' '),' increase')"
                    + " or contains(concat(' ',normalize-space(@class),' '),' plus')]");

    public FdaProductDetailsPage(WebDriver driver) {
        super(driver);
    }

    public boolean isDisplayed() {
        return waitForVisible(PRODUCT_TITLE).isDisplayed();
    }

    public String getProductName() {
        return getText(PRODUCT_TITLE);
    }

    /**
     * Waits for the price/stock/qty skeleton placeholders to finish their layout settle.
     *
     * CONFIRMED live on 2026-08-31 (TC_FBS_002, searching a second product from an already-loaded
     * PDP): searching from the header box mid-flow navigates this same page to a new PDP via a
     * client-side route change, so an element found by {@code findElements} can go stale between
     * that call and the {@code isDisplayed()} check on it, on the very next poll tick. Treat that
     * as "not settled yet" rather than letting it fail the whole poll.
     */
    private void waitForSkeletonsToClear() {
        wait.until(d -> {
            try {
                return d.findElements(SKELETON_LOADERS).stream().noneMatch(WebElement::isDisplayed);
            } catch (StaleElementReferenceException e) {
                return false;
            }
        });
    }

    /**
     * Waits for the add-to-cart button to become clickable (see class doc: it can be
     * present-but-disabled while a follow-up client-side price/stock fetch is still in flight).
     *
     * CONFIRMED live on 2026-09-01 (TC_FBS_003): that fetch can occasionally take longer than a
     * single explicit-wait window on a first PDP load - manually confirmed the button was in fact
     * enabled shortly after one wait window had already timed out. Retries a few times instead of
     * giving up after the first window, same pattern as {@link FdaPaymentPage#fillSecuredField}.
     */
    public boolean isAddToCartEnabled() {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return wait.until(ExpectedConditions.elementToBeClickable(ADD_TO_CART_BUTTON)).isEnabled();
            } catch (org.openqa.selenium.TimeoutException e) {
                log.debug("Add-to-cart button not yet clickable on attempt {}, retrying", attempt);
            }
        }
        return false;
    }

    public String getQuantity() {
        return waitForPresent(QTY_INPUT).getAttribute("value");
    }

    /**
     * Clicks the visible "+" quantity stepper once and waits for the hidden #qty input (kept in
     * sync via JS, see class doc) to reflect the change, so a subsequent {@link #addToCart()}
     * doesn't race the sync and add the old quantity.
     */
    public void increaseQuantity() {
        log.info("Increasing PDP quantity by 1");
        String before = getQuantity();
        click(QTY_INCREASE_BUTTON);
        wait.until(d -> !getQuantity().equals(before));
    }

    /**
     * Clicks the "+" stepper as many times as needed to reach the given quantity; a no-op if the
     * PDP is already at or above it.
     *
     * CONFIRMED live on 2026-09-01 (TC_FBS_004): the stepper's state is not reset by the
     * client-side route change between products - searching a second product from an
     * already-loaded PDP can land on the new PDP with the *previous* product's selected quantity
     * (e.g. 2) already showing, rather than the usual default of 1. When that carried-over value
     * already matches the desired quantity there is nothing to click - only "+" is needed here,
     * there's no requirement to force the stepper back down to a particular starting value first.
     */
    public void ensureQuantity(int desiredQuantity) {
        while (Integer.parseInt(getQuantity()) < desiredQuantity) {
            increaseQuantity();
        }
    }

    /** Adds the product via Magento's ajax add-to-cart; stays on the PDP (minicart count updates in place). */
    public void addToCart() {
        log.info("Adding product to cart from PDP");
        waitForSkeletonsToClear();
        click(ADD_TO_CART_BUTTON);
        wait.until(d -> {
            String count = waitForPresent(MINICART_COUNTER).getText().trim();
            return !count.isEmpty() && !count.equals("0");
        });
        dismissMiniCartFlyout();
    }

    /**
     * CONFIRMED live on 2026-09-01 (TC_FBS_004): adding an item auto-opens the mini-cart
     * flyout/sidebar, which visually covers the header - including the search box - blocking a
     * subsequent search for another product. Clicking the same showcart toggle that opened it
     * closes it again (standard Magento Luma minicart behavior); Escape as an extra, harmless
     * safety net in case the flyout also responds to it or the toggle locator doesn't match.
     */
    private void dismissMiniCartFlyout() {
        if (!driver.findElements(MINICART_TOGGLE).isEmpty()) {
            click(MINICART_TOGGLE);
        }
        driver.switchTo().activeElement().sendKeys(Keys.ESCAPE);
    }

    /** Navigates from the PDP header link to the full cart page, per the "Mi carrito" test step. */
    public FdaCartPage openMiCarrito() {
        click(MI_CARRITO_LINK);
        return new FdaCartPage(driver);
    }
}
