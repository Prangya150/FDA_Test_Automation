package com.fda.automation.tests.fbs;

import com.fda.automation.api.kibo.KiboAuthService;
import com.fda.automation.api.kibo.KiboOrdersService;
import com.fda.automation.api.kibo.KiboShipmentService;
import com.fda.automation.base.BaseTest;
import com.fda.automation.config.ConfigManager;
import com.fda.automation.models.OrderContext;
import com.fda.automation.pages.fda.FdaCartPage;
import com.fda.automation.pages.fda.FdaHomePage;
import com.fda.automation.pages.fda.FdaLoginPage;
import com.fda.automation.pages.fda.FdaOrderHistoryPage;
import com.fda.automation.pages.fda.FdaOrderSuccessPage;
import com.fda.automation.pages.fda.FdaPaymentPage;
import com.fda.automation.pages.fda.FdaProductDetailsPage;
import com.fda.automation.pages.fda.FdaShippingPage;
import com.fda.automation.pages.mirakl.MiraklDocumentsPage;
import com.fda.automation.pages.mirakl.MiraklLoginPage;
import com.fda.automation.pages.mirakl.MiraklOrderDetailsPage;
import com.fda.automation.pages.mirakl.MiraklOrdersPage;
import com.fda.automation.pages.mirakl.MiraklTrackingPage;
import com.fda.automation.utils.PollingUtils;
import com.fda.automation.utils.RandomDataUtils;
import org.openqa.selenium.WindowType;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

/**
 * TC_FBS_003 - Verify that a user can place an order for 1 product at quantity 2, fulfilled by a
 * single 3P (FBS) seller, across FDA (Magento storefront), Mirakl (marketplace operator) and Kibo
 * Commerce (fulfillment/OMS APIs).
 *
 * Kept as a single @Test method for the same reason as TC_FBS_001_Test and TC_FBS_002_Test:
 * BaseTest provisions one WebDriver per @BeforeMethod/@AfterMethod pair, so splitting this into
 * independent @Test methods would each start a fresh, logged-out browser and lose the FDA
 * session/cart/order state that later steps depend on. The Mirakl/Kibo portion of this flow is
 * identical to TC_FBS_001 (a single order, single line item), so those helper methods mirror
 * TC_FBS_001_Test; only the FDA quantity-stepper interaction is new.
 */
public class TC_FBS_003_Test extends BaseTest {

    private final ConfigManager config = ConfigManager.getInstance();
    private final OrderContext orderContext = new OrderContext();

    private FdaHomePage fdaHomePage;
    private FdaCartPage cartPage;
    private MiraklOrderDetailsPage miraklOrderDetailsPage;
    private String fdaWindowHandle;
    private String miraklWindowHandle;

    @Test(groups = {"regression", "fbs"},
            description = "Verify that a user can place 1 product with 2 quantities through one 3P (FBS) seller")
    public void verifyFbsOrderWithTwoQuantities() {
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
        // Using a persistent Chrome profile (see chrome.user.data.dir) so Mirakl's MFA-skip
        // cookie survives across runs also carries over any existing FDA session on that same
        // profile. Log out first so the login flow below always starts from a known-logged-out
        // state regardless of what a previous run left behind.
        getDriver().get(config.getFdaBaseUrl() + "/customer/account/logout/");

        getDriver().get(config.getFdaBaseUrl());

        FdaLoginPage loginPage = new FdaLoginPage(getDriver());
        loginPage.openAccountMenu();
        loginPage.clickLoginLink();
        Assert.assertTrue(loginPage.isLoginFormDisplayed(), "FDA login page was not displayed");

        loginPage.login(config.getFdaUsername(), config.getFdaPassword());

        fdaHomePage = new FdaHomePage(getDriver());
        fdaHomePage.waitUntilLoaded();

        fdaWindowHandle = getDriver().getWindowHandle();

        ensureCartIsEmpty();
    }

    /**
     * This account's cart persists across runs, which would otherwise break the "quantity is 2"
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
        // Calling FDA HomePage method to search for the product SKU
        FdaProductDetailsPage pdp = fdaHomePage.searchProduct(config.getFbs003ProductSku());

        Assert.assertTrue(pdp.isDisplayed(), "PDP was not displayed for SKU " + config.getFbs003ProductSku());
        Assert.assertTrue(pdp.isAddToCartEnabled(), "'Agregar al carrito' button was not enabled");
        Assert.assertEquals(pdp.getQuantity(), "1", "Default PDP quantity was not 1");

        // Calling FDA ProductDetailsPage method to increase the quantity from 1 to 2
        pdp.increaseQuantity();
        Assert.assertEquals(pdp.getQuantity(), "2", "PDP quantity was not 2 after increasing it");

        // Calling FDA ProductDetailsPage method to add the product (at quantity 2) to the cart
        pdp.addToCart();
        FdaCartPage cartPage = pdp.openMiCarrito();

        Assert.assertTrue(cartPage.isDisplayed(), "Cart page was not displayed");
        Assert.assertNotNull(cartPage.getProductName(), "Product name was not displayed on the Cart page");
        Assert.assertEquals(cartPage.getQuantity(), "2", "Cart quantity was not 2");

        orderContext.setFdaOrderTotal(cartPage.getCartTotal());
        Assert.assertNotNull(orderContext.getFdaOrderTotal(), "FDA order total was not captured from the cart page");

        this.cartPage = cartPage;
    }

    private void completeCheckoutAndPlaceOrder() {
        // Calling FDA CartPage method to proceed to the shipping/payment checkout
        FdaShippingPage shippingPage = cartPage.proceedToCheckout();
        Assert.assertTrue(shippingPage.isDisplayed(), "FDA shipping step was not displayed");

        // Calling FDA ShippingPage method to move to the payment step
        FdaPaymentPage paymentPage = shippingPage.clickSiguiente();
        Assert.assertTrue(paymentPage.isDisplayed(), "FDA payment step was not displayed");

        // Calling FDA PaymentPage method to select the credit/debit card payment option
        paymentPage.selectCreditDebitCardPayment();
        // Calling FDA PaymentPage method to enter card number, expiration date and security code
        paymentPage.enterCardDetails(config.getFdaCardNumber(), config.getFdaCardExpiry(), config.getFdaCardCvv());

        String placeOrderButtonText = paymentPage.getPlaceOrderButtonText();
        String expectedTotalDigits = orderContext.getFdaOrderTotal().toPlainString();
        Assert.assertTrue(placeOrderButtonText.contains(expectedTotalDigits)
                        || placeOrderButtonText.replaceAll("[^0-9.]", "").contains(expectedTotalDigits),
                "'Completar pago' button did not reflect the expected order total for 2 quantities. Button text: " + placeOrderButtonText);

        // Calling FDA PaymentPage method to complete the payment and place the order
        FdaOrderSuccessPage successPage = paymentPage.completarPago();
        Assert.assertTrue(successPage.isDisplayed(), "FDA order success page was not displayed");

        // Calling FDA OrderSuccessPage method to capture the generated Order ID
        String orderId = successPage.captureOrderId();
        Assert.assertNotNull(orderId, "FDA order id was not generated");
        orderContext.setOrderId(orderId);
    }

    private void verifyOrderHistory() {
        // Calling FDA HomePage method to open the account dropdown
        fdaHomePage.openMyAccountMenu();
        // Calling FDA HomePage method to open "Mis pedidos"
        FdaOrderHistoryPage orderHistoryPage = fdaHomePage.openMyOrders();

        Assert.assertTrue(orderHistoryPage.isDisplayed(), "FDA order history page was not displayed");
        Assert.assertTrue(orderHistoryPage.isOrderPresent(orderContext.getOrderId()),
                "FDA order " + orderContext.getOrderId() + " was not found in order history");
        // Same live-confirmed status as TC_FBS_001/TC_FBS_002 (fda.order.initial.status) - a
        // freshly placed order shows "Pendiente" here by default, not "Creada" as stated in the
        // test case text.
        String expectedInitialStatus = config.getFdaOrderInitialStatus();
        Assert.assertEquals(orderHistoryPage.getOrderStatus(orderContext.getOrderId()), expectedInitialStatus,
                "FDA order status was not '" + expectedInitialStatus + "'");
    }

    // ------------------------------------------------------------------
    // Mirakl
    // ------------------------------------------------------------------

    /** Opens Mirakl in a new tab in the same WebDriver session, per test-case requirement. */
    private void openMiraklInNewTab() {
        getDriver().switchTo().newWindow(WindowType.TAB);
        miraklWindowHandle = getDriver().getWindowHandle();
        getDriver().get(config.getMiraklBaseUrl());
    }

    private void switchToFdaTab() {
        getDriver().switchTo().window(fdaWindowHandle);
    }

    private void switchToMiraklTab() {
        getDriver().switchTo().window(miraklWindowHandle);
    }

    private void loginToMirakl() {
        MiraklLoginPage loginPage = new MiraklLoginPage(getDriver());

        // With a persistent Chrome profile (chrome.user.data.dir), a previous run's authenticated
        // Mirakl session can still be active, landing directly on the dashboard instead of the
        // login form. The login form is served from the same origin as the operator front office,
        // so a URL-prefix check can't tell the two apart - check for the form itself instead.
        if (!loginPage.isLoginFormPresent(Duration.ofSeconds(10))) {
            log.info("Mirakl session already authenticated from a previous run; skipping login");
            return;
        }

        Assert.assertTrue(loginPage.isDisplayed(), "Mirakl login page was not displayed");
        // Calling Mirakl LoginPage method to log in with username and password
        loginPage.login(config.getMiraklUsername(), config.getMiraklPassword());
        loginPage.waitForPostLoginNavigation(config.getMiraklBaseUrl(), Duration.ofSeconds(20));

        // Mirakl's Auth0 login can present an email MFA challenge; this framework has no email
        // integration to read the code, so it is entered manually in the visible browser window.
        if (loginPage.isMfaChallengeDisplayed()) {
            loginPage.waitForManualMfaCompletion(Duration.ofMinutes(5));
        }

        loginPage.waitForRedirectToOperatorFrontOffice(config.getMiraklBaseUrl());
    }

    private void verifyAndAcceptMiraklOrder() {
        MiraklOrdersPage ordersPage = new MiraklOrdersPage(getDriver());

        // Calling Mirakl OrdersPage method to open the "Orders" menu
        // The Orders menu can lag briefly right after login while the dashboard finishes loading;
        // poll for clickability instead of a blind fixed sleep (explicitly required by the test case).
        ordersPage.openOrdersMenu(Duration.ofSeconds(60));
        // Calling Mirakl OrdersPage method to open "All orders"
        ordersPage.openAllOrders();

        Duration syncTimeout = Duration.ofSeconds(config.getMiraklSyncTimeoutSeconds());
        Duration syncPollInterval = Duration.ofSeconds(config.getMiraklSyncPollIntervalSeconds());
        // Calling Mirakl OrdersPage method to search for the Order ID and wait for FDA -> Mirakl sync
        ordersPage.waitForOrderToAppear(orderContext.getOrderId(), syncTimeout, syncPollInterval);

        Assert.assertTrue(ordersPage.isOrderDisplayed(orderContext.getOrderId()),
                "Mirakl order " + orderContext.getOrderId() + " was not found");
        Assert.assertEquals(ordersPage.getOrderStatus(orderContext.getOrderId()), "Pending acceptance",
                "Mirakl order status was not 'Pending acceptance'");

        // Calling Mirakl OrdersPage method to open the Order Details page
        miraklOrderDetailsPage = ordersPage.openOrder(orderContext.getOrderId());

        BigDecimal miraklTotal = miraklOrderDetailsPage.getOrderTotal();
        orderContext.setMiraklOrderTotal(miraklTotal);
        Assert.assertEquals(miraklTotal.stripTrailingZeros(), orderContext.getFdaOrderTotal().stripTrailingZeros(),
                "Mirakl order total (" + miraklTotal + ") did not match FDA order total (" + orderContext.getFdaOrderTotal() + ")");

        // Calling Mirakl OrderDetailsPage method to accept the order
        miraklOrderDetailsPage.acceptOrder();
        miraklOrderDetailsPage.waitForStatus("Awaiting shipment", syncTimeout);
        Assert.assertTrue(miraklOrderDetailsPage.getStatus().equalsIgnoreCase("Awaiting shipment"),
                "Mirakl order status did not change to 'Awaiting shipment' after accepting");
    }

    private void verifyMiraklMoreActionsOptions() {
        // Calling Mirakl OrderDetailsPage method to open the "More actions" dropdown
        miraklOrderDetailsPage.openMoreActions();
        List<String> missing = miraklOrderDetailsPage.getMissingMoreActionsOptions();
        Assert.assertTrue(missing.isEmpty(), "Missing Mirakl 'More actions' options: " + missing);
    }

    private void uploadInvoice() {
        // Calling Mirakl OrderDetailsPage method to open the Documents page
        MiraklDocumentsPage documentsPage = miraklOrderDetailsPage.openDocuments();

        Assert.assertTrue(documentsPage.isAccountingDocumentsSectionDisplayed(), "Accounting Documents section was not displayed");
        Assert.assertTrue(documentsPage.isAddButtonDisplayed(), "'Add' button was not displayed in Accounting Documents");

        // Calling Mirakl DocumentsPage method to open the "Upload an order document" popup
        documentsPage.clickAdd();
        Assert.assertTrue(documentsPage.isUploadPopupDisplayed(), "'Upload an order document' popup was not displayed");

        // Calling Mirakl DocumentsPage method to select the "Invoice" document type
        documentsPage.selectDocumentType("Invoice");
        // Calling Mirakl DocumentsPage method to upload the invoice file
        documentsPage.uploadFile(config.getInvoiceFilePath());
        // Calling Mirakl DocumentsPage method to confirm the upload
        documentsPage.clickConfirm();

        String confirmation = documentsPage.waitForUploadConfirmation();
        Assert.assertTrue(confirmation.contains("The document has been uploaded"),
                "Upload confirmation message was not displayed. Got: " + confirmation);

        miraklOrderDetailsPage = documentsPage.backToOrder();
    }

    private void addTrackingInformation() {
        // Calling Mirakl OrderDetailsPage method to open "Add tracking information"
        MiraklTrackingPage trackingPage = miraklOrderDetailsPage.openAddTrackingInformation();
        Assert.assertTrue(trackingPage.isPopupDisplayed(), "Add tracking information popup was not displayed");

        List<String> carriers = trackingPage.getAvailableCarriers();
        Assert.assertFalse(carriers.isEmpty(), "No carriers were listed in the tracking popup");

        String carrier = config.getTrackingCarrier();
        // Calling Mirakl TrackingPage method to select the carrier
        trackingPage.selectCarrier(carrier);

        String trackingNumber = RandomDataUtils.generateTrackingNumber();
        Assert.assertEquals(trackingNumber.length(), 8, "Generated tracking number was not exactly 8 digits");
        orderContext.setTrackingNumber(trackingNumber);

        // Calling Mirakl TrackingPage method to enter the tracking number
        trackingPage.enterTrackingNumber(trackingNumber);
        // Calling Mirakl TrackingPage method to add the tracking information
        trackingPage.clickAdd();

        Assert.assertTrue(trackingPage.isCarrierDisplayed(carrier), "Carrier was not displayed as " + carrier);
        Assert.assertTrue(trackingPage.isTrackingNumberDisplayed(trackingNumber),
                "Tracking number " + trackingNumber + " was not displayed correctly");
    }

    private void markOrderAsShipped() {
        // Calling Mirakl OrderDetailsPage method to mark the order as shipped
        miraklOrderDetailsPage.markAsShipped();
        Duration timeout = Duration.ofSeconds(config.getMiraklSyncTimeoutSeconds());
        miraklOrderDetailsPage.waitForStatus("Shipped", timeout);
        Assert.assertTrue(miraklOrderDetailsPage.getStatus().equalsIgnoreCase("Shipped"),
                "Mirakl order status did not change from 'Awaiting shipment' to 'Shipped'");
    }

    private void markOrderAsReceived() {
        // Calling Mirakl OrderDetailsPage method to open the "More actions" dropdown
        miraklOrderDetailsPage.openMoreActions();
        List<String> missing = miraklOrderDetailsPage.getMissingMoreActionsOptions();
        Assert.assertTrue(missing.isEmpty(), "Missing Mirakl 'More actions' options before Custom field: " + missing);

        // Calling Mirakl OrderDetailsPage method to open "Custom field"
        miraklOrderDetailsPage.openCustomField();
        Assert.assertTrue(MiraklOrderDetailsPage.CUSTOM_FIELD_OPTIONS.contains("Entregado"),
                "'Entregado' is not one of the known Custom field options");

        // Calling Mirakl OrderDetailsPage method to select the "Entregado" custom field option
        miraklOrderDetailsPage.selectCustomFieldOption("Entregado");
        miraklOrderDetailsPage.selectEditAdditionalInformationValue("Yes");
        miraklOrderDetailsPage.confirmEditAdditionalInformation();
        // Calling Mirakl OrderDetailsPage method to mark the order as received
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
        // Calling Kibo AuthService to generate the access token
        KiboAuthService authService = new KiboAuthService(config.getKiboAuthUrl());
        String accessToken = authService.generateAccessToken(config.getKiboClientId(), config.getKiboClientSecret());
        Assert.assertNotNull(accessToken, "Kibo access token was not generated");
        orderContext.setKiboAccessToken(accessToken);
    }

    private void getKiboOrderDetails() {
        // Calling Kibo OrdersService to find the Kibo order id matching the FDA order id
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
        // Calling Kibo ShipmentService to fetch the shipment details and delivery type
        KiboShipmentService shipmentService = new KiboShipmentService(config.getKiboShipmentsUrl());
        String deliveryType = shipmentService.getDeliveryType(
                orderContext.getKiboAccessToken(), orderContext.getKiboOrderId(), config.getKiboShipmentDeliveryTypeJsonPath());

        orderContext.setDeliveryType(deliveryType);
        Assert.assertEquals(deliveryType, "FBS", "Kibo shipment delivery type was not 'FBS'");
    }
}
