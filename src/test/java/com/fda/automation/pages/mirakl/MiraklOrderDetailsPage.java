package com.fda.automation.pages.mirakl;

import com.fda.automation.base.BasePage;
import com.fda.automation.utils.CurrencyUtils;
import com.fda.automation.utils.PollingUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Mirakl order details page: status, order total, "More actions" menu (Documents, Custom field,
 * Accept, Mark as Shipped, ...).
 *
 * TODO: locators below are based on the visible labels specified in the test case; verify the
 * real markup once the Mirakl operator front office DOM can be inspected directly.
 */
public class MiraklOrderDetailsPage extends BasePage {

    // CONFIRMED live on 2026-08-20: the order details page shows status as a shipment-level
    // heading, e.g. "Shipment 1: awaiting shipment" - not a class="status"/"label" element as
    // originally guessed. Picks the innermost element containing that text (there is no stable
    // class to hook), then getStatus() strips the "Shipment N:" prefix.
    private static final By ORDER_STATUS = By.xpath(
            "//*[starts-with(normalize-space(.),'Shipment ') and not(.//*[starts-with(normalize-space(.),'Shipment ')])]");
    private static final By ORDER_TOTAL = By.xpath("//*[contains(normalize-space(.),'Total')]/following::*[contains(text(),'$') or contains(text(),'MXN')][1]");
    private static final By ACCEPT_BUTTON = By.xpath("//button[contains(normalize-space(.),'Accept')]");
    // CONFIRMED live on 2026-08-20: the real button text is "Mark as shipped" (lowercase "s"),
    // not "Mark as Shipped" as originally guessed.
    private static final By MARK_AS_SHIPPED_BUTTON = By.xpath(
            "//button[contains(translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'mark as shipped')]");
    private static final By CONFIRM_SHIP_POPUP_BUTTON = By.xpath("//div[contains(@class,'modal') or @role='dialog']//button[contains(normalize-space(.),'Confirm') or contains(normalize-space(.),'Ship')]");
    // CONFIRMED live on 2026-08-20: setting "Entregado" to "yes" alone transitions the status only
    // asynchronously and unreliably (didn't happen within 30 min in one real run, took ~20 min in
    // another) - a separate "Mark as received" button (same direct-action pattern as "Mark as
    // shipped") makes the transition immediate and deterministic.
    private static final By MARK_AS_RECEIVED_BUTTON = By.xpath(
            "//button[contains(translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'mark as received')]");

    private static final By MORE_ACTIONS_BUTTON = By.xpath("//button[contains(normalize-space(.),'More actions')]");
    private static final By MORE_ACTIONS_MENU = By.xpath("//*[@role='menu' or contains(@class,'dropdown-menu')]");
    private static final By DOCUMENTS_MENU_ITEM = menuItem("Documents");
    private static final By CUSTOM_FIELD_MENU_ITEM = menuItem("Custom field");

    // CONFIRMED live on 2026-08-20: some Mirakl themes may still show a confirmation dialog after
    // selecting a custom field option - guarded as optional in confirmEditAdditionalInformation().
    private static final By EDIT_ADDITIONAL_INFO_CONFIRM_BUTTON =
            By.xpath("//div[contains(@class,'modal') or @role='dialog']//button[contains(normalize-space(.),'Confirm')]");
    // CONFIRMED live on 2026-08-20: the "Edit additional information" dialog that opens after
    // selecting a custom field option has its own custom combobox for the field's value, shown as
    // "Nothing selected" until a value is chosen - same custom-combobox pattern used elsewhere.
    private static final By EDIT_ADDITIONAL_INFO_VALUE_DROPDOWN_TRIGGER = By.xpath(
            "//div[contains(@class,'modal') or @role='dialog']"
            + "//*[contains(normalize-space(.),'Nothing selected') and not(.//*[contains(normalize-space(.),'Nothing selected')])]");

    public MiraklOrderDetailsPage(WebDriver driver) {
        super(driver);
    }

    /**
     * CONFIRMED live on 2026-08-20: a bare contains() match returns the outer wrapping element
     * (document order puts ancestors before descendants), which has no click handler of its own -
     * clicking it left the dropdown open instead of navigating. Restrict to the innermost leaf
     * element containing the label text, same fix pattern as the order-status locator.
     */
    private static By menuItem(String label) {
        return By.xpath("//*[@role='menu' or contains(@class,'dropdown-menu')]"
                + "//*[contains(normalize-space(.),'" + label + "') and not(.//*[contains(normalize-space(.),'" + label + "')])]");
    }

    /** Strips the "Shipment N:" prefix Mirakl renders ahead of the actual status, e.g. "Shipment 1: awaiting shipment". */
    public String getStatus() {
        String raw = getText(ORDER_STATUS);
        int colonIndex = raw.indexOf(':');
        return colonIndex >= 0 ? raw.substring(colonIndex + 1).trim() : raw;
    }

    public void waitForStatus(String expectedStatus, Duration timeout) {
        PollingUtils.pollUntil(this::getStatus, status -> status.trim().equalsIgnoreCase(expectedStatus),
                timeout, Duration.ofSeconds(5),
                "Mirakl order status never reached '" + expectedStatus + "'");
    }

    public BigDecimal getOrderTotal() {
        return CurrencyUtils.parseCurrency(getText(ORDER_TOTAL));
    }

    public void acceptOrder() {
        log.info("Accepting Mirakl order");
        click(ACCEPT_BUTTON);
    }

    public MiraklOrderDetailsPage openMoreActions() {
        click(MORE_ACTIONS_BUTTON);
        waitForVisible(MORE_ACTIONS_MENU);
        return this;
    }

    private static final List<String> EXPECTED_MORE_ACTIONS_OPTIONS =
            List.of("View", "Documents", "History", "Edit", "Order reference", "Custom field");

    /**
     * Returns the subset of the expected More actions options that are NOT currently visible in
     * the menu. Case-insensitive: CONFIRMED live on 2026-08-20 that the "View" and "Edit" group
     * headers render as "VIEW" / "EDIT" (CSS text-transform), which a case-sensitive contains()
     * never matches.
     */
    public List<String> getMissingMoreActionsOptions() {
        List<String> visibleTexts = driver.findElements(By.xpath("//*[@role='menu' or contains(@class,'dropdown-menu')]//*"))
                .stream().map(el -> el.getText().trim()).filter(t -> !t.isEmpty()).collect(Collectors.toList());
        return EXPECTED_MORE_ACTIONS_OPTIONS.stream()
                .filter(expected -> visibleTexts.stream().noneMatch(actual -> actual.toLowerCase().contains(expected.toLowerCase())))
                .collect(Collectors.toList());
    }

    public MiraklDocumentsPage openDocuments() {
        click(DOCUMENTS_MENU_ITEM);
        return new MiraklDocumentsPage(driver);
    }

    public MiraklTrackingPage openAddTrackingInformation() {
        click(By.xpath("//button[contains(normalize-space(.),'Add tracking information')]"));
        return new MiraklTrackingPage(driver);
    }

    public void markAsShipped() {
        log.info("Marking Mirakl order as Shipped");
        click(MARK_AS_SHIPPED_BUTTON);
        // Some Mirakl operator themes show a confirmation dialog before applying the transition.
        List<org.openqa.selenium.WebElement> confirmPopup = driver.findElements(CONFIRM_SHIP_POPUP_BUTTON);
        if (!confirmPopup.isEmpty()) {
            confirmPopup.get(0).click();
        }
    }

    /**
     * CONFIRMED live on 2026-08-20: "Custom field" opens a flyout submenu of More actions (its
     * options - Entregado, 3PL delivery, etc. - are custom field names), not a modal dialog as
     * originally guessed.
     */
    public void openCustomField() {
        click(CUSTOM_FIELD_MENU_ITEM);
        waitForVisible(menuItem("Entregado"));
    }

    public static final Set<String> CUSTOM_FIELD_OPTIONS =
            Set.of("Entregado", "3PL Tracking current Status", "Ready for Pick Up");

    /** Same flyout-submenu leaf-node click pattern as {@link #menuItem}, not a modal option. */
    public void selectCustomFieldOption(String option) {
        click(menuItem(option));
    }

    /**
     * CONFIRMED live on 2026-08-20: the "Entregado" field's value is a simple Yes/No custom
     * combobox (not a native select) defaulting to "Nothing selected". Selecting "Yes" and
     * confirming is what drives the Shipped -> Received transition, applied asynchronously by
     * Mirakl (observed taking up to ~20 minutes in a real run) rather than immediately.
     */
    public void selectEditAdditionalInformationValue(String value) {
        click(EDIT_ADDITIONAL_INFO_VALUE_DROPDOWN_TRIGGER);
        click(By.xpath("//*[@role='option'][normalize-space(.)='" + value + "']"));
    }

    public void confirmEditAdditionalInformation() {
        click(EDIT_ADDITIONAL_INFO_CONFIRM_BUTTON);
    }

    public void markAsReceived() {
        log.info("Marking Mirakl order as Received");
        click(MARK_AS_RECEIVED_BUTTON);
    }
}
