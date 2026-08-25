package com.fda.automation.pages.mirakl;

import com.fda.automation.base.BasePage;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Mirakl "Add tracking information" popup.
 *
 * TODO: locators are based on the visible labels named in the test case; verify against the live
 * DOM once accessible.
 */
public class MiraklTrackingPage extends BasePage {

    private static final By POPUP = By.xpath("//div[contains(@class,'modal') or @role='dialog'][.//*[contains(.,'tracking')]]");
    // CONFIRMED live on 2026-08-20: same custom combobox pattern as the Document type dropdown -
    // not a native <select>, a clickable box showing "Select a carrier" with role='option' items.
    private static final By CARRIER_DROPDOWN_TRIGGER = By.xpath(
            "//div[contains(@class,'modal') or @role='dialog']"
            + "//*[contains(normalize-space(.),'Select a carrier') and not(.//*[contains(normalize-space(.),'Select a carrier')])]");
    private static final By CARRIER_OPTIONS = By.xpath("//*[@role='option']");
    // CONFIRMED live on 2026-08-20: guessed name/id attributes didn't match - this is the only
    // text input in the popup (the carrier field is a custom combobox, not an <input>).
    private static final By TRACKING_NUMBER_INPUT =
            By.xpath("//div[contains(@class,'modal') or @role='dialog']//input[@type='text' or not(@type)]");
    private static final By CLOSE_BUTTON = By.xpath("//div[contains(@class,'modal') or @role='dialog']//button[contains(normalize-space(.),'Close')]");
    private static final By ADD_BUTTON = By.xpath("//div[contains(@class,'modal') or @role='dialog']//button[contains(normalize-space(.),'Add')]");

    // CONFIRMED live on 2026-08-20: after saving, the order page shows a collapsed "View tracking
    // information" link, not the carrier/tracking number as plain text - must expand it first.
    private static final By VIEW_TRACKING_INFO_LINK = By.xpath(
            "//*[contains(normalize-space(.),'View tracking information') and not(.//*[contains(normalize-space(.),'View tracking information')])]");
    private static final By PAGE_BODY = By.tagName("body");

    public MiraklTrackingPage(WebDriver driver) {
        super(driver);
    }

    public boolean isPopupDisplayed() {
        return waitForVisible(POPUP).isDisplayed();
    }

    public List<String> getAvailableCarriers() {
        click(CARRIER_DROPDOWN_TRIGGER);
        return driver.findElements(CARRIER_OPTIONS).stream()
                .map(o -> o.getText().trim()).filter(t -> !t.isEmpty()).collect(Collectors.toList());
    }

    /** Opens the dropdown first only if it isn't already open (e.g. left open by {@link #getAvailableCarriers()}). */
    public void selectCarrier(String carrier) {
        By option = By.xpath("//*[@role='option'][normalize-space(.)='" + carrier + "']");
        if (driver.findElements(option).isEmpty()) {
            click(CARRIER_DROPDOWN_TRIGGER);
        }
        wait.until(ExpectedConditions.elementToBeClickable(option)).click();
    }

    public void enterTrackingNumber(String trackingNumber) {
        type(TRACKING_NUMBER_INPUT, trackingNumber);
    }

    public void clickAdd() {
        log.info("Saving DHL tracking information");
        click(ADD_BUTTON);
        wait.until(ExpectedConditions.elementToBeClickable(VIEW_TRACKING_INFO_LINK)).click();
    }

    /**
     * CONFIRMED live on 2026-08-20: the expanded panel fetches carrier/tracking details
     * asynchronously (e.g. a "Tracking URL", "Shipment location" section render in afterward) -
     * an immediate check right after expanding can race that fetch, so poll briefly instead.
     */
    public boolean isCarrierDisplayed(String carrier) {
        try {
            return wait.until(d -> d.findElement(PAGE_BODY).getText().contains(carrier));
        } catch (org.openqa.selenium.TimeoutException e) {
            return false;
        }
    }

    public boolean isTrackingNumberDisplayed(String trackingNumber) {
        try {
            return wait.until(d -> d.findElement(PAGE_BODY).getText().contains(trackingNumber));
        } catch (org.openqa.selenium.TimeoutException e) {
            return false;
        }
    }
}
