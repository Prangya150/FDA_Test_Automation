package com.fda.automation.base;

import com.aventstack.extentreports.Status;
import com.fda.automation.config.ConfigManager;
import com.fda.automation.utils.ExtentReportManager;
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

    protected void click(By locator) {
        log.debug("Click: {}", locator);
        ExtentReportManager.log(Status.INFO, "Clicked element: " + locator);
        waitForLoaderToDisappear();
        WebElement element = waitForClickable(locator);
        try {
            element.click();
        } catch (ElementClickInterceptedException e) {
            // elementToBeClickable() only checks the target element itself (visible +
            // enabled), not whether something else covers it - a sticky header/footer can
            // still overlap it right after Selenium's default edge-of-viewport scroll,
            // especially on long single-page forms. Scroll it to the center of the
            // viewport (away from those fixed edges) and fall back to a JS click, which
            // dispatches the click event directly without needing to be the topmost
            // element at that point.
            log.debug("Click intercepted for {}, scrolling into view center and retrying via JS click", locator);
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
        }
        pauseForSlowMotion();
    }

    /**
     * Optional pause after each click()/type(), purely for a human watching the browser
     * live to be able to follow along - actions otherwise happen faster than eyes can
     * track. Off by default (0ms, no behavior change); enable with -Dslowmo.ms=1500 (or
     * any other value) on the mvn command line.
     */
    private void pauseForSlowMotion() {
        int slowMotionMillis = ConfigManager.getInstance().getSlowMotionMillis();
        if (slowMotionMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(slowMotionMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Waits out Magento's global AJAX loading-mask overlay. WebDriver's clickability
     * check only looks at the target element itself, not whether something else is
     * covering it - if this overlay is still up when click() fires, the click can
     * silently land on the overlay instead of the intended element with no exception
     * thrown, leaving the page looking "stuck" one step behind.
     */
    protected void waitForLoaderToDisappear() {
        wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".loading-mask")));
    }

    protected void type(By locator, String text) {
        log.debug("Type '{}' into: {}", text, locator);
        // waitForVisible() alone isn't enough - an input can be visible but still briefly
        // disabled while its page finishes loading (observed live: a Mirakl search input
        // sitting above a still-loading skeleton table threw
        // InvalidElementStateException/"not currently interactable" despite being
        // visible). elementToBeClickable() additionally waits for it to be enabled.
        WebElement el = waitForClickable(locator);
        boolean isPassword = "password".equalsIgnoreCase(el.getAttribute("type"));
        ExtentReportManager.log(Status.INFO, "Typed '" + (isPassword ? "••••••••" : text) + "' into element: " + locator);
        el.clear();
        el.sendKeys(text);
        // React-controlled inputs can mishandle native clear()/sendKeys(): either dropping
        // the first keystroke sent immediately after clear() (the change handler isn't
        // attached yet - observed live on Mirakl's shop search box, silently truncating the
        // search term), or clear() itself leaving stale text behind, which repeated retries
        // in MiraklShopSearchPage.waitForShopInResults() then kept appending onto instead of
        // replacing (observed live: the search box accumulated multiple copies of the term,
        // eventually breaking the page). Retrying the same native calls doesn't reliably fix
        // either case since both stem from the same underlying flakiness, so once a mismatch
        // is detected, force the value in directly via JS instead - this bypasses native key
        // simulation entirely, setting the value and firing the input/change events React's
        // controlled components listen for in one atomic step.
        if (!text.equals(el.getAttribute("value"))) {
            log.debug("Typed value mismatch for {} (found '{}'), forcing via JS", locator, el.getAttribute("value"));
            setValueViaJavascript(el, text);
            if (!text.equals(el.getAttribute("value"))) {
                throw new IllegalStateException("Could not type '" + text + "' into " + locator
                        + " - field contains '" + el.getAttribute("value") + "' after both native and JS attempts");
            }
        }
        pauseForSlowMotion();
    }

    private void setValueViaJavascript(WebElement el, String text) {
        ((JavascriptExecutor) driver).executeScript(
                "var setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;"
                        + "setter.call(arguments[0], arguments[1]);"
                        + "arguments[0].dispatchEvent(new Event('input', {bubbles: true}));"
                        + "arguments[0].dispatchEvent(new Event('change', {bubbles: true}));",
                el, text);
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

    /**
     * Like {@link #isDisplayed}, but polls up to {@code timeout} instead of checking once -
     * for locators whose appearance lags behind an action that triggers it asynchronously
     * (e.g. a search results grid re-filtering after typing into a search box), where an
     * instant check can race the update and wrongly report "not present" even though it
     * would have appeared moments later. Uses its own short-lived wait rather than the
     * shared {@code wait} field, so it doesn't affect that field's timeout for other calls.
     */
    protected boolean isVisibleWithin(By locator, Duration timeout) {
        try {
            new WebDriverWait(driver, timeout).until(ExpectedConditions.visibilityOfElementLocated(locator));
            return true;
        } catch (TimeoutException e) {
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

    /**
     * Opens a new browser tab and switches to it, returning the handle of the tab that was
     * active beforehand so the caller can switch back with {@link #closeTabAndSwitchBack}.
     */
    protected String openNewTab() {
        String originalHandle = driver.getWindowHandle();
        driver.switchTo().newWindow(WindowType.TAB);
        return originalHandle;
    }

    /** Closes the current tab and switches back to the tab identified by {@code originalHandle}. */
    protected void closeTabAndSwitchBack(String originalHandle) {
        driver.close();
        driver.switchTo().window(originalHandle);
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
