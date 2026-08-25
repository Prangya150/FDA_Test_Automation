package com.fda.automation.pages.mirakl;

import com.fda.automation.base.BasePage;
import com.fda.automation.utils.PollingUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.time.Duration;
import java.util.List;

/**
 * Mirakl "Orders" > "All orders" listing page (the modern Mirakl OP3 left-nav UI).
 *
 * LOCATOR NOTE: verified against a real, logged-in Mirakl session on 2026-08-19. The sidebar
 * renders "All orders" with a lowercase "o" - a case-sensitive XPath contains() on "All Orders"
 * (as worded in the test case) does not match. A first fix used a case-insensitive contains()
 * match, but that also matched unrelated dashboard summary text like "Orders awaiting shipment"
 * and "Late orders" (both legitimately contain the substring "orders"), clicking the wrong one -
 * so these use an exact (not substring) case-insensitive match on the nav item's own text instead.
 *
 * CRITICAL FINDING (2026-08-19): several orders appeared to "never sync" from FDA into Mirakl,
 * even after 15 minutes, for two combined reasons - neither of which was an actual sync problem:
 *  1. The "Order ID" search filter does an exact match against Mirakl's own order id, which is
 *     the FDA order number plus a "WEB-A" suffix (e.g. FDA order 4000288487 is searchable in
 *     Mirakl as "4000288487WEB-A"). Searching the bare FDA order number always returned "No
 *     results found", confirmed by browsing the unfiltered order list and finding the "missing"
 *     orders sitting right there the whole time.
 *  2. Even after fixing the search, {@link #waitForOrderToAppear} still reported false negatives
 *     because it checked immediately after a page refresh, before this SPA's grid had finished
 *     re-rendering - see the note on that method.
 */
public class MiraklOrdersPage extends BasePage {

    private static final By ORDERS_MENU = caseInsensitiveExactTextXpath("Orders");
    private static final By ALL_ORDERS_MENU_ITEM = caseInsensitiveExactTextXpath("All orders");
    private static final By ORDER_SEARCH_INPUT = By.cssSelector("input[type='search'], input[placeholder*='Search']");
    private static final By ORDERS_GRID = By.cssSelector("table, [role='table']");
    // Confirmed suffix Mirakl appends to the FDA order number for its own "Order ID" field/search.
    private static final String MIRAKL_ORDER_ID_SUFFIX = "WEB-A";

    private static By caseInsensitiveExactTextXpath(String text) {
        String upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String lower = "abcdefghijklmnopqrstuvwxyz";
        return By.xpath("//*[self::a or self::span or self::button]"
                + "[translate(normalize-space(.), '" + upper + "', '" + lower + "')='" + text.toLowerCase() + "']");
    }

    public MiraklOrdersPage(WebDriver driver) {
        super(driver);
    }

    /**
     * The Orders menu can take a while to become interactive right after login while the
     * dashboard finishes loading; poll for clickability instead of a blind fixed sleep.
     */
    public MiraklOrdersPage openOrdersMenu(Duration timeout) {
        new org.openqa.selenium.support.ui.WebDriverWait(driver, timeout)
                .until(ExpectedConditions.elementToBeClickable(ORDERS_MENU)).click();
        return this;
    }

    public MiraklOrdersPage openAllOrders() {
        wait.until(ExpectedConditions.elementToBeClickable(ALL_ORDERS_MENU_ITEM)).click();
        waitForVisible(ORDERS_GRID);
        return this;
    }

    public void searchByOrderId(String orderId) {
        WebElement searchInput = waitForVisible(ORDER_SEARCH_INPUT);
        searchInput.clear();
        searchInput.sendKeys(orderId + MIRAKL_ORDER_ID_SUFFIX, Keys.ENTER);
    }

    private By rowForOrderId(String orderId) {
        return By.xpath("//tr[contains(., '" + orderId + "')]");
    }

    public boolean isOrderDisplayed(String orderId) {
        return !driver.findElements(rowForOrderId(orderId)).isEmpty();
    }

    public String getOrderStatus(String orderId) {
        By statusCell = By.xpath("//tr[contains(., '" + orderId + "')]//td[contains(@class,'status') or contains(., 'Pending') or contains(., 'Awaiting') or contains(., 'Shipped') or contains(., 'Received')]");
        return getText(statusCell);
    }

    /**
     * Searches for the order exactly once, then polls by reloading the page on each interval
     * (per team direction: enter the Order ID a single time, then just refresh while waiting for
     * FDA -> Mirakl sync) rather than re-typing into the search field on every tick.
     *
     * BUG FOUND (2026-08-19): checking immediately after {@code driver.navigate().refresh()} is a
     * race - this is a heavy SPA, and the grid hadn't finished re-rendering yet at the moment of
     * the check, producing a false "not found" on an order that was actually already there (a
     * failure screenshot taken moments later showed the "missing" order sitting in the list).
     * Waiting for the grid to reappear after each refresh before checking fixes this.
     */
    public void waitForOrderToAppear(String orderId, Duration timeout, Duration pollInterval) {
        searchByOrderId(orderId);
        PollingUtils.pollUntil(
                () -> {
                    driver.navigate().refresh();
                    waitForVisible(ORDERS_GRID);
                    return isOrderDisplayed(orderId);
                },
                found -> found,
                timeout,
                pollInterval,
                "FDA order " + orderId + " never appeared in Mirakl All Orders"
        );
    }

    public MiraklOrderDetailsPage openOrder(String orderId) {
        click(By.xpath("//tr[contains(., '" + orderId + "')]//a[contains(., '" + orderId + "')]"));
        return new MiraklOrderDetailsPage(driver);
    }
}
