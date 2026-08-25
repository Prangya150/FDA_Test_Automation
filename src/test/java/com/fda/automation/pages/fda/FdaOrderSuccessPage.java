package com.fda.automation.pages.fda;

import com.fda.automation.base.BasePage;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

/**
 * FDA checkout success page.
 *
 * LOCATOR NOTE: verified against a real, completed checkout run on 2026-08-19
 * (checkout/onepage/success/), which rendered:
 *   &lt;p&gt;Número de pedido &lt;a href="...sales/order/view/order_id/507934/" class="order-number"&gt;4000288448&lt;/a&gt;&lt;/p&gt;
 */
public class FdaOrderSuccessPage extends BasePage {

    private static final By SUCCESS_CONTAINER = By.cssSelector(".checkout-success");
    private static final By ORDER_NUMBER_LINK = By.cssSelector(".checkout-success a.order-number");

    public FdaOrderSuccessPage(WebDriver driver) {
        super(driver);
    }

    public boolean isDisplayed() {
        return waitForVisible(SUCCESS_CONTAINER).isDisplayed();
    }

    public String captureOrderId() {
        String orderId = getText(ORDER_NUMBER_LINK);
        log.info("Captured FDA order id: {}", orderId);
        return orderId;
    }
}
