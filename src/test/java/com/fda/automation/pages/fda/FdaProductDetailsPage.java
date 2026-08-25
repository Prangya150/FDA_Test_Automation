package com.fda.automation.pages.fda;

import com.fda.automation.base.BasePage;
import org.openqa.selenium.By;
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

    public FdaProductDetailsPage(WebDriver driver) {
        super(driver);
    }

    public boolean isDisplayed() {
        return waitForVisible(PRODUCT_TITLE).isDisplayed();
    }

    /** Waits for the price/stock/qty skeleton placeholders to finish their layout settle. */
    private void waitForSkeletonsToClear() {
        wait.until(d -> d.findElements(SKELETON_LOADERS).stream().noneMatch(WebElement::isDisplayed));
    }

    public boolean isAddToCartEnabled() {
        try {
            return wait.until(ExpectedConditions.elementToBeClickable(ADD_TO_CART_BUTTON)).isEnabled();
        } catch (org.openqa.selenium.TimeoutException e) {
            return false;
        }
    }

    public String getQuantity() {
        return waitForPresent(QTY_INPUT).getAttribute("value");
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
    }

    /** Navigates from the PDP header link to the full cart page, per the "Mi carrito" test step. */
    public FdaCartPage openMiCarrito() {
        click(MI_CARRITO_LINK);
        return new FdaCartPage(driver);
    }
}
