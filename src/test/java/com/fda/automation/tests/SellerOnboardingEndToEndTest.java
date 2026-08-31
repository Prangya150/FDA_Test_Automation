package com.fda.automation.tests;

import com.aventstack.extentreports.Status;
import com.aventstack.extentreports.markuputils.CodeLanguage;
import com.aventstack.extentreports.markuputils.MarkupHelper;
import com.fda.automation.base.BaseTest;
import com.fda.automation.config.ConfigManager;
import com.fda.automation.pages.DocumentSubmissionPage;
import com.fda.automation.pages.HomePage;
import com.fda.automation.pages.MarketplaceLandingPage;
import com.fda.automation.pages.MiddlewareLoginPage;
import com.fda.automation.pages.MiddlewareSellerDetailPage;
import com.fda.automation.pages.MiraklLoginPage;
import com.fda.automation.pages.MiraklShopSearchPage;
import com.fda.automation.pages.OutlookInboxPage;
import com.fda.automation.pages.OutlookLoginPage;
import com.fda.automation.pages.SellerRegistrationPage;
import com.fda.automation.utils.DocumentFixtures;
import com.fda.automation.utils.ExtentReportManager;
import com.fda.automation.utils.KiboApiClient;
import com.fda.automation.utils.TestDataGenerator;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import static com.fda.automation.pages.SellerRegistrationPage.*;

/**
 * The full TC_SOB_001 seller onboarding journey, end to end, across all four systems it
 * touches: the FDA storefront, Outlook, the Mirakl middleware, the Mirakl marketplace back
 * office, and the Kibo commerce API - as ONE TestNG test method (one row in the report, not
 * nine), so it can never be mistaken for a set of independent test cases. Internally it's
 * broken into private stage methods purely for readability; every value a later stage needs
 * (seller email, trade name, document-submission link, Mirakl shop id, ...) is produced by
 * an earlier stage and passed as a plain Java method argument/return value - no disk-backed
 * context file, no shared mutable state, no separate test executions to bridge.
 *
 * Each stage is wrapped by stage(name, ...), which logs a start/pass line and, on failure,
 * logs exactly which named stage failed before letting the exception propagate - so the run
 * stops at the first failing stage (everything after it never executes) and both the console
 * log and the single TestNG failure clearly identify which stage broke.
 *
 * Run against the FDA staging site (not the demo base.url in config.properties):
 *   mvn test -DsuiteXmlFile=src/test/resources/testng_seller_onboarding.xml \
 *            -Dbase.url=https://mcstaging.fahorro.com
 *
 * Requires these environment variables (never pass credentials as -D system properties or
 * commit them to config.properties):
 *   OUTLOOK_USERNAME / OUTLOOK_PASSWORD       - mailbox that receives seller + FDA emails
 *   MIDDLEWARE_USERNAME / MIDDLEWARE_PASSWORD - Mirakl middleware operator login
 *   MIRAKL_USERNAME / MIRAKL_PASSWORD         - Mirakl marketplace back-office login
 *   KIBO_CLIENT_ID / KIBO_CLIENT_SECRET       - Kibo commerce API OAuth client credentials
 *
 * Steps 3 onward (middleware, document submission, Mirakl, Kibo) use PLACEHOLDER LOCATORS
 * that have not been verified against the live systems - see MiddlewareLoginPage's javadoc
 * for why. Steps 1-2 (FDA registration + Outlook confirmation) locators ARE live-verified -
 * see SellerRegistrationPage's and OutlookLoginPage's javadocs.
 */
public class SellerOnboardingEndToEndTest extends BaseTest {

    @Test(groups = {"smoke"}, description = "TC_SOB_001: seller onboarding end to end - "
            + "registration -> registration email -> middleware pre-approval -> pre-approval email -> "
            + "document upload -> middleware final approval -> final-approval email -> "
            + "Mirakl shop search -> Kibo validation")
    public void sellerOnboardingEndToEnd() {
        ExtentReportManager.startTest("sellerOnboardingEndToEnd", "TC_SOB_001: seller onboarding end to end - "
                + "registration -> registration email -> middleware pre-approval -> pre-approval email -> "
                + "document upload -> middleware final approval -> final-approval email -> "
                + "Mirakl shop search -> Kibo validation");

        Map<String, String> seller = stage("1. Seller Registration (FDA frontend)", this::registerSeller);
        String sellerEmail = seller.get(KEY_CORREO);
        String tradeName = seller.get(KEY_NOMBRE_COMERCIAL);
        log.info("Seller created: name={} {}, email={}, shopName={}",
                seller.get(KEY_NOMBRE), seller.get(KEY_APELLIDO), sellerEmail, tradeName);

        stage("2. Registration Successful Email", () -> verifyRegistrationEmail(sellerEmail, tradeName));

        stage("3. Middleware Pre-Approval", () -> middlewarePreApproval(sellerEmail));

        String documentLink = stage("4. Pre-Approval Email + \"Ingresa al link\"",
                () -> verifyPreApprovalEmailAndExtractLink(tradeName));

        stage("5. Upload Seller Documents", () -> uploadSellerDocuments(documentLink, sellerEmail));

        stage("6. Middleware Final Approval", () -> middlewareFinalApproval(sellerEmail));

        stage("7. Final Approval Email", () -> verifyFinalApprovalEmail(tradeName));

        String shopId = stage("8. Mirakl Marketplace Shop Search + Capture Shop ID",
                () -> searchMiraklShopAndCaptureId(tradeName));

        stage("9. Kibo Verification", () -> validateKiboResponse(shopId, tradeName));

        log.info("=== TC_SOB_001 seller onboarding completed successfully: shop={}, shopId={}, email={} ===",
                tradeName, shopId, sellerEmail);
    }

    // ---------------------------------------------------------------------------------
    // Stage 1: Seller Registration
    // ---------------------------------------------------------------------------------
    private Map<String, String> registerSeller() {
        HomePage homePage = new HomePage(getDriver()).open();
        MarketplaceLandingPage landingPage = homePage.goToSellerLanding();
        SellerRegistrationPage registrationPage = landingPage.clickComenzarAVender();

        Map<String, String> data = registrationPage.completeRegistration(this::buildValidSellerData);

        Assert.assertTrue(registrationPage.isRegistrationSuccessful(),
                "Expected a success confirmation after submitting the onboarding wizard");
        log.info("Registration successful. Confirmation: {}", registrationPage.getConfirmationText());
        return data;
    }

    // ---------------------------------------------------------------------------------
    // Stage 2: Registration Successful Email
    // ---------------------------------------------------------------------------------
    private void verifyRegistrationEmail(String sellerEmail, String tradeName) {
        String outlookUsername = requireEnv(ConfigManager.getInstance().getOutlookUsername(), "OUTLOOK_USERNAME");
        String outlookPassword = requireEnv(ConfigManager.getInstance().getOutlookPassword(), "OUTLOOK_PASSWORD");

        OutlookInboxPage inbox = new OutlookLoginPage(getDriver()).open().login(outlookUsername, outlookPassword);
        inbox.openOtherTab();

        // Real subject: "QA:¡Gracias por unirse a nosotros!" - not "FDA" (the mailbox
        // receives all QA runs' emails, mixed with pre-approval/final-approval ones that
        // arrive later in the same run, so "unirse" is what distinguishes this one). The
        // mailbox also accumulates every past run's emails with this same subject, so
        // "unirse" alone matches whichever one happens to be first in the DOM - stale, not
        // necessarily this run's - hence also requiring the (unique per run) trade name,
        // which the email body echoes back ("¡Hola <tradeName>! Gracias por registrarte...").
        Assert.assertTrue(inbox.waitForEmailContaining(Duration.ofMinutes(2), "unirse", tradeName),
                "Expected a registration confirmation email (\"Gracias por unirse\") in the Other tab for seller " + sellerEmail);
        log.info("Registration confirmation email received for {} ({})", sellerEmail, tradeName);

        inbox.openEmailContaining("unirse", tradeName);
        logEmailBody("Registration Confirmation Email", inbox.getEmailBodyText());
    }

    // ---------------------------------------------------------------------------------
    // Stage 3: Middleware Pre-Approval
    // ---------------------------------------------------------------------------------
    private void middlewarePreApproval(String sellerEmail) {
        String middlewareUsername = requireEnv(ConfigManager.getInstance().getMiddlewareUsername(), "MIDDLEWARE_USERNAME");
        String middlewarePassword = requireEnv(ConfigManager.getInstance().getMiddlewarePassword(), "MIDDLEWARE_PASSWORD");

        MiddlewareSellerDetailPage detail = new MiddlewareLoginPage(getDriver())
                .open()
                .login(middlewareUsername, middlewarePassword)
                .openSellerByEmail(sellerEmail);

        detail.reviewAndPreApprove();
        log.info("Pre-Approval completed for seller {}", sellerEmail);
    }

    // ---------------------------------------------------------------------------------
    // Stage 4: Pre-Approval Email -> "Ingresa al link"
    // ---------------------------------------------------------------------------------
    private String verifyPreApprovalEmailAndExtractLink(String tradeName) {
        String outlookUsername = requireEnv(ConfigManager.getInstance().getOutlookUsername(), "OUTLOOK_USERNAME");
        String outlookPassword = requireEnv(ConfigManager.getInstance().getOutlookPassword(), "OUTLOOK_PASSWORD");

        OutlookInboxPage inbox = new OutlookLoginPage(getDriver()).open().login(outlookUsername, outlookPassword);
        inbox.openOtherTab();

        // Real subject: "QA:¡Felicidades! Su preaprobación ha sido aceptada" - "preaprob"
        // distinguishes it from the final-approval email, which also contains "Felicidades".
        // Also require the trade name (see stage 2's comment) - without it this matched a
        // stale pre-approval email from an earlier run still sitting in the mailbox, whose
        // "Ingresa al link" pointed at an already-submitted seller, so document upload
        // landed on "¡Solicitud ya enviada!" instead of the upload form.
        // Delivery for this specific email has been observed live to take anywhere from
        // ~10s to a bit over 2 minutes (unlike the registration-ack email, which has
        // consistently arrived within ~10s) - 5 minutes gives real headroom instead of
        // relying on being lucky.
        Assert.assertTrue(inbox.waitForEmailContaining(Duration.ofMinutes(5), "preaprob", tradeName),
                "Expected a pre-approval email (\"preaprobación\") in the Other tab");
        log.info("Pre-Approval email received for {}", tradeName);
        inbox.openEmailContaining("preaprob", tradeName);
        logEmailBody("Pre-Approval Email", inbox.getEmailBodyText());

        String documentLink = inbox.extractLinkHref("Ingresa al link");
        Assert.assertNotNull(documentLink, "Expected an \"Ingresa al link\" link in the pre-approval email");
        log.info("Extracted document submission link: {}", documentLink);
        return documentLink;
    }

    // ---------------------------------------------------------------------------------
    // Stage 5: Upload Seller Documents
    // ---------------------------------------------------------------------------------
    private void uploadSellerDocuments(String documentLink, String sellerEmail) {
        Map<String, String> bankData = new HashMap<>();
        bankData.put(DocumentSubmissionPage.KEY_TIENDA_EMAIL, sellerEmail);
        bankData.put(DocumentSubmissionPage.KEY_NUMERO_CUENTA_BANCARIA, "1234567890");
        bankData.put(DocumentSubmissionPage.KEY_CLABE, "002010077777777771");
        bankData.put(DocumentSubmissionPage.KEY_NOMBRE_BANCO, "BBVA");
        bankData.put(DocumentSubmissionPage.KEY_NOMBRE_TITULAR, "Ana Garcia");

        new DocumentSubmissionPage(getDriver())
                .open(documentLink)
                .submitAllDocuments(bankData,
                        DocumentFixtures.pathTo(DocumentFixtures.IDENTIDAD),
                        DocumentFixtures.pathTo(DocumentFixtures.COMPROBANTE_DOMICILIO),
                        DocumentFixtures.pathTo(DocumentFixtures.CONSTANCIA_FISCAL),
                        DocumentFixtures.pathTo(DocumentFixtures.ACTA_CONSTITUTIVA),
                        DocumentFixtures.pathTo(DocumentFixtures.PODER_NOTARIAL),
                        DocumentFixtures.pathTo(DocumentFixtures.OPINION_CUMPLIMIENTO_SAT),
                        DocumentFixtures.pathTo(DocumentFixtures.LICENCIA_COMERCIAL),
                        DocumentFixtures.pathTo(DocumentFixtures.ESTADO_CUENTA));

        log.info("Document upload completed (8 documents + bank details) for {}", sellerEmail);
    }

    // ---------------------------------------------------------------------------------
    // Stage 6: Middleware Final Approval
    // ---------------------------------------------------------------------------------
    private void middlewareFinalApproval(String sellerEmail) {
        String middlewareUsername = requireEnv(ConfigManager.getInstance().getMiddlewareUsername(), "MIDDLEWARE_USERNAME");
        String middlewarePassword = requireEnv(ConfigManager.getInstance().getMiddlewarePassword(), "MIDDLEWARE_PASSWORD");

        MiddlewareSellerDetailPage detail = new MiddlewareLoginPage(getDriver())
                .open()
                .login(middlewareUsername, middlewarePassword)
                .openSellerByEmail(sellerEmail);

        detail.downloadDocumentsAndApprove();
        log.info("Final Approval completed for seller {}", sellerEmail);
    }

    // ---------------------------------------------------------------------------------
    // Stage 7: Final Approval Email
    // ---------------------------------------------------------------------------------
    private void verifyFinalApprovalEmail(String tradeName) {
        String outlookUsername = requireEnv(ConfigManager.getInstance().getOutlookUsername(), "OUTLOOK_USERNAME");
        String outlookPassword = requireEnv(ConfigManager.getInstance().getOutlookPassword(), "OUTLOOK_PASSWORD");

        OutlookInboxPage inbox = new OutlookLoginPage(getDriver()).open().login(outlookUsername, outlookPassword);
        inbox.openOtherTab();

        // "Felicidades" alone also matches the pre-approval email ("Su preaprobación ha
        // sido aceptada"), so require "incorporación" + "exitosa" too to pin down the
        // final-approval one specifically - and the trade name (see stage 2's comment) to
        // pin it to this run rather than a stale one from an earlier run. Same 5-minute
        // headroom as stage 4's wait - see its comment for why.
        Assert.assertTrue(inbox.waitForEmailContaining(Duration.ofMinutes(5), "incorporación", "exitosa", tradeName),
                "Expected a \"QA: ¡Felicidades! Su incorporación fue exitosa\" email in the Other tab");
        log.info("Final Approval email received for {}", tradeName);

        inbox.openEmailContaining("incorporación", "exitosa", tradeName);
        logEmailBody("Final Approval Email", inbox.getEmailBodyText());
    }

    // ---------------------------------------------------------------------------------
    // Stage 8: Mirakl Marketplace Shop Search + Capture Shop ID
    // ---------------------------------------------------------------------------------
    private String searchMiraklShopAndCaptureId(String tradeName) {
        String miraklUsername = requireEnv(ConfigManager.getInstance().getMiraklUsername(), "MIRAKL_USERNAME");
        String miraklPassword = requireEnv(ConfigManager.getInstance().getMiraklPassword(), "MIRAKL_PASSWORD");
        String outlookUsername = requireEnv(ConfigManager.getInstance().getOutlookUsername(), "OUTLOOK_USERNAME");
        String outlookPassword = requireEnv(ConfigManager.getInstance().getOutlookPassword(), "OUTLOOK_PASSWORD");

        // Mirakl's login always challenges with an emailed one-time code in this
        // environment (no persisted "remember this device" cookie survives a fresh
        // Selenium profile), so this login needs Outlook credentials too - see
        // MiraklLoginPage's javadoc.
        MiraklShopSearchPage shopSearch = new MiraklLoginPage(getDriver()).open()
                .login(miraklUsername, miraklPassword, outlookUsername, outlookPassword);
        shopSearch.openAllShopAccounts();

        // The shop was only just approved in stages 6/7, and has been observed live to not
        // be immediately searchable - poll rather than searching once.
        Assert.assertTrue(shopSearch.waitForShopInResults(tradeName, Duration.ofMinutes(5)),
                "Expected shop \"" + tradeName + "\" to appear in Mirakl's Todas las cuentas tienda search results");
        log.info("Mirakl Shop found for trade name {}", tradeName);

        String shopId = shopSearch.openShopAndCaptureId(tradeName);
        Assert.assertTrue(shopId != null && !shopId.isBlank(),
                "Expected a non-empty Mirakl shop id to be captured for shop \"" + tradeName + "\"");
        log.info("Mirakl Shop ID captured: {}", shopId);
        return shopId;
    }

    // ---------------------------------------------------------------------------------
    // Stage 9: Kibo Verification
    // ---------------------------------------------------------------------------------
    private void validateKiboResponse(String shopId, String tradeName) {
        // The manual test case calls for an 8-minute wait here for the shop just opened
        // in stage 8 to propagate through to Kibo before checking it.
        sleepMinutes(8);

        // Dynamically fed from stage 8's captured Mirakl shop id (see KiboApiClient's
        // javadoc for the assumption this rests on). -Dkibo.locationId remains available
        // as a manual override for debugging a single run without redoing stages 1-8.
        String locationId = System.getProperty("kibo.locationId", shopId);

        log.info("Kibo request executing for location {} (shop {})", locationId, tradeName);
        KiboApiClient kibo = new KiboApiClient();
        String token = kibo.fetchAccessToken();
        Response response = kibo.getLocationRetryingOn401(token, locationId);

        String body = response.asString();
        ExtentReportManager.log(Status.INFO, "Kibo API status: " + response.statusCode());
        ExtentReportManager.getTest().log(Status.INFO, MarkupHelper.createCodeBlock(
                body == null ? "" : body, CodeLanguage.JSON));

        Assert.assertEquals(response.statusCode(), 200,
                "Expected Kibo location " + locationId + " to respond 200: " + body);
        Assert.assertTrue(body != null && !body.isBlank(), "Expected a non-empty Kibo response body");

        // Soft check only - the Kibo response schema/field names for matching a seller by
        // name are unconfirmed, so this is logged for visibility rather than asserted.
        boolean nameEchoedBack = tradeName != null && body.toLowerCase().contains(tradeName.toLowerCase());
        log.info("Kibo response validated (200, non-empty body) for location {}; trade name \"{}\" {} in response body",
                locationId, tradeName, nameEchoedBack ? "found" : "not found");
        log.info("Kibo location {} response: {}", locationId, body);
    }

    // ---------------------------------------------------------------------------------
    // Stage orchestration helper - logs stage start/pass/fail so a single-method test
    // still reports exactly which named stage broke, and stops the run at that point.
    // ---------------------------------------------------------------------------------
    private <T> T stage(String name, Supplier<T> action) {
        log.info("==== STAGE START: {} ====", name);
        ExtentReportManager.log(Status.INFO, "<b>" + name + "</b>");
        try {
            T result = action.get();
            log.info("==== STAGE PASSED: {} ====", name);
            ExtentReportManager.log(Status.PASS, name + " passed");
            return result;
        } catch (RuntimeException | AssertionError e) {
            log.error("==== STAGE FAILED: {} -- {} ====", name, e.getMessage());
            ExtentReportManager.log(Status.FAIL, name + " failed: " + e.getMessage());
            throw e;
        }
    }

    private void stage(String name, Runnable action) {
        stage(name, () -> {
            action.run();
            return null;
        });
    }

    /** Logs an opened email's body into the ExtentReport as a code block, for visibility. */
    private void logEmailBody(String label, String body) {
        log.info("{}: {}", label, body);
        ExtentReportManager.getTest().log(Status.INFO, "<b>" + label + "</b>");
        ExtentReportManager.getTest().log(Status.INFO, MarkupHelper.createCodeBlock(
                body == null || body.isBlank() ? "(no body captured)" : body));
    }

    private String requireEnv(String value, String envVarName) {
        Assert.assertNotNull(value, envVarName + " environment variable must be set to run this test");
        return value;
    }

    private void sleepMinutes(int minutes) {
        try {
            Thread.sleep(Duration.ofMinutes(minutes).toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private Map<String, String> buildValidSellerData() {
        String email = TestDataGenerator.uniqueEmail("psamal+full", "kognivera.com");
        String tradeName = TestDataGenerator.uniqueTradeName("Bienestar Kognivera");

        Map<String, String> data = new HashMap<>();
        // Personal info
        data.put(KEY_NOMBRE, "Ana");
        data.put(KEY_APELLIDO, "Garcia");
        data.put(KEY_CORREO, email);
        data.put(KEY_ROL, "Dueño");
        data.put(KEY_TELEFONO, "5512345678");
        data.put(KEY_PAIS, "México");
        // Company info
        data.put(KEY_NOMBRE_COMERCIAL, tradeName);
        data.put(KEY_RAZON_SOCIAL, tradeName + " SA de CV");
        data.put(KEY_RFC, TestDataGenerator.uniqueRfc("GODE"));
        data.put(KEY_TIPO_EMPRESA, "Empresa privada");
        data.put(KEY_CALLE, "Av. Insurgentes Sur");
        data.put(KEY_CODIGO_POSTAL, "03100");
        data.put(KEY_COLONIA, "Del Valle Centro");
        data.put(KEY_MUNICIPIO, "Benito Juárez");
        data.put(KEY_NUMERO_EXTERIOR, "123");
        data.put(KEY_ESTADO, "Ciudad de México");
        // Store info
        data.put(KEY_ECOMMERCE_PROPIO, "si");
        data.put(KEY_ECOMMERCE_URL, "https://example.com");
        data.put(KEY_MARCA_IMPI_ACTIVA, "si");
        data.put(KEY_MARCA_1, tradeName);
        data.put(KEY_SITIO_VENTA, "AMAZON");
        data.put(KEY_CATEGORIA, "Marca del Ahorro");
        data.put(KEY_SUBCATEGORIA_VALUE, "17221"); // Antibióticos
        data.put(KEY_PIEZAS_SITIO_VENTA, "100");
        data.put(KEY_TICKET_PROMEDIO, "250");
        data.put(KEY_LINK_CATALOGO, "https://example.com/catalogo");
        data.put(KEY_PAQUETERIAS_TERCERAS, "si");
        data.put(KEY_DESPACHO_24_48, "si");
        data.put(KEY_ENVIA_INVENTARIO, "si");
        // Product info
        data.put(KEY_OFRECE_GARANTIA, "si");
        data.put(KEY_ACEPTA_DEVOLUCIONES, "si");
        data.put(KEY_GENERA_FACTURAS, "si");
        return data;
    }
}
