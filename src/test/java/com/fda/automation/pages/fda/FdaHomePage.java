package com.fda.automation.pages.fda;

import com.fda.automation.base.BasePage;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

/**
 * FDA storefront home / logged-in landing page.
 *
 * LOCATOR NOTE: verified against the live DOM on 2026-08-19. The storefront uses a third-party
 * "Empathy/Infinite Search" widget (not Magento's default quick-search box), and the account menu
 * is the same icon-only header button used pre- and post-login.
 */
public class FdaHomePage extends BasePage {

    // Confirmed: <input type="text" name="q" class="empathy-search-input" ... placeholder="¿Qué estás buscando?">
    private static final By SEARCH_INPUT = By.cssSelector("input.empathy-search-input");
    // Same header toggle as FdaLoginPage; post-login it opens the account dropdown instead of the login links.
    private static final By MY_ACCOUNT_LINK = By.cssSelector("button[data-action='customer-menu-toggle']");
    // TODO: verify once authenticated; the pre-login dropdown only exposes "Iniciar sesión"/"Crea una cuenta",
    // so the post-login "Mis pedidos" link markup could not be confirmed without valid credentials.
    private static final By MY_ORDERS_LINK = By.xpath("//a[contains(normalize-space(.),'Mis pedidos')]");
    // Confirmed: <header id="ammenu-header-container" class="ammenu-header-container page-header" ...>
    private static final By ACCOUNT_INDICATOR = By.id("ammenu-header-container");

    public FdaHomePage(WebDriver driver) {
        super(driver);
    }

    public FdaHomePage waitUntilLoaded() {
        waitForVisible(ACCOUNT_INDICATOR);
        waitForVisible(SEARCH_INPUT);
        return this;
    }

    /**
     * Types the SKU into the header search box and submits it with Enter.
     *
     * CONFIRMED live behavior (2026-08-19): for a numeric SKU query, the search widget resolves
     * it via a dedicated SKU-search API and Enter navigates straight to that product's PDP -
     * there is no intermediate results-listing page to click through, matching the test case's
     * own wording ("Press Enter" -> "Wait until PDP is displayed"). A non-numeric/keyword query
     * instead lands on a multi-product results listing page, which is out of scope for this flow.
     *
     * CONFIRMED live on 2026-09-01 (TC_FBS_004): landing on a second product's PDP this way is a
     * client-side route change, not a full page load, and the quantity-stepper widget is not
     * reset by it - a second product searched for from an already-loaded PDP can display (and
     * actually submit to the cart) the *previous* product's selected quantity instead of its own
     * real default of 1. A hard reload after landing forces the PDP - including that widget's
     * underlying add-to-cart state - to initialize from a clean server-rendered page, matching how
     * a first, fresh PDP visit already behaves correctly.
     *
     * CONFIRMED live on 2026-09-01 (TC_FBS_004, second occurrence): the search box was never
     * cleared before typing the next SKU, so a second search's query concatenated onto whatever
     * was still in the field - matching no product, and silently leaving the browser on the
     * previous product's PDP instead of navigating. Clearing the field first prevents that
     * concatenation regardless of what was left in it.
     *
     * CONFIRMED live on 2026-09-01 (TC_FBS_004, third occurrence): even with the field cleared,
     * the second product still ended up merged into the first product's cart line. Root cause: the
     * PDP's {@code <h1>} title element is the *same DOM node* the client-side router reuses across
     * products - it is already visible before the new product's data has replaced the old text, so
     * a plain {@code isDisplayed()} check (waiting for that node to be visible) can return true
     * instantly, before the route change has actually happened. The refresh that follows then
     * reloads whatever URL is still in the address bar, which can still be the *previous* product's
     * if the SPA router hadn't pushed the new URL yet - silently leaving both products' add-to-cart
     * actions operating on the same, first product. Waiting for the URL itself to change is a
     * signal that doesn't depend on when any particular element's text gets swapped in.
     */
    public FdaProductDetailsPage searchProduct(String sku) {
        log.info("Searching FDA storefront for SKU: {}", sku);
        String urlBeforeSearch = driver.getCurrentUrl();
        WebElement searchInput = waitForVisible(SEARCH_INPUT);
        searchInput.clear();
        searchInput.sendKeys(sku, Keys.ENTER);
        wait.until(d -> !d.getCurrentUrl().equals(urlBeforeSearch));
        FdaProductDetailsPage pdp = new FdaProductDetailsPage(driver);
        pdp.isDisplayed();
        driver.navigate().refresh();
        pdp.isDisplayed();
        return pdp;
    }

    public FdaHomePage openMyAccountMenu() {
        click(MY_ACCOUNT_LINK);
        return this;
    }

    public FdaOrderHistoryPage openMyOrders() {
        wait.until(ExpectedConditions.elementToBeClickable(MY_ORDERS_LINK)).click();
        return new FdaOrderHistoryPage(driver);
    }
}
