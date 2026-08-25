package com.fda.automation.pages.mirakl;

import com.fda.automation.base.BasePage;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;

/**
 * Mirakl "Accounting Documents" section (opened via More actions > Documents) and its
 * "Upload an order document" popup.
 *
 * TODO: locators are based on the visible labels/columns named in the test case; verify against
 * the live DOM once accessible.
 */
public class MiraklDocumentsPage extends BasePage {

    // CONFIRMED live on 2026-08-20: the real heading text is "Accounting documents" (lowercase
    // "d"), not "Accounting Documents" as originally guessed - match case-insensitively.
    private static final By ACCOUNTING_DOCUMENTS_SECTION = By.xpath(
            "//*[contains(translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'accounting documents')]");
    private static final By ADD_BUTTON = By.xpath("//button[contains(normalize-space(.),'Add')]");

    private static final By UPLOAD_POPUP = By.xpath("//div[contains(@class,'modal') or @role='dialog'][.//*[contains(.,'Upload an order document')]]");
    // CONFIRMED live on 2026-08-20: this is a custom combobox (a clickable box showing
    // "-- Select --"), not a native <select> - no <select> element exists in the dialog at all.
    private static final By DOCUMENT_TYPE_DROPDOWN_TRIGGER = By.xpath(
            "//div[contains(@class,'modal') or @role='dialog']"
            + "//*[contains(normalize-space(.),'-- Select --') and not(.//*[contains(normalize-space(.),'-- Select --')])]");
    private static final By FILE_INPUT = By.cssSelector("input[type='file']");
    private static final By CANCEL_BUTTON = By.xpath("//div[contains(@class,'modal') or @role='dialog']//button[contains(normalize-space(.),'Cancel')]");
    private static final By CONFIRM_BUTTON = By.xpath("//div[contains(@class,'modal') or @role='dialog']//button[contains(normalize-space(.),'Confirm')]");
    private static final By UPLOAD_SUCCESS_MESSAGE = By.xpath("//*[contains(normalize-space(.),'The document has been uploaded')]");
    // CONFIRMED live on 2026-08-20: Documents opens as its own separate page (not an inline
    // section of the order details page) - a "Back to order no. ..." link returns to it.
    private static final By BACK_TO_ORDER_LINK = By.xpath("//a[contains(normalize-space(.),'Back to order')]");

    public MiraklDocumentsPage(WebDriver driver) {
        super(driver);
    }

    public boolean isAccountingDocumentsSectionDisplayed() {
        return waitForVisible(ACCOUNTING_DOCUMENTS_SECTION).isDisplayed();
    }

    public boolean isAddButtonDisplayed() {
        return waitForVisible(ADD_BUTTON).isDisplayed();
    }

    public void clickAdd() {
        click(ADD_BUTTON);
        waitForVisible(UPLOAD_POPUP);
    }

    public boolean isUploadPopupDisplayed() {
        return waitForVisible(UPLOAD_POPUP).isDisplayed();
    }

    /**
     * CONFIRMED live on 2026-08-20: once a document has been uploaded, its row also shows the
     * document type as plain text (e.g. "Invoice"), which a page-wide text match can pick up
     * instead of the dropdown option - scope to the ARIA listbox option role instead.
     */
    public void selectDocumentType(String documentType) {
        click(DOCUMENT_TYPE_DROPDOWN_TRIGGER);
        click(By.xpath("//*[@role='option'][normalize-space(.)='" + documentType + "']"));
    }

    /** Selenium uploads files by sending the absolute path directly to the (usually hidden) file input. */
    public void uploadFile(String absoluteFilePath) {
        driver.findElement(FILE_INPUT).sendKeys(absoluteFilePath);
    }

    public void clickConfirm() {
        log.info("Confirming invoice upload");
        click(CONFIRM_BUTTON);
    }

    public String waitForUploadConfirmation() {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(UPLOAD_SUCCESS_MESSAGE)).getText();
    }

    /** Documents is a standalone page, not an inline section - navigate back to resume order-level actions. */
    public MiraklOrderDetailsPage backToOrder() {
        click(BACK_TO_ORDER_LINK);
        return new MiraklOrderDetailsPage(driver);
    }
}
