package com.fda.automation.base;

import com.fda.automation.config.ConfigManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.*;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;

public abstract class BasePage {
    protected final WebDriver driver;
    protected final WebDriverWait wait;
    protected static final Logger log = LogManager.getLogger(BasePage.class);

    protected BasePage(WebDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver,
                Duration.ofSeconds(ConfigManager.getInstance().getExplicitWait()));
        PageFactory.initElements(driver, this);
    }

    protected WebElement waitForClickable(By locator) {
        return wait.until(ExpectedConditions.elementToBeClickable(locator));
    }

    protected WebElement waitForVisible(By locator) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    protected WebElement waitForPresent(By locator) {
        return wait.until(ExpectedConditions.presenceOfElementLocated(locator));
    }

    /**
     * CONFIRMED live on 2026-08-20 across several Mirakl pages: a sticky header or a panel
     * expanded by a previous action can transiently overlap a target's click point, intercepting
     * a native click - fall back to a JS click on interception rather than fixing this
     * call-by-call at every affected locator.
     *
     * CONFIRMED live on 2026-09-01 (TC_FBS_004): on the FDA storefront's client-side (SPA-style)
     * navigation between products, an element located by {@code waitForClickable} can go stale in
     * the gap before {@code .click()} actually fires, as the DOM keeps re-rendering after landing
     * on the new page. Retries a few times (re-locating fresh each time) instead of fixing this
     * call-by-call at every affected locator, same rationale as the click-intercepted fallback.
     */
    protected void click(By locator) {
        log.debug("Click: {}", locator);
        final int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                WebElement element = waitForClickable(locator);
                try {
                    element.click();
                } catch (ElementClickInterceptedException e) {
                    log.debug("Native click intercepted for {}, falling back to JS click", locator);
                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
                }
                return;
            } catch (StaleElementReferenceException e) {
                if (attempt == maxAttempts) {
                    throw e;
                }
                log.debug("Element went stale for {} on attempt {}, retrying", locator, attempt);
            }
        }
    }

    protected void type(By locator, String text) {
        log.debug("Type '{}' into: {}", text, locator);
        WebElement el = waitForVisible(locator);
        el.clear();
        el.sendKeys(text);
    }

    protected String getText(By locator) {
        return waitForVisible(locator).getText().trim();
    }

    protected boolean isDisplayed(By locator) {
        try {
            return driver.findElement(locator).isDisplayed();
        } catch (NoSuchElementException e) {
            return false;
        }
    }

    protected void selectByVisibleText(By locator, String text) {
        new Select(waitForPresent(locator)).selectByVisibleText(text);
    }

    protected void selectByValue(By locator, String value) {
        new Select(waitForPresent(locator)).selectByValue(value);
    }

    protected List<WebElement> findAll(By locator) {
        wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(locator));
        return driver.findElements(locator);
    }

    protected void navigateTo(String path) {
        String url = ConfigManager.getInstance().getBaseUrl() + path;
        log.info("Navigate to: {}", url);
        driver.get(url);
    }

    public String getPageTitle() {
        return driver.getTitle();
    }

    public String getCurrentUrl() {
        return driver.getCurrentUrl();
    }
}
