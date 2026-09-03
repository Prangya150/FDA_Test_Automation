# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Run all tests (default suite: testng.xml, parallel="methods" thread-count=2)
mvn test

# Run a specific suite file
mvn test -DsuiteXmlFile=src/test/resources/testng_smoke.xml

# Run only smoke tests (using TestNG groups via surefire)
mvn test -Dgroups=smoke

# Run headless in a different browser
mvn test -Dbrowser=firefox -Dheadless=true

# Compile without running tests
mvn compile test-compile

# Clean build artifacts
mvn clean
```

If `mvn` isn't on PATH, use the wrapper committed in this repo instead: `./mvnw.cmd` (Windows) / `./mvnw` (Unix) — same arguments.

Config overrides are passed as `-D` system properties; `ConfigManager` reads `config.properties` first, but system properties take precedence when accessed via `System.getProperty`. Secrets (passwords, API keys) are meant to be supplied this way in CI rather than committed — the checked-in `config.properties` values are the actual sandbox/staging credentials currently in use, not placeholders.

**Running a single test via an IDE's TestNG runner** (e.g. Eclipse "Run As > TestNG Test") generates an ad-hoc suite XML on the fly and does **not** include the `<listeners>` wired in `src/test/resources/testng.xml` — so `TestListener`'s auto-screenshot-on-failure does not fire in that mode. Use `mvn test -Dtest=TC_FBS_00N_Test` (or add the class to a suite file) if a failure screenshot is needed.

## Architecture

**Layer structure** (src/main → framework; src/test → tests + pages):

```
src/main/java/com/fda/automation/
  config/ConfigManager.java      — singleton, loads config.properties; getters for browser/url/wait/headless and per-test-case values
  api/kibo/                      — REST Assured clients for the Kibo Commerce OMS (auth, orders, shipments)
  utils/DriverFactory.java       — creates WebDriver per browser; reads from ConfigManager; supports a persistent Chrome profile (chrome.user.data.dir)
  utils/ScreenshotUtils.java     — captures PNG to target/screenshots/ on failure
  utils/ExcelUtils.java          — reads .xlsx sheets into List<Map<String,String>> or Object[][] for @DataProvider
  utils/PollingUtils.java        — generic retry/poll helper for cross-system async sync (FDA→Mirakl, FDA→Kibo) that no WebDriverWait condition can observe
  utils/CurrencyUtils.java       — parses displayed currency strings (e.g. "MXN$2.00") into BigDecimal for cross-system total comparisons
  models/OrderContext.java       — carries values captured at one stage of a scenario (FDA order id/total) that a later stage (Mirakl, Kibo) needs
  base/BasePage.java             — abstract; wraps WebDriverWait; provides click/type/getText/isDisplayed/select helpers, with built-in retry on stale/intercepted clicks
  base/BaseTest.java             — TestNG base; ThreadLocal<WebDriver> for parallel safety; @BeforeMethod/@AfterMethod
  listeners/TestListener.java    — ITestListener; logs pass/fail/skip; auto-captures screenshot on failure (only active when wired via testng.xml, see Commands)

src/test/java/com/fda/automation/
  pages/fda/                     — Page Objects for the FDA storefront (Magento 2 + Adyen payment + Empathy search widget)
  pages/mirakl/                  — Page Objects for the Mirakl operator front office (marketplace/seller management)
  pages/paypal/                  — Page Object for the PayPal-hosted checkout pages
  tests/fbs/                     — TC_FBS_00N end-to-end order-lifecycle tests (see below)
```

**Key design decisions:**
- `BaseTest` uses `ThreadLocal<WebDriver>` — tests run in parallel (`parallel="methods"` in testng.xml) safely
- Implicit waits are explicitly set to 0; all waits go through `WebDriverWait` in `BasePage`
- `BasePage.navigateTo(path)` prepends `base.url` from config — page objects use relative paths only
- `TestListener` is wired in `testng.xml`, not via annotation, so it applies to all tests automatically — but only when the suite is actually run through that file (see the IDE-runner caveat in Commands)

**Adding a new page:**
1. Create `src/test/java/com/fda/automation/pages/<app>/FooPage.java` extending `BasePage`
2. Define `private static final By` locators as constants
3. Use `BasePage` helper methods (`click`, `type`, `getText`, etc.) — do not call `driver.findElement` directly

**Adding a new test:**
1. Create `src/test/java/com/fda/automation/tests/FooTest.java` extending `BaseTest`
2. Call `getDriver()` to obtain the thread-local driver and pass to page constructors
3. Tag with `groups = {"smoke"}` or `groups = {"regression"}`

**Data-driven tests:** Use `ExcelUtils.toDataProvider(filePath, sheetName)` as the `@DataProvider` source; each row arrives as `Map<String, String>`.

## The TC_FBS test suite

`src/test/java/com/fda/automation/tests/fbs/TC_FBS_00N_Test.java` is a family of end-to-end tests that each drive the **same three-system order lifecycle** — FDA storefront checkout → Mirakl marketplace acceptance/fulfillment → Kibo Commerce OMS verification — varying one or two dimensions per test case (product count, quantity, seller count, payment method):

| Test | Products × qty | Sellers | Payment |
|---|---|---|---|
| TC_FBS_001 | 1×1 | 1 (FBS) | Adyen credit/debit card |
| TC_FBS_002 | 2×1 | 1 (FBS) | card |
| TC_FBS_003 | 1×2 | 1 (FBS) | card |
| TC_FBS_004 | 2×2 | 1 (FBS) | card |
| TC_FBS_005 | 2×1 | 2 different FBS sellers | card |
| TC_FBS_006 | 2×2 | 2 different FBS sellers | card |
| TC_FBS_007 | 1×1 | 1 (FBS) | PayPal |

Each is **one `@Test` method**, not split into steps — `BaseTest` provisions a fresh `WebDriver` per `@BeforeMethod`/`@AfterMethod`, so splitting would lose the FDA session/cart/order state between steps. Mirakl/Kibo helper methods are mirrored (not shared/extracted) into each test class rather than factored into a common base, so read the specific test you're touching rather than assuming shared logic.

A single scenario spans two browser tabs in one `WebDriver` session (FDA + Mirakl opened via `driver.switchTo().newWindow(WindowType.TAB)`), plus REST Assured calls to Kibo. `OrderContext` (in `models/`) threads the order id and totals captured on the FDA side through to the Mirakl/Kibo assertions later in the same test.

**Config keys**: `fbs00N.product1.sku` / `fbs00N.product2.sku` per test case in `config.properties`, reusing shared `fda.*`, `mirakl.*`, `invoice.file.path`, `tracking.carrier`, `kibo.*` keys. `fda.order.initial.status=Pendiente` — CONFIRMED live to be the actual freshly-placed-order status, not "Creada" as the original manual test case text says; several other config values likewise reflect real environment behavior discovered by running the tests, not the written test-case spec.

**Multi-seller checkouts (TC_FBS_005/006) split into one Mirakl suborder per seller**, referenced as the FDA order number plus an incrementing suffix (`WEB-A`, `WEB-B`, ...) — see `MiraklOrdersPage.findSuborderReferences`. Each suborder gets its own full accept→documents→tracking→ship→receive lifecycle, run in a loop, end-to-end per suborder rather than batched by step. Kibo mirrors this as one shipment whose `items` array has one entry per seller (`KiboShipmentService.getAllDeliveryTypes`) — not one shipment per seller — so verifying delivery type must walk every item of every shipment, not just `shipments[0].items[0]`.

**`mirakl.sync.timeout.seconds=1200` (20 min)**: FDA→Mirakl order sync latency, and the async "Entregado" custom-field → Received status transition, are both highly variable and have been observed to occasionally take the full window. A timeout here is not necessarily a script bug — check Mirakl directly before assuming one.

**TC_FBS_007 (PayPal) is the newest and least-verified path.** Unlike every other locator in this codebase, which carries a "CONFIRMED live on <date>" comment from an actual completed run, `pages/paypal/PayPalPage.java` and the PayPal-specific locators in `FdaPaymentPage` are still being hardened against real runs — expect to need locator fixes there before treating a TC_FBS_007 failure as a real regression rather than an unfinished locator.

## Locator-hardening conventions (read before editing page objects)

This app stack (Magento 2 SPA-ish storefront on FDA, a heavy SPA operator front office on Mirakl OP3) has produced enough surprising, non-obvious UI behavior that fixes are documented inline as they're discovered, rather than assumed to generalize. Patterns already hit more than once, so worth knowing before adding new locators:
- **Text matches are case-insensitive by default** (`translate(...)` xpath idiom) — CSS `text-transform` (e.g. "VIEW" vs "View") and inconsistent capitalization across environments have both broken naive case-sensitive matches.
- **Match the innermost leaf element containing the text, not any ancestor** — a bare `contains()` xpath returns the outer wrapping element first in document order, which often has no click handler of its own.
- **ajax-populated sections need a longer, dedicated wait**, not the shared default `explicit.wait` (10s) — the payment method list and Adyen secured-field iframes are both skeleton-rendered first, then populated by a follow-up ajax call; bigger carts (more line items/sellers) widen this gap further.
- **Retry stale/intercepted elements** rather than fixing case-by-case — `BasePage.click()` already retries on `StaleElementReferenceException`/`ElementClickInterceptedException`; page-specific fill loops (e.g. Adyen secured fields, 3DS challenge) follow the same pattern for their own multi-step interactions.
