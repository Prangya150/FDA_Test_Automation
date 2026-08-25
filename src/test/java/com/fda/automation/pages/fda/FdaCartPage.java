package com.fda.automation.pages.fda;

import com.fda.automation.base.BasePage;
import com.fda.automation.utils.CurrencyUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
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

    public FdaCartPage(WebDriver driver) {
        super(driver);
    }

    public boolean isDisplayed() {
        return waitForVisible(CART_TABLE).isDisplayed();
    }

    public String getProductName() {
        return getText(PRODUCT_NAME);
    }

    public String getQuantity() {
        return waitForVisible(QTY_INPUT).getAttribute("value");
    }

    /** Parses the displayed grand total into a BigDecimal for later numeric comparison against Mirakl. */
    public BigDecimal getCartTotal() {
        String rawTotal = getText(GRAND_TOTAL);
        return CurrencyUtils.parseCurrency(rawTotal);
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
