package com.fda.automation.tests;

import com.fda.automation.base.BaseTest;
import com.fda.automation.config.ConfigManager;
import com.fda.automation.pages.HomePage;
import com.fda.automation.pages.MarketplaceLandingPage;
import com.fda.automation.pages.OutlookInboxPage;
import com.fda.automation.pages.OutlookLoginPage;
import com.fda.automation.pages.SellerRegistrationPage;
import com.fda.automation.utils.SellerContextStore;
import com.fda.automation.utils.TestDataGenerator;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static com.fda.automation.pages.SellerRegistrationPage.*;

/**
 * Stage 1 of TC_SOB_E2E_001 / TC_SOB_001 steps 1-50: submit the seller onboarding wizard
 * on the FDA storefront, confirm the success screen, then confirm the registration
 * confirmation email arrives in Outlook.
 *
 * Run against the FDA staging site (not the demo base.url in config.properties):
 *   mvn test -DsuiteXmlFile=src/test/resources/testng_seller_onboarding.xml \
 *            -Dbase.url=https://mcstaging.fahorro.com
 *
 * The Outlook check (testSellerReceivesRegistrationConfirmationEmail) requires
 * OUTLOOK_USERNAME / OUTLOOK_PASSWORD environment variables for the mailbox that receives
 * mail for the seller email used during registration (TestDataGenerator.uniqueEmail uses
 * plus-addressing under one base mailbox, e.g. fda.automation+123@kognivera.com routes to
 * fda.automation@kognivera.com) - never pass credentials as -D system properties or commit
 * them to config.properties.
 */
public class SellerOnboardingTest extends BaseTest {

    @Test(groups = {"smoke"}, description = "Stage 1: seller completes the onboarding wizard with valid data")
    public void testSellerCanSubmitOnboardingWizard() {
        Map<String, String> data = buildValidSellerData();

        HomePage homePage = new HomePage(getDriver()).open();
        MarketplaceLandingPage landingPage = homePage.goToSellerLanding();
        SellerRegistrationPage registrationPage = landingPage.clickComenzarAVender();

        registrationPage.completeRegistration(data);

        Assert.assertTrue(registrationPage.isRegistrationSuccessful(),
                "Expected a success confirmation after submitting the onboarding wizard");
        log.info("Registration confirmation: {}", registrationPage.getConfirmationText());

        SellerContextStore.save(data.get(KEY_CORREO), data.get(KEY_NOMBRE_COMERCIAL));
        log.info("Persisted seller email and trade name to target/seller-context.properties for downstream stages");
    }

    @Test(groups = {"smoke"}, dependsOnMethods = "testSellerCanSubmitOnboardingWizard",
            description = "Stage 1 continued (TC_SOB_001 steps 41-50): seller receives the registration confirmation email in Outlook")
    public void testSellerReceivesRegistrationConfirmationEmail() {
        String outlookUsername = ConfigManager.getInstance().getOutlookUsername();
        String outlookPassword = ConfigManager.getInstance().getOutlookPassword();
        Assert.assertNotNull(outlookUsername, "OUTLOOK_USERNAME environment variable must be set to run this test");
        Assert.assertNotNull(outlookPassword, "OUTLOOK_PASSWORD environment variable must be set to run this test");

        Properties sellerContext = SellerContextStore.load();
        String sellerEmail = sellerContext.getProperty(SellerContextStore.KEY_EMAIL);

        OutlookInboxPage inbox = new OutlookLoginPage(getDriver()).open().login(outlookUsername, outlookPassword);
        inbox.openOtherTab();

        Assert.assertTrue(inbox.hasEmailContaining("FDA"),
                "Expected an FDA registration confirmation email in the Other tab for seller " + sellerEmail);
    }

    private Map<String, String> buildValidSellerData() {
        String email = TestDataGenerator.uniqueEmail("fda.automation+", "kognivera.com");
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
        data.put(KEY_RFC, TestDataGenerator.uniqueRfc("ABC"));
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
