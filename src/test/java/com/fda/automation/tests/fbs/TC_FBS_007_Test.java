package com.fda.automation.tests.fbs;

import com.fda.automation.api.kibo.KiboAuthService;
import com.fda.automation.api.kibo.KiboOrdersService;
import com.fda.automation.api.kibo.KiboShipmentService;
import com.fda.automation.base.BaseTest;
import com.fda.automation.base.SuiteSession;
import com.fda.automation.config.ConfigManager;
import com.fda.automation.listeners.SuiteLoginListener;
import com.fda.automation.models.OrderContext;
import com.fda.automation.pages.fda.FdaCartPage;
import com.fda.automation.pages.fda.FdaHomePage;
import com.fda.automation.pages.fda.FdaOrderHistoryPage;
import com.fda.automation.pages.fda.FdaOrderSuccessPage;
import com.fda.automation.pages.fda.FdaPaymentPage;
import com.fda.automation.pages.fda.FdaProductDetailsPage;
import com.fda.automation.pages.fda.FdaShippingPage;
import com.fda.automation.pages.mirakl.MiraklDocumentsPage;
import com.fda.automation.pages.mirakl.MiraklOrderDetailsPage;
import com.fda.automation.pages.mirakl.MiraklOrdersPage;
import com.fda.automation.pages.mirakl.MiraklTrackingPage;
import com.fda.automation.pages.paypal.PayPalPage;
import com.fda.automation.utils.PollingUtils;
import com.fda.automation.utils.RandomDataUtils;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * TC_FBS_007 - End-to-end FBS order lifecycle across FDA (Magento storefront), Mirakl
 * (marketplace operator) and Kibo Commerce (fulfillment/OMS APIs), paid via PayPal instead of the
 * credit/debit card flow used by TC_FBS_001. Otherwise identical in shape to TC_FBS_001: 1 product,
 * 1 quantity, single 3P (FBS) seller.
 *
 * PayPal is new ground for this framework - {@link PayPalPage} and the PayPal-specific locators on
 * {@link FdaPaymentPage} are best-effort and NOT YET CONFIRMED against a real run (unlike the
 * FDA/Mirakl locators elsewhere, which all carry "CONFIRMED live on ..." notes from one). Expect to
 * adjust {@link PayPalPage} once this test actually reaches the PayPal-hosted pages.
 *
 * Kept as a single @Test method for the same reason as the other FBS tests: BaseTest provisions one
 * WebDriver per @BeforeMethod/@AfterMethod pair, so splitting this into independent @Test methods
 * would each start a fresh, logged-out browser and lose the FDA session/cart/order state that later
 * steps depend on.
 */
public class TC_FBS_007_Test extends BaseTest {

    private final ConfigManager config = ConfigManager.getInstance();
    private final OrderContext orderContext = new OrderContext();

    private FdaHomePage fdaHomePage;
    private FdaCartPage cartPage;
    private MiraklOrderDetailsPage miraklOrderDetailsPage;
    private String fdaWindowHandle;
    private String miraklWindowHandle;

    @Test(groups = {"regression", "fbs"},
            description = "Verify the complete FBS order lifecycle across FDA, Mirakl and Kibo, paid via PayPal")
    public void verifyFbsOrderLifecycleWithPayPal() {
        // Both applications are authenticated upfront, before any checkout steps run, so a
        // manual MFA prompt for Mirakl (if shown) is handled right away rather than mid-flow.
        loginToFDA();
        openMiraklInNewTab();
        loginToMirakl();
        switchToFdaTab();

        searchAndAddProductToCart();
        completeCheckoutAndPlaceOrderWithPayPal();
        verifyOrderHistory();

        switchToMiraklTab();
        verifyAndAcceptMiraklOrder();

        authenticateWithKibo();
        getKiboOrderDetails();
        verifyShipmentDeliveryType();

        verifyMiraklMoreActionsOptions();
        uploadInvoice();
        addTrackingInformation();
        markOrderAsShipped();
        markOrderAsReceived();
    }

    // ------------------------------------------------------------------
    // FDA
    // ------------------------------------------------------------------

    private void loginToFDA() {
        // Login now happens once for the whole suite (see SuiteLoginListener) instead of here -
        // just switch to the already-authenticated FDA tab and make sure the shared account's cart
        // is empty before this test's own "quantity is 1" assertions run.
        fdaWindowHandle = SuiteSession.getFdaWindowHandle();
        switchToFdaTab();

        fdaHomePage = new FdaHomePage(getDriver());
        fdaHomePage.waitUntilLoaded();

        ensureCartIsEmpty();
    }

    /**
     * This account's cart persists across runs, which would otherwise break the "quantity is 1"
     * assertions the test case requires if a previous run (or a previous manual session) left
     * items behind.
     */
    private void ensureCartIsEmpty() {
        getDriver().get(config.getFdaBaseUrl() + "/checkout/cart/");
        FdaCartPage cartPage = new FdaCartPage(getDriver());
        if (!cartPage.isEmpty()) {
            cartPage.emptyCart();
        }
    }

    private void searchAndAddProductToCart() {
        FdaProductDetailsPage pdp = fdaHomePage.searchProduct(config.getFbs007ProductSku());

        Assert.assertTrue(pdp.isDisplayed(), "PDP was not displayed for SKU " + config.getFbs007ProductSku());
        Assert.assertTrue(pdp.isAddToCartEnabled(), "'Agregar al carrito' button was not enabled");
        Assert.assertEquals(pdp.getQuantity(), "1", "Default PDP quantity was not 1");

        pdp.addToCart();
        FdaCartPage cartPage = pdp.openMiCarrito();

        Assert.assertTrue(cartPage.isDisplayed(), "Cart page was not displayed");
        Assert.assertEquals(cartPage.getQuantity(), "1", "Cart quantity was not 1");

        orderContext.setFdaOrderTotal(cartPage.getCartTotal());
        Assert.assertNotNull(orderContext.getFdaOrderTotal(), "FDA order total was not captured from the cart page");

        this.cartPage = cartPage;
    }

    private void completeCheckoutAndPlaceOrderWithPayPal() {
        FdaShippingPage shippingPage = cartPage.proceedToCheckout();
        Assert.assertTrue(shippingPage.isDisplayed(), "FDA shipping step was not displayed");

        FdaPaymentPage paymentPage = shippingPage.clickSiguiente();
        Assert.assertTrue(paymentPage.isDisplayed(), "FDA payment step was not displayed");

        paymentPage.selectPayPalPayment();

        Set<String> handlesBeforePayPal = getDriver().getWindowHandles();
        paymentPage.clickPayPalPagarButton();

        switchToPayPal(handlesBeforePayPal);
        PayPalPage payPalPage = new PayPalPage(getDriver());
        Assert.assertTrue(payPalPage.isDisplayed(), "PayPal login page was not displayed");

        payPalPage.login(config.getFdaPaypalEmail(), config.getFdaPaypalPassword());
        payPalPage.selectPayWithOption("Visa");
        payPalPage.clickFullPurchase();

        switchBackToFdaAfterPayPal();

        String placeOrderButtonText = paymentPage.getPlaceOrderButtonText();
        String expectedTotalDigits = orderContext.getFdaOrderTotal().toPlainString();
        Assert.assertTrue(placeOrderButtonText.contains(expectedTotalDigits)
                        || placeOrderButtonText.replaceAll("[^0-9.]", "").contains(expectedTotalDigits),
                "'Completar pago' button did not reflect the expected order total. Button text: " + placeOrderButtonText);

        FdaOrderSuccessPage successPage = paymentPage.completarPago();
        Assert.assertTrue(successPage.isDisplayed(), "FDA order success page was not displayed");

        String orderId = successPage.captureOrderId();
        Assert.assertNotNull(orderId, "FDA order id was not generated");
        orderContext.setOrderId(orderId);
    }

    /**
     * Switches to wherever PayPal actually rendered: a new popup window/tab (typical for hosted
     * PayPal checkouts) or a same-window redirect. Not yet confirmed live which of the two this
     * integration actually does - handles both rather than assuming one.
     */
    private void switchToPayPal(Set<String> handlesBeforePayPal) {
        new WebDriverWait(getDriver(), Duration.ofSeconds(30)).until(d -> {
            Set<String> handlesNow = d.getWindowHandles();
            return handlesNow.size() > handlesBeforePayPal.size() || d.getCurrentUrl().contains("paypal.com");
        });

        Set<String> handlesAfter = getDriver().getWindowHandles();
        if (handlesAfter.size() > handlesBeforePayPal.size()) {
            String newHandle = handlesAfter.stream()
                    .filter(h -> !handlesBeforePayPal.contains(h))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("A new window opened for PayPal but its handle could not be found"));
            getDriver().switchTo().window(newHandle);
        }

        new WebDriverWait(getDriver(), Duration.ofSeconds(30)).until(d -> d.getCurrentUrl().contains("paypal.com"));
    }

    /**
     * Returns focus to the FDA tab once PayPal hands control back - either because its popup
     * window closed itself, or because the same window redirected back to the FDA domain.
     */
    private void switchBackToFdaAfterPayPal() {
        if (getDriver().getWindowHandles().contains(fdaWindowHandle)) {
            getDriver().switchTo().window(fdaWindowHandle);
        }
        new WebDriverWait(getDriver(), Duration.ofSeconds(60)).until(d -> !d.getCurrentUrl().contains("paypal.com"));
    }

    private void verifyOrderHistory() {
        fdaHomePage.openMyAccountMenu();
        FdaOrderHistoryPage orderHistoryPage = fdaHomePage.openMyOrders();

        Assert.assertTrue(orderHistoryPage.isDisplayed(), "FDA order history page was not displayed");
        Assert.assertTrue(orderHistoryPage.isOrderPresent(orderContext.getOrderId()),
                "FDA order " + orderContext.getOrderId() + " was not found in order history");
        // Same live-confirmed status as the earlier FBS tests (fda.order.initial.status) - a
        // freshly placed order shows "Pendiente" here by default, not "Creada" as stated in the
        // test case text.
        String expectedInitialStatus = config.getFdaOrderInitialStatus();
        Assert.assertEquals(orderHistoryPage.getOrderStatus(orderContext.getOrderId()), expectedInitialStatus,
                "FDA order status was not '" + expectedInitialStatus + "'");
    }

    // ------------------------------------------------------------------
    // Mirakl
    // ------------------------------------------------------------------

    /** Switches to Mirakl's already-open tab (opened once for the whole suite by SuiteLoginListener). */
    private void openMiraklInNewTab() {
        miraklWindowHandle = SuiteSession.getMiraklWindowHandle();
        switchToMiraklTab();
    }

    private void switchToFdaTab() {
        getDriver().switchTo().window(fdaWindowHandle);
    }

    private void switchToMiraklTab() {
        getDriver().switchTo().window(miraklWindowHandle);
        // Mirakl's Auth0 session can expire mid-suite (a run now spans all 7 tests, not just one) -
        // see SuiteLoginListener.reauthenticateIfExpired for why this check lives here.
        SuiteLoginListener.reauthenticateIfExpired();
    }

    private void loginToMirakl() {
        // No-op: login now happens once for the whole suite (see SuiteLoginListener), and
        // switchToMiraklTab() already re-authenticates if that shared session expired mid-suite.
        // Kept as a call site so this method's callers don't need to change.
    }

    private void verifyAndAcceptMiraklOrder() {
        MiraklOrdersPage ordersPage = new MiraklOrdersPage(getDriver());

        // The Orders menu can lag briefly right after login while the dashboard finishes loading;
        // poll for clickability instead of a blind fixed sleep (explicitly required by the test case).
        ordersPage.openOrdersMenu(Duration.ofSeconds(60));
        ordersPage.openAllOrders();

        Duration syncTimeout = Duration.ofSeconds(config.getMiraklSyncTimeoutSeconds());
        Duration syncPollInterval = Duration.ofSeconds(config.getMiraklSyncPollIntervalSeconds());
        ordersPage.waitForOrderToAppear(orderContext.getOrderId(), syncTimeout, syncPollInterval);

        Assert.assertTrue(ordersPage.isOrderDisplayed(orderContext.getOrderId()),
                "Mirakl order " + orderContext.getOrderId() + " was not found");
        Assert.assertEquals(ordersPage.getOrderStatus(orderContext.getOrderId()), "Pending acceptance",
                "Mirakl order status was not 'Pending acceptance'");

        miraklOrderDetailsPage = ordersPage.openOrder(orderContext.getOrderId());

        BigDecimal miraklTotal = miraklOrderDetailsPage.getOrderTotal();
        orderContext.setMiraklOrderTotal(miraklTotal);
        Assert.assertEquals(miraklTotal.stripTrailingZeros(), orderContext.getFdaOrderTotal().stripTrailingZeros(),
                "Mirakl order total (" + miraklTotal + ") did not match FDA order total (" + orderContext.getFdaOrderTotal() + ")");

        miraklOrderDetailsPage.acceptOrder();
        miraklOrderDetailsPage.waitForStatus("Awaiting shipment", syncTimeout);
        Assert.assertTrue(miraklOrderDetailsPage.getStatus().equalsIgnoreCase("Awaiting shipment"),
                "Mirakl order status did not change to 'Awaiting shipment' after accepting");
    }

    private void verifyMiraklMoreActionsOptions() {
        miraklOrderDetailsPage.openMoreActions();
        List<String> missing = miraklOrderDetailsPage.getMissingMoreActionsOptions();
        Assert.assertTrue(missing.isEmpty(), "Missing Mirakl 'More actions' options: " + missing);
    }

    private void uploadInvoice() {
        MiraklDocumentsPage documentsPage = miraklOrderDetailsPage.openDocuments();

        Assert.assertTrue(documentsPage.isAccountingDocumentsSectionDisplayed(), "Accounting Documents section was not displayed");
        Assert.assertTrue(documentsPage.isAddButtonDisplayed(), "'Add' button was not displayed in Accounting Documents");

        documentsPage.clickAdd();
        Assert.assertTrue(documentsPage.isUploadPopupDisplayed(), "'Upload an order document' popup was not displayed");

        documentsPage.selectDocumentType("Invoice");
        documentsPage.uploadFile(config.getInvoiceFilePath());
        documentsPage.clickConfirm();

        String confirmation = documentsPage.waitForUploadConfirmation();
        Assert.assertTrue(confirmation.contains("The document has been uploaded"),
                "Upload confirmation message was not displayed. Got: " + confirmation);

        miraklOrderDetailsPage = documentsPage.backToOrder();
    }

    private void addTrackingInformation() {
        MiraklTrackingPage trackingPage = miraklOrderDetailsPage.openAddTrackingInformation();
        Assert.assertTrue(trackingPage.isPopupDisplayed(), "Add tracking information popup was not displayed");

        List<String> carriers = trackingPage.getAvailableCarriers();
        Assert.assertFalse(carriers.isEmpty(), "No carriers were listed in the tracking popup");

        String carrier = config.getTrackingCarrier();
        trackingPage.selectCarrier(carrier);

        String trackingNumber = RandomDataUtils.generateTrackingNumber();
        Assert.assertEquals(trackingNumber.length(), 8, "Generated tracking number was not exactly 8 digits");
        orderContext.setTrackingNumber(trackingNumber);

        trackingPage.enterTrackingNumber(trackingNumber);
        trackingPage.clickAdd();

        Assert.assertTrue(trackingPage.isCarrierDisplayed(carrier), "Carrier was not displayed as " + carrier);
        Assert.assertTrue(trackingPage.isTrackingNumberDisplayed(trackingNumber),
                "Tracking number " + trackingNumber + " was not displayed correctly");
    }

    private void markOrderAsShipped() {
        miraklOrderDetailsPage.markAsShipped();
        Duration timeout = Duration.ofSeconds(config.getMiraklSyncTimeoutSeconds());
        miraklOrderDetailsPage.waitForStatus("Shipped", timeout);
        Assert.assertTrue(miraklOrderDetailsPage.getStatus().equalsIgnoreCase("Shipped"),
                "Mirakl order status did not change from 'Awaiting shipment' to 'Shipped'");
    }

    private void markOrderAsReceived() {
        miraklOrderDetailsPage.openMoreActions();
        List<String> missing = miraklOrderDetailsPage.getMissingMoreActionsOptions();
        Assert.assertTrue(missing.isEmpty(), "Missing Mirakl 'More actions' options before Custom field: " + missing);

        miraklOrderDetailsPage.openCustomField();
        Assert.assertTrue(MiraklOrderDetailsPage.CUSTOM_FIELD_OPTIONS.contains("Entregado"),
                "'Entregado' is not one of the known Custom field options");

        miraklOrderDetailsPage.selectCustomFieldOption("Entregado");
        miraklOrderDetailsPage.selectEditAdditionalInformationValue("Yes");
        miraklOrderDetailsPage.confirmEditAdditionalInformation();
        miraklOrderDetailsPage.markAsReceived();

        Duration timeout = Duration.ofSeconds(config.getMiraklSyncTimeoutSeconds());
        miraklOrderDetailsPage.waitForStatus("Received", timeout);
        Assert.assertTrue(miraklOrderDetailsPage.getStatus().equalsIgnoreCase("Received"),
                "Mirakl order status did not change from 'Shipped' to 'Received'");
    }

    // ------------------------------------------------------------------
    // Kibo
    // ------------------------------------------------------------------

    private void authenticateWithKibo() {
        KiboAuthService authService = new KiboAuthService(config.getKiboAuthUrl());
        String accessToken = authService.generateAccessToken(config.getKiboClientId(), config.getKiboClientSecret());
        Assert.assertNotNull(accessToken, "Kibo access token was not generated");
        orderContext.setKiboAccessToken(accessToken);
    }

    private void getKiboOrderDetails() {
        KiboOrdersService ordersService = new KiboOrdersService(config.getKiboOrdersUrl());

        String kiboOrderId = PollingUtils.pollUntil(
                () -> ordersService.findKiboOrderIdByExternalId(orderContext.getKiboAccessToken(), orderContext.getOrderId()),
                id -> id != null,
                Duration.ofSeconds(config.getKiboSyncTimeoutSeconds()),
                Duration.ofSeconds(config.getKiboSyncPollIntervalSeconds()),
                "No Kibo order found with externalId matching FDA order " + orderContext.getOrderId());

        Assert.assertNotNull(kiboOrderId, "Kibo order id was not generated");
        orderContext.setKiboOrderId(kiboOrderId);
    }

    private void verifyShipmentDeliveryType() {
        KiboShipmentService shipmentService = new KiboShipmentService(config.getKiboShipmentsUrl());
        String deliveryType = shipmentService.getDeliveryType(
                orderContext.getKiboAccessToken(), orderContext.getKiboOrderId(), config.getKiboShipmentDeliveryTypeJsonPath());

        orderContext.setDeliveryType(deliveryType);
        Assert.assertEquals(deliveryType, "FBS", "Kibo shipment delivery type was not 'FBS'");
    }
}
