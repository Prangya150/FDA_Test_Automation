package com.fda.automation.pages.fda;

import com.fda.automation.base.BasePage;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
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
     */
    public FdaProductDetailsPage searchProduct(String sku) {
        log.info("Searching FDA storefront for SKU: {}", sku);
        waitForVisible(SEARCH_INPUT).sendKeys(sku, Keys.ENTER);
        return new FdaProductDetailsPage(driver);
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
