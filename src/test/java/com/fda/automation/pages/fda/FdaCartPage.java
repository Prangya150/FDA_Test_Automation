package com.fda.automation.pages.fda;

import com.fda.automation.base.BasePage;
import com.fda.automation.utils.CurrencyUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.math.BigDecimal;
import java.util.List;

/**
 * FDA shopping cart page (Magento 2 checkout/cart).
 *
 * LOCATOR NOTE: verified against a real cart page dump on 2026-08-19 (added a live product and
 * inspected the resulting DOM). The totals table has no ".cart-totals" wrapper class as stock
 * Magento themes usually do, and it renders a separate "Impuesto" (tax) row with its own
 * `.price` span, so the grand-total locator is scoped to `tr.grand.totals` specifically to avoid
 * picking up the tax amount instead.
 *
 * CONFIRMED live on 2026-08-31 (TC_FBS_002, a 2-item cart): even after a full page navigation to
 * /checkout/cart/, the totals summary block is knockout-rendered and refreshes itself via a
 * follow-up ajax call - the same checkout-wide loading overlay used on the shipping/payment steps
 * covers that recalculation. Reading the grand total before it clears returned a stale figure
 * (the single-item total from before the second product's price was folded in).
 */
public class FdaCartPage extends BasePage {

    private static final By CART_TABLE = By.id("shopping-cart-table");
    private static final By PRODUCT_NAME = By.cssSelector("#shopping-cart-table .product-item-name a");
    private static final By QTY_INPUT = By.cssSelector("#shopping-cart-table input.qty");
    private static final By GRAND_TOTAL = By.cssSelector("tr.grand.totals td.amount .price");
    // Confirmed: <button type="button" data-role="proceed-to-checkout" title="Proceder al pago" class="action primary checkout">
    private static final By PROCEED_TO_CHECKOUT_BUTTON = By.cssSelector("button[data-role='proceed-to-checkout']");
    // Confirmed: <a href="#" title="Eliminar el artículo" class="action action-delete" data-post="...">
    private static final By REMOVE_ITEM_LINKS = By.cssSelector("#shopping-cart-table a.action-delete");
    // Same checkout-wide ajax loading overlay used on the shipping/payment steps.
    private static final By LOADING_MASK = By.cssSelector("div.loading-mask[data-role='loader']");

    public FdaCartPage(WebDriver driver) {
        super(driver);
    }

    /**
     * CONFIRMED live on 2026-09-01 (TC_FBS_005): reaching the cart page here follows an add-to-cart
     * whose mini-cart flyout was just dismissed (see FdaProductDetailsPage.dismissMiniCartFlyout),
     * so the page can still be settling from that when this is first called - an element found by
     * {@code findElements} can go stale between that call and the {@code isDisplayed()} check on
     * it, same race already seen (and fixed the same way) in FdaProductDetailsPage's skeleton wait.
     */
    private void waitForTotalsToSettle() {
        wait.until(d -> {
            try {
                return d.findElements(LOADING_MASK).stream().noneMatch(WebElement::isDisplayed);
            } catch (StaleElementReferenceException e) {
                return false;
            }
        });
    }

    public boolean isDisplayed() {
        boolean displayed = waitForVisible(CART_TABLE).isDisplayed();
        waitForTotalsToSettle();
        return displayed;
    }

    public String getProductName() {
        return getText(PRODUCT_NAME);
    }

    public String getQuantity() {
        return waitForVisible(QTY_INPUT).getAttribute("value");
    }

    /** Row locator for a specific line item, used when the cart holds more than one product. */
    private By rowForProduct(String productName) {
        return By.xpath("//table[@id='shopping-cart-table']//tr[.//a[contains(normalize-space(.),'" + productName + "')]]");
    }

    public boolean isProductDisplayed(String productName) {
        waitForVisible(CART_TABLE);
        return !driver.findElements(rowForProduct(productName)).isEmpty();
    }

    /** Reads the quantity for a single line item, identified by its product name. */
    public String getQuantityForProduct(String productName) {
        By qtyInputForProduct = By.xpath(
                "//table[@id='shopping-cart-table']//tr[.//a[contains(normalize-space(.),'" + productName + "')]]//input[contains(@class,'qty')]");
        return waitForVisible(qtyInputForProduct).getAttribute("value");
    }

    /**
     * Parses the displayed grand total into a BigDecimal for later numeric comparison against Mirakl.
     *
     * CONFIRMED live on 2026-08-31 (TC_FBS_002, a 2-item cart): the totals summary is
     * knockout-bound and its text can still be updating in place shortly after the cart page
     * loads (no visibility change, so waitForVisible alone doesn't catch it) - a first read
     * returned the stale single-item total. Polls until two consecutive reads agree, rather than
     * trusting the very first one.
     */
    public BigDecimal getCartTotal() {
        waitForTotalsToSettle();
        String[] previousText = {null};
        wait.until(d -> {
            String current = getText(GRAND_TOTAL);
            boolean stable = current.equals(previousText[0]);
            previousText[0] = current;
            return stable;
        });
        return CurrencyUtils.parseCurrency(previousText[0]);
    }

    public FdaShippingPage proceedToCheckout() {
        wait.until(ExpectedConditions.elementToBeClickable(PROCEED_TO_CHECKOUT_BUTTON)).click();
        return new FdaShippingPage(driver);
    }

    /**
     * Removes every line item from the cart so a fresh run starts from a known-empty state
     * (a shared test account's cart otherwise persists quantity across executions, which would
     * break the "quantity is 1" assertions the test case requires).
     */
    public void emptyCart() {
        List<org.openqa.selenium.WebElement> removeLinks = driver.findElements(REMOVE_ITEM_LINKS);
        while (!removeLinks.isEmpty()) {
            log.info("Removing existing cart item before starting a fresh run");
            int countBeforeRemoval = removeLinks.size();
            removeLinks.get(0).click();
            wait.until(d -> d.findElements(REMOVE_ITEM_LINKS).size() < countBeforeRemoval);
            removeLinks = driver.findElements(REMOVE_ITEM_LINKS);
        }
    }

    public boolean isEmpty() {
        return driver.findElements(CART_TABLE).isEmpty();
    }
}
