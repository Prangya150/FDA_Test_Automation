package com.fda.automation.pages;

import com.fda.automation.base.BasePage;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mirakl marketplace shop search (TC_SOB_001 steps 107-109): "Tiendas" nav -> "Todas las
 * cuentas tienda" -> search by seller/trade name -> assert the row appears -> open it.
 *
 * Confirmed live: "Tiendas" is a real {@code <button>} that expands a dropdown containing
 * "Todas las cuentas tienda" as a {@code role="menuitem"} item (not a plain {@code <a>}/
 * {@code <button>}, so the original placeholder's tag-restricted alternatives never
 * matched it). The results grid IS a real HTML table - {@code <tr id="{rowId}">} rows
 * containing {@code <td id="{rowId}_name">} cells (no ARIA {@code role="row"}/{@code
 * role="cell"} anywhere in it, despite looking like a role-based grid at a glance; an
 * earlier version of this locator assumed ARIA roles and matched zero rows no matter how
 * long it waited - confirmed by comparing a failure screenshot, which showed the row
 * correctly rendered, against the page source captured at the same instant, which had no
 * {@code role="row"} element in the whole document). The shop name in each row is a real
 * {@code <a>} link to {@code /mmp/operator/shop/{id}}.
 */
public class MiraklShopSearchPage extends BasePage {

    private static final By TIENDAS_NAV = By.xpath("//button[normalize-space()='Tiendas']");
    private static final By TODAS_LAS_CUENTAS_TIENDA =
            By.xpath("//*[@role='menuitem'][contains(normalize-space(.),'Todas las cuentas tienda')]");
    private static final By BUSCAR_INPUT =
            By.xpath("//input[contains(@placeholder,'Buscar') or contains(@aria-label,'Buscar')]");

    public MiraklShopSearchPage(WebDriver driver) {
        super(driver);
    }

    public void openAllShopAccounts() {
        click(TIENDAS_NAV);
        click(TODAS_LAS_CUENTAS_TIENDA);
    }

    public void searchFor(String sellerNameOrTradeName) {
        type(BUSCAR_INPUT, sellerNameOrTradeName);
    }

    /**
     * Checking instantly right after typing (a bare isDisplayed()) races Mirakl's grid,
     * which re-filters asynchronously after the search box changes. A 3-second poll still
     * wasn't enough - observed live, repeatedly: a screenshot taken right as a 3-second
     * check gave up showed the row correctly present, meaning the grid's own filter+render
     * latency can exceed 3 seconds. 8 seconds gives it real headroom.
     */
    public boolean isShopPresentInResults(String sellerNameOrTradeName) {
        return isVisibleWithin(resultRow(sellerNameOrTradeName), Duration.ofSeconds(8));
    }

    /**
     * Polls by re-typing the search term instead of checking once - a shop approved
     * moments earlier (this step runs right after the final-approval step) is not
     * necessarily indexed for search yet (observed live: a shop confirmed via direct URL
     * to genuinely exist did not show up in this search within the default 10s wait). No
     * extra sleep between attempts - isShopPresentInResults's own 8-second wait already
     * paces each retype, and adding a further fixed sleep on top of that only widens the
     * window in which a result that's already ready sits unnoticed.
     */
    public boolean waitForShopInResults(String sellerNameOrTradeName, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (true) {
            searchFor(sellerNameOrTradeName);
            if (isShopPresentInResults(sellerNameOrTradeName)) {
                return true;
            }
            if (System.currentTimeMillis() >= deadline) {
                return false;
            }
        }
    }

    private static final Pattern SHOP_ID_PATTERN = Pattern.compile("/mmp/operator/shop/(\\d+)");

    /**
     * Step 109: reads the shop id out of the row link's href
     * ({@code /mmp/operator/shop/{id}}, confirmed live - see class javadoc), then clicks
     * it to open the shop's detail page. Returns the captured id so callers (e.g. the
     * Kibo check in step 111) can pass it downstream.
     */
    public String openShopAndCaptureId(String sellerNameOrTradeName) {
        By shopLink = By.xpath("//tr[contains(., \"" + sellerNameOrTradeName + "\")]"
                + "//a[contains(normalize-space(.), \"" + sellerNameOrTradeName + "\")]");
        String href = waitForVisible(shopLink).getAttribute("href");
        Matcher matcher = SHOP_ID_PATTERN.matcher(href == null ? "" : href);
        if (!matcher.find()) {
            throw new IllegalStateException("Could not parse a shop id out of shop link href: " + href);
        }
        String shopId = matcher.group(1);
        click(shopLink);
        return shopId;
    }

    private By resultRow(String sellerNameOrTradeName) {
        return By.xpath("//tr[contains(., \"" + sellerNameOrTradeName + "\")]");
    }
}
