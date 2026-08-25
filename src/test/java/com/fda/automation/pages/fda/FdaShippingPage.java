package com.fda.automation.pages.fda;

import com.fda.automation.base.BasePage;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

/**
 * FDA checkout - shipping step (a fully custom UI, not Magento's default onepage layout).
 *
 * LOCATOR NOTE: verified against a real, completed checkout run on 2026-08-19. The step heading
 * reads "Dirección de envío" and the continue button carries a stable data-role attribute.
 */
public class FdaShippingPage extends BasePage {

    private static final By SHIPPING_STEP_HEADING = By.xpath("//*[contains(normalize-space(.),'Dirección de envío')]");
    // Confirmed: <button ... class="button action continue primary" data-role="opc-continue">
    private static final By NEXT_BUTTON = By.cssSelector("button[data-role='opc-continue']");
    // Magento's checkout-wide ajax loading overlay; can still cover the continue button for a
    // moment after address validation completes, intercepting the click.
    private static final By LOADING_MASK = By.cssSelector("div.loading-mask[data-role='loader']");

    public FdaShippingPage(WebDriver driver) {
        super(driver);
    }

    public boolean isDisplayed() {
        return waitForVisible(SHIPPING_STEP_HEADING).isDisplayed();
    }

    public FdaPaymentPage clickSiguiente() {
        wait.until(d -> d.findElements(LOADING_MASK).stream().noneMatch(WebElement::isDisplayed));
        wait.until(ExpectedConditions.elementToBeClickable(NEXT_BUTTON)).click();
        return new FdaPaymentPage(driver);
    }
}
