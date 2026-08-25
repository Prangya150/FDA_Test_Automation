package com.fda.automation.pages.fda;

import com.fda.automation.base.BasePage;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

/**
 * Magento 2 "My Orders" (sales/order/history) page.
 */
public class FdaOrderHistoryPage extends BasePage {

    // Magento 2 Luma default "My Orders" table id
    private static final By ORDERS_TABLE = By.id("my-orders-table");

    public FdaOrderHistoryPage(WebDriver driver) {
        super(driver);
    }

    public boolean isDisplayed() {
        return waitForVisible(ORDERS_TABLE).isDisplayed();
    }

    private By rowForOrderId(String orderId) {
        return By.xpath("//table[@id='my-orders-table']//tr[td[contains(normalize-space(.), '" + orderId + "')]]");
    }

    public boolean isOrderPresent(String orderId) {
        return !driver.findElements(rowForOrderId(orderId)).isEmpty();
    }

    /** Reads the status column ("Creada", etc.) for the given order id row. */
    public String getOrderStatus(String orderId) {
        By statusCell = By.xpath(
                "//table[@id='my-orders-table']//tr[td[contains(normalize-space(.), '" + orderId + "')]]//td[contains(@class,'status')]");
        return getText(statusCell);
    }
}
