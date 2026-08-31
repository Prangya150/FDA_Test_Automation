package com.fda.automation.pages;

import com.fda.automation.base.BasePage;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

/**
 * Mirakl middleware seller list (TC_SOB_001 step 56): finds the row for a given seller
 * (matched by email) and clicks its "Vista" button under the "Acción" column. Reused for
 * both the pre-approval pass (step 56) and the final-approval pass (step 88).
 *
 * Confirmed live: this is a MUI X DataGrid, not an HTML {@code <table>} - there is no
 * {@code <tr>} anywhere in the DOM, so the original placeholder's {@code //tr[...]} never
 * matched anything. Each row is a {@code <div role="row" data-id="...">}, each cell a
 * {@code <div role="gridcell" data-field="...">} (the email column's data-field is
 * "sellerEmail"), and - same CSS text-transform gotcha as MiddlewareLoginPage's "Iniciar
 * sesión" button - the visible all-caps "VISTA" is really mixed-case "Vista" in the DOM.
 * The cell's text is visually ellipsis-truncated via CSS but the full email is still the
 * actual DOM text (also mirrored in an aria-label), so a plain contains() match works.
 */
public class MiddlewareSellerListPage extends BasePage {

    public MiddlewareSellerListPage(WebDriver driver) {
        super(driver);
    }

    public MiddlewareSellerDetailPage openSellerByEmail(String email) {
        log.info("Opening seller detail for {}", email);
        By vistaButtonForRow = By.xpath(
                "//div[@role='row'][.//div[@data-field='sellerEmail'][contains(normalize-space(.),\""
                        + email + "\")]]//button[normalize-space()='Vista']");
        click(vistaButtonForRow);
        return new MiddlewareSellerDetailPage(driver);
    }
}
