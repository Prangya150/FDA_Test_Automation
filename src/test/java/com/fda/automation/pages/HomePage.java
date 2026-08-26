package com.fda.automation.pages;

import com.fda.automation.base.BasePage;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

/**
 * Locators confirmed live on mcstaging.fahorro.com via Playwright:
 * - ACCOUNT_MENU_TOGGLE is the header button with data-action="customer-menu-toggle"
 *   (aria-label="Mi cuenta"); it opens a dialog listing "Crea una cuenta" and
 *   "Vende con nosotros".
 * - "Vende con nosotros" has no id/class, only text, and links to /marketplace-inicio/.
 */
public class HomePage extends BasePage {

    private static final By ACCOUNT_MENU_TOGGLE = By.cssSelector("button[data-action='customer-menu-toggle']");
    private static final By VENDE_CON_NOSOTROS_LINK = By.xpath("//a[contains(normalize-space(.),'Vende con nosotros')]");

    public HomePage(WebDriver driver) {
        super(driver);
    }

    public HomePage open() {
        navigateTo("/");
        return this;
    }

    public void openAccountMenu() {
        click(ACCOUNT_MENU_TOGGLE);
    }

    public MarketplaceLandingPage clickVendeConNosotros() {
        click(VENDE_CON_NOSOTROS_LINK);
        return new MarketplaceLandingPage(driver);
    }

    public MarketplaceLandingPage goToSellerLanding() {
        openAccountMenu();
        return clickVendeConNosotros();
    }
}
