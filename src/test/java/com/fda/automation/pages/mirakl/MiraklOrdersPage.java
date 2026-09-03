package com.fda.automation.pages.mirakl;

import com.fda.automation.base.BasePage;
import com.fda.automation.utils.PollingUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.time.Duration;
import java.util.ArrayList;
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
    // CONFIRMED live on 2026-09-01 (TC_FBS_005): when a single FDA checkout contains items from
    // two different 3P sellers, Mirakl splits it into one suborder per seller, each with its own
    // reference - the FDA order number plus an incrementing suffix (WEB-A for the first seller,
    // WEB-B for the second, ...) rather than one combined order.
    private static final String MIRAKL_ORDER_ID_BASE_SUFFIX = "WEB";
    private static final java.util.regex.Pattern SUBORDER_REFERENCE_SUFFIX = java.util.regex.Pattern.compile("WEB-[A-Z]");

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
     *
     * CONFIRMED live on 2026-09-01 (TC_FBS_006): switching back to this tab after the several
     * minutes the FDA checkout takes on the other tab can find the Auth0 session has expired in
     * the background, silently dropping the page back to the login form - which has no "Orders"
     * menu at all, so this times out no matter how long it waits. Logs the current URL and whether
     * a login form is showing on timeout, so that is distinguishable from a genuinely slow-loading
     * dashboard instead of just another unexplained timeout.
     */
    public MiraklOrdersPage openOrdersMenu(Duration timeout) {
        try {
            new org.openqa.selenium.support.ui.WebDriverWait(driver, timeout)
                    .until(ExpectedConditions.elementToBeClickable(ORDERS_MENU)).click();
        } catch (org.openqa.selenium.TimeoutException e) {
            boolean loginFormPresent = !driver.findElements(By.cssSelector("input[name='username'], input#username")).isEmpty();
            log.error("'Orders' menu not clickable after {}s; current URL: {}, login form present: {}",
                    timeout.getSeconds(), driver.getCurrentUrl(), loginFormPresent);
            throw e;
        }
        return this;
    }

    public MiraklOrdersPage openAllOrders() {
        wait.until(ExpectedConditions.elementToBeClickable(ALL_ORDERS_MENU_ITEM)).click();
        waitForVisible(ORDERS_GRID);
        return this;
    }

    /** Searches using the exact, ready-to-match reference (no suffix appended). */
    public void searchByOrderReference(String reference) {
        WebElement searchInput = waitForVisible(ORDER_SEARCH_INPUT);
        searchInput.clear();
        searchInput.sendKeys(reference, Keys.ENTER);
    }

    public void searchByOrderId(String orderId) {
        searchByOrderReference(orderId + MIRAKL_ORDER_ID_SUFFIX);
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
     * Searches for the given reference exactly once, then polls by reloading the page on each
     * interval (per team direction: enter the Order ID a single time, then just refresh while
     * waiting for FDA -> Mirakl sync) rather than re-typing into the search field on every tick.
     *
     * BUG FOUND (2026-08-19): checking immediately after {@code driver.navigate().refresh()} is a
     * race - this is a heavy SPA, and the grid hadn't finished re-rendering yet at the moment of
     * the check, producing a false "not found" on an order that was actually already there (a
     * failure screenshot taken moments later showed the "missing" order sitting in the list).
     * Waiting for the grid to reappear after each refresh before checking fixes this.
     */
    public void waitForOrderReferenceToAppear(String reference, Duration timeout, Duration pollInterval) {
        searchByOrderReference(reference);
        PollingUtils.pollUntil(
                () -> {
                    driver.navigate().refresh();
                    waitForVisible(ORDERS_GRID);
                    return isOrderDisplayed(reference);
                },
                found -> found,
                timeout,
                pollInterval,
                "Mirakl reference " + reference + " never appeared in Mirakl All Orders"
        );
    }

    public void waitForOrderToAppear(String orderId, Duration timeout, Duration pollInterval) {
        waitForOrderReferenceToAppear(orderId + MIRAKL_ORDER_ID_SUFFIX, timeout, pollInterval);
    }

    /**
     * CONFIRMED live on 2026-09-01 (TC_FBS_005): searching the bare "&lt;orderId&gt;WEB" (i.e.
     * without a trailing -A/-B/-C suffix) returns every suborder for that FDA order in a single
     * search - e.g. searching "4000299068WEB" surfaces both "4000299068WEB-A" and
     * "4000299068WEB-B" together, rather than needing an exact, full-reference search per suffix.
     * Searching once and polling the grid for {@code expectedCount} distinct references is also
     * far faster than the previous approach of searching each suffix in turn, each spending its
     * own full time budget waiting for a single row to sync.
     */
    public List<String> findSuborderReferences(String orderId, int expectedCount, Duration timeout, Duration pollInterval) {
        String searchTerm = orderId + MIRAKL_ORDER_ID_BASE_SUFFIX;
        searchByOrderReference(searchTerm);

        java.util.regex.Pattern referencePattern = java.util.regex.Pattern.compile(
                java.util.regex.Pattern.quote(orderId) + SUBORDER_REFERENCE_SUFFIX.pattern());

        List<String> found;
        try {
            found = PollingUtils.pollUntil(
                    () -> {
                        driver.navigate().refresh();
                        waitForVisible(ORDERS_GRID);
                        return extractMatchingReferences(referencePattern);
                    },
                    refs -> refs.size() >= expectedCount,
                    timeout,
                    pollInterval,
                    "Only found suborders matching '" + searchTerm + "-*' in Mirakl (expected " + expectedCount + ")");
        } catch (IllegalStateException e) {
            throw new IllegalStateException("Only found suborders matching '" + searchTerm
                    + "-*' in Mirakl (expected " + expectedCount + ")", e);
        }

        // The grid's row order reflects Mirakl's own sync/display order, not the WEB-A/WEB-B
        // suffix order (a live run returned WEB-B before WEB-A) - sort so callers can rely on
        // processing suborders in suffix order (WEB-A fully, then WEB-B, ...).
        found.sort(java.util.Comparator.naturalOrder());

        log.info("Found {} Mirakl suborder reference(s) for FDA order {}: {}", found.size(), orderId, found);
        return found;
    }

    /** Scans every visible grid row's text for occurrences of {@code referencePattern}. */
    private List<String> extractMatchingReferences(java.util.regex.Pattern referencePattern) {
        java.util.Set<String> references = new java.util.LinkedHashSet<>();
        for (WebElement row : driver.findElements(By.xpath("//tr"))) {
            java.util.regex.Matcher matcher = referencePattern.matcher(row.getText());
            while (matcher.find()) {
                references.add(matcher.group());
            }
        }
        return new ArrayList<>(references);
    }

    public MiraklOrderDetailsPage openOrder(String orderId) {
        click(By.xpath("//tr[contains(., '" + orderId + "')]//a[contains(., '" + orderId + "')]"));
        return new MiraklOrderDetailsPage(driver);
    }
}
