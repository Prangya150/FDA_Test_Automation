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
 * TC_FBS_006 - Verify that a user can place an order with 2 products at quantity 2 each, fulfilled
 * by two different 3P (FBS) sellers, across FDA (Magento storefront), Mirakl (marketplace
 * operator) and Kibo Commerce (fulfillment/OMS APIs).
 *
 * Combines the quantity-stepper flow from TC_FBS_004_Test with the two-seller Mirakl/Kibo handling
 * from TC_FBS_005_Test: a two-seller checkout syncs into Mirakl as *two* separate suborders (one
 * per seller, referenced as the FDA order number plus an incrementing WEB-A/WEB-B suffix - see
 * {@link MiraklOrdersPage#findSuborderReferences}), each processed fully (accept, then the entire
 * documents/tracking/ship/receive sequence) before moving on to the next - WEB-A end-to-end, then
 * WEB-B - rather than accepting both up front. Kibo stays a *single* shipment for the whole order,
 * with one line item per seller (see {@link KiboShipmentService#getAllDeliveryTypes}), so the
 * Mirakl-side loop and the Kibo-side delivery-type check are independent; the Mirakl fulfillment
 * loop runs to completion regardless of what the Kibo check finds. The manual test case's own 76
 * steps describe only a single Mirakl order/shipment (matching TC_FBS_001-004), so this test adapts
 * that same sequence to loop over both suborders instead of assuming there is exactly one.
 *
 * Kept as a single @Test method for the same reason as the earlier FBS tests: BaseTest provisions
 * one WebDriver per @BeforeMethod/@AfterMethod pair, so splitting this into independent @Test
 * methods would each start a fresh, logged-out browser and lose the FDA session/cart/order state
 * that later steps depend on.
 */
public class TC_FBS_006_Test extends BaseTest {

    private final ConfigManager config = ConfigManager.getInstance();
    private final OrderContext orderContext = new OrderContext();

    private FdaHomePage fdaHomePage;
    private FdaCartPage cartPage;
    private String fdaWindowHandle;
    private String miraklWindowHandle;

    private String firstProductName;
    private String secondProductName;
    private List<String> miraklSuborderReferences;

    @Test(groups = {"regression", "fbs"},
            description = "Verify that a user can place 2 products with 2 quantities each through two different 3P (FBS) sellers")
    public void verifyFbsOrderWithTwoProductsAndTwoQuantitiesFromTwoSellers() {
        // Both applications are authenticated upfront, before any checkout steps run, so a
        // manual MFA prompt for Mirakl (if shown) is handled right away rather than mid-flow.
        loginToFDA();
        openMiraklInNewTab();
        loginToMirakl();
        switchToFdaTab();

        searchAndAddFirstProductToCart();
        searchAndAddSecondProductToCart();
        verifyCartContents();
        completeCheckoutAndPlaceOrder();
        verifyOrderHistory();

        switchToMiraklTab();
        reauthenticateMiraklIfSessionExpired();
        processMiraklSuborders();

        authenticateWithKibo();
        getKiboOrderDetails();
        verifyShipmentDeliveryTypes();
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

    private void searchAndAddFirstProductToCart() {
        // Calling FDA HomePage method to search for the first product SKU
        FdaProductDetailsPage pdp = fdaHomePage.searchProduct(config.getFbs006Product1Sku());

        Assert.assertTrue(pdp.isDisplayed(), "PDP was not displayed for SKU " + config.getFbs006Product1Sku());
        Assert.assertTrue(pdp.isAddToCartEnabled(), "'Agregar al carrito' button was not enabled for the first product");
        Assert.assertEquals(pdp.getQuantity(), "1", "Default PDP quantity was not 1 for the first product");

        // Calling FDA ProductDetailsPage method to increase the first product's quantity from 1 to 2
        pdp.ensureQuantity(2);
        Assert.assertEquals(pdp.getQuantity(), "2", "First product PDP quantity was not 2 after increasing it");

        firstProductName = pdp.getProductName();

        // Calling FDA ProductDetailsPage method to add the first product (at quantity 2) to the cart
        pdp.addToCart();
    }

    private void searchAndAddSecondProductToCart() {
        // Calling FDA HomePage method to search for the second product SKU
        FdaProductDetailsPage pdp = fdaHomePage.searchProduct(config.getFbs006Product2Sku());

        Assert.assertTrue(pdp.isDisplayed(), "PDP was not displayed for SKU " + config.getFbs006Product2Sku());
        Assert.assertTrue(pdp.isAddToCartEnabled(), "'Agregar al carrito' button was not enabled for the second product");
        Assert.assertEquals(pdp.getQuantity(), "1", "Default PDP quantity was not 1 for the second product");

        // Calling FDA ProductDetailsPage method to increase the second product's quantity from 1 to 2
        pdp.ensureQuantity(2);
        Assert.assertEquals(pdp.getQuantity(), "2", "Second product PDP quantity was not 2 after increasing it");

        secondProductName = pdp.getProductName();
        // Guards against the search silently failing to navigate away from the first product's PDP
        // instead of a confusing downstream cart-total/quantity mismatch.
        Assert.assertNotEquals(secondProductName, firstProductName,
                "Second product search landed on the same PDP as the first product ('" + firstProductName + "')");

        // Calling FDA ProductDetailsPage method to add the second product (at quantity 2) to the cart
        pdp.addToCart();

        // Calling FDA ProductDetailsPage method to open the cart page
        cartPage = pdp.openMiCarrito();
    }

    private void verifyCartContents() {
        Assert.assertTrue(cartPage.isDisplayed(), "Cart page was not displayed");

        Assert.assertTrue(cartPage.isProductDisplayed(firstProductName),
                "First product '" + firstProductName + "' was not displayed on the Cart page");
        Assert.assertTrue(cartPage.isProductDisplayed(secondProductName),
                "Second product '" + secondProductName + "' was not displayed on the Cart page");

        Assert.assertEquals(cartPage.getQuantityForProduct(firstProductName), "2",
                "First product cart quantity was not 2");
        Assert.assertEquals(cartPage.getQuantityForProduct(secondProductName), "2",
                "Second product cart quantity was not 2");

        orderContext.setFdaOrderTotal(cartPage.getCartTotal());
        Assert.assertNotNull(orderContext.getFdaOrderTotal(), "FDA order total was not captured from the cart page");
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
                "'Completar pago' button did not reflect the expected order total for both products at quantity 2 each. Button text: " + placeOrderButtonText);

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
        performMiraklLogin(loginPage);
    }

    /**
     * CONFIRMED live on 2026-09-01 (TC_FBS_006): the several minutes the FDA checkout flow takes on
     * the other tab is long enough for the Mirakl Auth0 session to expire in the background,
     * silently dropping this tab back to the login form by the time Mirakl-side steps resume -
     * which has no "Orders" menu at all, so {@link MiraklOrdersPage#openOrdersMenu} just times out
     * no matter how long it waits. Checks for that and replays the same login (and manual MFA, if
     * re-challenged) used by {@link #loginToMirakl()} before continuing.
     */
    private void reauthenticateMiraklIfSessionExpired() {
        MiraklLoginPage loginPage = new MiraklLoginPage(getDriver());
        if (!loginPage.isLoginFormPresent(Duration.ofSeconds(5))) {
            return;
        }

        log.info("Mirakl session expired while the FDA checkout was running; logging back in");
        performMiraklLogin(loginPage);
    }

    private void performMiraklLogin(MiraklLoginPage loginPage) {
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

    /**
     * Finds both Mirakl suborders for this FDA order (one per 3P seller) and fully processes each
     * one - accept, then the entire documents/tracking/ship/receive sequence - before moving on to
     * the next: WEB-A runs end-to-end first, then WEB-B, rather than accepting both suborders up
     * front and only then looping back over them for fulfillment.
     */
    private void processMiraklSuborders() {
        MiraklOrdersPage ordersPage = new MiraklOrdersPage(getDriver());

        // Calling Mirakl OrdersPage method to open the "Orders" menu
        // The Orders menu can lag briefly right after login while the dashboard finishes loading;
        // poll for clickability instead of a blind fixed sleep (explicitly required by the test case).
        ordersPage.openOrdersMenu(Duration.ofSeconds(60));
        // Calling Mirakl OrdersPage method to open "All orders"
        ordersPage.openAllOrders();

        Duration syncTimeout = Duration.ofSeconds(config.getMiraklSyncTimeoutSeconds());
        Duration syncPollInterval = Duration.ofSeconds(config.getMiraklSyncPollIntervalSeconds());

        // Calling Mirakl OrdersPage method to find both seller suborders for this FDA order
        // (returned in WEB-A, WEB-B, ... order)
        miraklSuborderReferences = ordersPage.findSuborderReferences(orderContext.getOrderId(), 2, syncTimeout, syncPollInterval);
        Assert.assertEquals(miraklSuborderReferences.size(), 2,
                "Expected 2 Mirakl suborders (one per seller) for FDA order " + orderContext.getOrderId());

        BigDecimal combinedMiraklTotal = BigDecimal.ZERO;
        for (String reference : miraklSuborderReferences) {
            combinedMiraklTotal = combinedMiraklTotal.add(acceptAndFulfillSuborder(ordersPage, reference, syncTimeout));
        }

        orderContext.setMiraklOrderTotal(combinedMiraklTotal);
        Assert.assertEquals(combinedMiraklTotal.stripTrailingZeros(), orderContext.getFdaOrderTotal().stripTrailingZeros(),
                "Combined Mirakl suborder totals (" + combinedMiraklTotal + ") did not match FDA order total (" + orderContext.getFdaOrderTotal() + ")");
    }

    /** Accepts one suborder and runs its full documents/tracking/ship/receive sequence, returning its total. */
    private BigDecimal acceptAndFulfillSuborder(MiraklOrdersPage ordersPage, String reference, Duration syncTimeout) {
        Assert.assertEquals(ordersPage.getOrderStatus(reference), "Pending acceptance",
                "Mirakl suborder " + reference + " status was not 'Pending acceptance'");

        // Calling Mirakl OrdersPage method to open this suborder's Order Details page
        MiraklOrderDetailsPage detailsPage = ordersPage.openOrder(reference);
        BigDecimal orderTotal = detailsPage.getOrderTotal();

        // Calling Mirakl OrderDetailsPage method to accept this suborder
        detailsPage.acceptOrder();
        detailsPage.waitForStatus("Awaiting shipment", syncTimeout);
        Assert.assertTrue(detailsPage.getStatus().equalsIgnoreCase("Awaiting shipment"),
                "Mirakl suborder " + reference + " status did not change to 'Awaiting shipment' after accepting");

        verifyMiraklMoreActionsOptions(detailsPage);
        detailsPage = uploadInvoice(detailsPage);
        addTrackingInformation(detailsPage);
        markOrderAsShipped(detailsPage);
        markOrderAsReceived(detailsPage);

        // Calling Mirakl OrdersPage method to return to the unfiltered grid before the next suborder
        ordersPage.openAllOrders();

        return orderTotal;
    }

    private void verifyMiraklMoreActionsOptions(MiraklOrderDetailsPage detailsPage) {
        // Calling Mirakl OrderDetailsPage method to open the "More actions" dropdown
        detailsPage.openMoreActions();
        List<String> missing = detailsPage.getMissingMoreActionsOptions();
        Assert.assertTrue(missing.isEmpty(), "Missing Mirakl 'More actions' options: " + missing);
    }

    private MiraklOrderDetailsPage uploadInvoice(MiraklOrderDetailsPage detailsPage) {
        // Calling Mirakl OrderDetailsPage method to open the Documents page
        MiraklDocumentsPage documentsPage = detailsPage.openDocuments();

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

        return documentsPage.backToOrder();
    }

    private void addTrackingInformation(MiraklOrderDetailsPage detailsPage) {
        // Calling Mirakl OrderDetailsPage method to open "Add tracking information"
        MiraklTrackingPage trackingPage = detailsPage.openAddTrackingInformation();
        Assert.assertTrue(trackingPage.isPopupDisplayed(), "Add tracking information popup was not displayed");

        List<String> carriers = trackingPage.getAvailableCarriers();
        Assert.assertFalse(carriers.isEmpty(), "No carriers were listed in the tracking popup");

        String carrier = config.getTrackingCarrier();
        // Calling Mirakl TrackingPage method to select the carrier
        trackingPage.selectCarrier(carrier);

        String trackingNumber = RandomDataUtils.generateTrackingNumber();
        Assert.assertEquals(trackingNumber.length(), 8, "Generated tracking number was not exactly 8 digits");

        // Calling Mirakl TrackingPage method to enter the tracking number
        trackingPage.enterTrackingNumber(trackingNumber);
        // Calling Mirakl TrackingPage method to add the tracking information
        trackingPage.clickAdd();

        Assert.assertTrue(trackingPage.isCarrierDisplayed(carrier), "Carrier was not displayed as " + carrier);
        Assert.assertTrue(trackingPage.isTrackingNumberDisplayed(trackingNumber),
                "Tracking number " + trackingNumber + " was not displayed correctly");
    }

    private void markOrderAsShipped(MiraklOrderDetailsPage detailsPage) {
        // Calling Mirakl OrderDetailsPage method to mark the order as shipped
        detailsPage.markAsShipped();
        Duration timeout = Duration.ofSeconds(config.getMiraklSyncTimeoutSeconds());
        detailsPage.waitForStatus("Shipped", timeout);
        Assert.assertTrue(detailsPage.getStatus().equalsIgnoreCase("Shipped"),
                "Mirakl order status did not change from 'Awaiting shipment' to 'Shipped'");
    }

    private void markOrderAsReceived(MiraklOrderDetailsPage detailsPage) {
        // Calling Mirakl OrderDetailsPage method to open the "More actions" dropdown
        detailsPage.openMoreActions();
        List<String> missing = detailsPage.getMissingMoreActionsOptions();
        Assert.assertTrue(missing.isEmpty(), "Missing Mirakl 'More actions' options before Custom field: " + missing);

        // Calling Mirakl OrderDetailsPage method to open "Custom field"
        detailsPage.openCustomField();
        Assert.assertTrue(MiraklOrderDetailsPage.CUSTOM_FIELD_OPTIONS.contains("Entregado"),
                "'Entregado' is not one of the known Custom field options");

        // Calling Mirakl OrderDetailsPage method to select the "Entregado" custom field option
        detailsPage.selectCustomFieldOption("Entregado");
        detailsPage.selectEditAdditionalInformationValue("Yes");
        detailsPage.confirmEditAdditionalInformation();
        // Calling Mirakl OrderDetailsPage method to mark the order as received
        detailsPage.markAsReceived();

        Duration timeout = Duration.ofSeconds(config.getMiraklSyncTimeoutSeconds());
        detailsPage.waitForStatus("Received", timeout);
        Assert.assertTrue(detailsPage.getStatus().equalsIgnoreCase("Received"),
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

    /**
     * Verifies every shipment item (one per seller) has delivery type "FBS", not just the first.
     *
     * The second seller's line item can take a few seconds to appear in Kibo after its Mirakl
     * suborder is accepted, so this polls for 2 delivery types rather than asserting on a single
     * immediate read (which intermittently only sees the first).
     */
    private void verifyShipmentDeliveryTypes() {
        // Calling Kibo ShipmentService to fetch the delivery type of every shipment item on this order
        KiboShipmentService shipmentService = new KiboShipmentService(config.getKiboShipmentsUrl());
        List<String> deliveryTypes = PollingUtils.pollUntil(
                () -> shipmentService.getAllDeliveryTypes(orderContext.getKiboAccessToken(), orderContext.getKiboOrderId()),
                types -> types.size() >= 2,
                Duration.ofSeconds(config.getKiboSyncTimeoutSeconds()),
                Duration.ofSeconds(config.getKiboSyncPollIntervalSeconds()),
                "Kibo never reported 2 delivery types (one per seller) for order " + orderContext.getKiboOrderId());

        Assert.assertEquals(deliveryTypes.size(), 2,
                "Expected 2 Kibo shipment delivery types (one per seller), found: " + deliveryTypes);
        for (String deliveryType : deliveryTypes) {
            Assert.assertEquals(deliveryType, "FBS", "A Kibo shipment delivery type was not 'FBS'. All: " + deliveryTypes);
        }
        orderContext.setDeliveryType(deliveryTypes.get(0));
    }
}
