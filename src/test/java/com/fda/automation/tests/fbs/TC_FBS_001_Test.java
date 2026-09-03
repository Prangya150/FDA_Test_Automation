package com.fda.automation.tests.fbs;

import com.fda.automation.api.kibo.KiboAuthService;
import com.fda.automation.api.kibo.KiboOrdersService;
import com.fda.automation.api.kibo.KiboShipmentService;
import com.fda.automation.base.BaseTest;
import com.fda.automation.base.SuiteSession;
import com.fda.automation.config.ConfigManager;
import com.fda.automation.listeners.SuiteLoginListener;
import com.fda.automation.models.OrderContext;
import com.fda.automation.reporting.StepLogger;
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
import com.fda.automation.utils.PollingUtils;
import com.fda.automation.utils.RandomDataUtils;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

/**
 * TC_FBS_001 - End-to-end FBS order lifecycle across FDA (Magento storefront), Mirakl
 * (marketplace operator) and Kibo Commerce (fulfillment/OMS APIs).
 *
 * Kept as a single @Test method: BaseTest provisions one WebDriver per @BeforeMethod/@AfterMethod
 * pair, so splitting this into independent @Test methods would each start a fresh, logged-out
 * browser and lose the FDA session/cart/order state that later steps depend on. The helper
 * methods below keep the business flow readable while the assertions stay in the test body.
 */
public class TC_FBS_001_Test extends BaseTest {

    private final ConfigManager config = ConfigManager.getInstance();
    private final OrderContext orderContext = new OrderContext();

    private FdaHomePage fdaHomePage;
    private FdaCartPage cartPage;
    private MiraklOrderDetailsPage miraklOrderDetailsPage;
    private String fdaWindowHandle;
    private String miraklWindowHandle;

    @Test(groups = {"regression", "fbs"},
            description = "Verify the complete FBS order lifecycle across FDA, Mirakl and Kibo")
    public void verifyFbsOrderLifecycle() {
        // Both applications are authenticated upfront, before any checkout steps run, so a
        // manual MFA prompt for Mirakl (if shown) is handled right away rather than mid-flow.
        loginToFDA();
        openMiraklInNewTab();
        loginToMirakl();
        switchToFdaTab();

        searchAndAddProductToCart();
        completeCheckoutAndPlaceOrder();
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
        StepLogger.step(1, "Login to FDA application");
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
        StepLogger.step(2, "Ensure FDA cart is empty before test");
        getDriver().get(config.getFdaBaseUrl() + "/checkout/cart/");
        FdaCartPage cartPage = new FdaCartPage(getDriver());
        if (!cartPage.isEmpty()) {
            cartPage.emptyCart();
        }
    }

    private void searchAndAddProductToCart() {
        StepLogger.step(6, "Search product by SKU and add to cart");
        FdaProductDetailsPage pdp = fdaHomePage.searchProduct(config.getFdaProductSku());

        Assert.assertTrue(pdp.isDisplayed(), "PDP was not displayed for SKU " + config.getFdaProductSku());
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

    private void completeCheckoutAndPlaceOrder() {
        StepLogger.step(7, "Complete checkout and place order (shipping -> payment -> 3DS -> success)");
        FdaShippingPage shippingPage = cartPage.proceedToCheckout();
        Assert.assertTrue(shippingPage.isDisplayed(), "FDA shipping step was not displayed");

        FdaPaymentPage paymentPage = shippingPage.clickSiguiente();
        Assert.assertTrue(paymentPage.isDisplayed(), "FDA payment step was not displayed");

        paymentPage.selectCreditDebitCardPayment();
        paymentPage.enterCardDetails(config.getFdaCardNumber(), config.getFdaCardExpiry(), config.getFdaCardCvv());

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

    private void verifyOrderHistory() {
        StepLogger.step(8, "Verify order appears in FDA order history with status 'Pendiente'");
        fdaHomePage.openMyAccountMenu();
        FdaOrderHistoryPage orderHistoryPage = fdaHomePage.openMyOrders();

        Assert.assertTrue(orderHistoryPage.isDisplayed(), "FDA order history page was not displayed");
        Assert.assertTrue(orderHistoryPage.isOrderPresent(orderContext.getOrderId()),
                "FDA order " + orderContext.getOrderId() + " was not found in order history");
        // CONFIRMED live on 2026-08-19: a freshly placed order shows "Pendiente" here, not "Creada"
        // as stated in the TC_FBS_001 text; see fda.order.initial.status in config.properties.
        String expectedInitialStatus = config.getFdaOrderInitialStatus();
        Assert.assertEquals(orderHistoryPage.getOrderStatus(orderContext.getOrderId()), expectedInitialStatus,
                "FDA order status was not '" + expectedInitialStatus + "'");
    }

    // ------------------------------------------------------------------
    // Mirakl
    // ------------------------------------------------------------------

    /** Switches to Mirakl's already-open tab (opened once for the whole suite by SuiteLoginListener). */
    private void openMiraklInNewTab() {
        StepLogger.step(3, "Open Mirakl operator front office in a new browser tab");
        miraklWindowHandle = SuiteSession.getMiraklWindowHandle();
        switchToMiraklTab();
    }

    private void switchToFdaTab() {
        StepLogger.step(5, "Switch browser focus back to FDA tab");
        getDriver().switchTo().window(fdaWindowHandle);
    }

    private void switchToMiraklTab() {
        StepLogger.step(9, "Switch browser focus to Mirakl tab");
        getDriver().switchTo().window(miraklWindowHandle);
        // Mirakl's Auth0 session can expire mid-suite (a run now spans all 7 tests, not just one) -
        // see SuiteLoginListener.reauthenticateIfExpired for why this check lives here.
        SuiteLoginListener.reauthenticateIfExpired();
    }

    private void loginToMirakl() {
        StepLogger.step(4, "Login to Mirakl operator front office (with MFA if prompted)");
        // No-op: login now happens once for the whole suite (see SuiteLoginListener), and
        // switchToMiraklTab() already re-authenticates if that shared session expired mid-suite.
        // Kept as a call site so this method's callers don't need to change.
    }

    private void verifyAndAcceptMiraklOrder() {
        StepLogger.step(10, "Verify FDA order appears in Mirakl, verify totals match, and accept order");
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
        StepLogger.step(14, "Verify all expected options are present in Mirakl 'More actions' menu");
        miraklOrderDetailsPage.openMoreActions();
        List<String> missing = miraklOrderDetailsPage.getMissingMoreActionsOptions();
        Assert.assertTrue(missing.isEmpty(), "Missing Mirakl 'More actions' options: " + missing);
    }

    private void uploadInvoice() {
        StepLogger.step(15, "Upload invoice document to Mirakl accounting documents");
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
        StepLogger.step(16, "Add DHL tracking information to Mirakl order");
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
        StepLogger.step(17, "Mark Mirakl order as shipped and wait for status change");
        miraklOrderDetailsPage.markAsShipped();
        Duration timeout = Duration.ofSeconds(config.getMiraklSyncTimeoutSeconds());
        miraklOrderDetailsPage.waitForStatus("Shipped", timeout);
        Assert.assertTrue(miraklOrderDetailsPage.getStatus().equalsIgnoreCase("Shipped"),
                "Mirakl order status did not change from 'Awaiting shipment' to 'Shipped'");
    }

    private void markOrderAsReceived() {
        StepLogger.step(18, "Mark Mirakl order as received via custom field 'Entregado = Yes'");
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
        StepLogger.step(11, "Authenticate with Kibo Commerce API (OAuth client-credentials)");
        KiboAuthService authService = new KiboAuthService(config.getKiboAuthUrl());
        String accessToken = authService.generateAccessToken(config.getKiboClientId(), config.getKiboClientSecret());
        Assert.assertNotNull(accessToken, "Kibo access token was not generated");
        orderContext.setKiboAccessToken(accessToken);
    }

    private void getKiboOrderDetails() {
        StepLogger.step(12, "Find Kibo order by FDA externalId and retrieve Kibo order ID");
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
        StepLogger.step(13, "Verify Kibo shipment delivery type equals 'FBS'");
        KiboShipmentService shipmentService = new KiboShipmentService(config.getKiboShipmentsUrl());
        String deliveryType = shipmentService.getDeliveryType(
                orderContext.getKiboAccessToken(), orderContext.getKiboOrderId(), config.getKiboShipmentDeliveryTypeJsonPath());

        orderContext.setDeliveryType(deliveryType);
        Assert.assertEquals(deliveryType, "FBS", "Kibo shipment delivery type was not 'FBS'");
    }
}
