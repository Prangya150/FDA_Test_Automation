package com.fda.automation.pages;

import com.fda.automation.base.BasePage;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Page object for the seller onboarding wizard at /marketplace/provider/form
 * (TC_SOB_001 steps 6-40 / TC_SOB_E2E_001 Stage 1).
 *
 * Locators were captured by driving the live wizard on mcstaging.fahorro.com with
 * Playwright. It's a Knockout.js/Magento UI Form component:
 * - Every field/section carries a stable data-index attribute (on the fieldset or
 *   wrapping div) and every input/select carries a real name="..." attribute -
 *   both are used here instead of the auto-generated ids the widgets also render
 *   (e.g. "ko_unique_3", bare numeric option ids), which are not stable across
 *   page loads.
 * - Dropdowns are jQuery UI "selectmenu" widgets: the underlying &lt;select&gt; is
 *   display:none, so selection is done by clicking the visible sibling
 *   ui-selectmenu-button and then the option (exposed with role="option") in the
 *   menu it opens - see chooseFromSelectMenu().
 * - Yes/No questions render as a fieldset[data-index] containing two
 *   &lt;label&gt;Si&lt;/label&gt; / &lt;label&gt;No&lt;/label&gt; elements bound to hidden
 *   radios with per-render-unique names - the label text is the only stable target.
 * - "Colonia" starts as a free-text input and is replaced by a colonySelect
 *   selectmenu once a valid "Código postal" is entered (which also auto-fills
 *   Municipio/Estado - overwritten deterministically here via BasePage.type()).
 * - "¿Cuál es el link de tu ecommerce?" only appears once ownEcommerce = Si.
 * - "Cantidad de piezas / ticket promedio / link a catálogo" are per selling-site
 *   table rows (storeInformationStep[previousSales][N][...]), one row per checked
 *   site in check order. This flow checks a single site, so index 0 is used.
 * - Category selection is a jQuery UI accordion: click the group heading (h4 text,
 *   e.g. "Marca del Ahorro") to expand it, then check the category by its real
 *   catalog id value (e.g. "17221" = Antibióticos).
 *
 * Confirmed live: a successful submission replaces the wizard with a plain
 * &lt;h2&gt;¡Listo!&lt;br&gt;Se han enviado tus datos correctamente&lt;/h2&gt; (no id/class,
 * so CONFIRMATION_MESSAGE matches on text) followed by a paragraph echoing the
 * submitted email.
 */
public class SellerRegistrationPage extends BasePage {

    // ---- Personal info ----
    public static final String KEY_NOMBRE = "nombre";
    public static final String KEY_APELLIDO = "apellido";
    public static final String KEY_CORREO = "correo";
    public static final String KEY_ROL = "rolEnLaEmpresa"; // Dueño | Director | Gerente | Colaborador
    public static final String KEY_TELEFONO = "numeroTelefonico";
    public static final String KEY_PAIS = "pais"; // visible option text, e.g. "México"

    // ---- Company info ----
    public static final String KEY_NOMBRE_COMERCIAL = "nombreComercial";
    public static final String KEY_RAZON_SOCIAL = "razonSocial";
    public static final String KEY_RFC = "rfc";
    // Empresa pública | Empresa con cotización en la bolsa | Empresa privada |
    // Organización benéfica | Particular (No tengo ninguna empresa registrada)
    public static final String KEY_TIPO_EMPRESA = "tipoEmpresa";
    public static final String KEY_CALLE = "calle";
    public static final String KEY_CODIGO_POSTAL = "codigoPostal";
    // Visible option text in the colonia dropdown revealed by the postal code, e.g. "Del Valle Centro"
    public static final String KEY_COLONIA = "colonia";
    public static final String KEY_MUNICIPIO = "municipioOAlcaldia";
    public static final String KEY_NUMERO_EXTERIOR = "numeroExterior";
    public static final String KEY_ESTADO = "estado"; // visible option text, e.g. "Ciudad de México"

    // ---- Store info ---- ("si"/"no" for yes-no fields)
    public static final String KEY_ECOMMERCE_PROPIO = "tieneEcommercePropio";
    public static final String KEY_ECOMMERCE_URL = "ecommerceUrl"; // only relevant/visible when tieneEcommercePropio = si
    public static final String KEY_MARCA_IMPI_ACTIVA = "marcaImpiActiva";
    public static final String KEY_MARCA_1 = "marca1";
    public static final String KEY_SITIO_VENTA = "sitioDeVenta"; // real checkbox value, e.g. "AMAZON"
    public static final String KEY_CATEGORIA = "categoria"; // accordion group heading, e.g. "Marca del Ahorro"
    public static final String KEY_SUBCATEGORIA_VALUE = "subcategoriaValue"; // real checkbox value, e.g. "17221" (Antibióticos)
    public static final String KEY_PIEZAS_SITIO_VENTA = "piezasSitioDeVenta";
    public static final String KEY_TICKET_PROMEDIO = "ticketPromedio";
    public static final String KEY_LINK_CATALOGO = "linkCatalogo"; // scoped to the first selected selling site
    public static final String KEY_PAQUETERIAS_TERCERAS = "trabajaConPaqueteriasTerceras";
    public static final String KEY_DESPACHO_24_48 = "puedeDespacharEn24a48hrs";
    public static final String KEY_ENVIA_INVENTARIO = "puedeEnviarInventarioAlAlmacen";

    // ---- Product info ----
    public static final String KEY_OFRECE_GARANTIA = "ofreceGarantia";
    public static final String KEY_ACEPTA_DEVOLUCIONES = "aceptaDevoluciones";
    public static final String KEY_GENERA_FACTURAS = "generaFacturasAlConsumidor";

    private static final By CONFIRMATION_MESSAGE =
            By.xpath("//h2[contains(normalize-space(.),'Se han enviado tus datos correctamente')]");

    public SellerRegistrationPage(WebDriver driver) {
        super(driver);
    }

    private By byName(String name) {
        return By.cssSelector("[name=\"" + name + "\"]");
    }

    private By selectMenuButton(String selectName) {
        return By.xpath("//select[@name=\"" + selectName + "\"]/following-sibling::span[contains(@class,'ui-selectmenu-button')]");
    }

    private By selectMenuOption(String optionText) {
        return By.xpath("//*[@role='option'][normalize-space()=\"" + optionText + "\"]");
    }

    private void chooseFromSelectMenu(String selectName, String optionText) {
        click(selectMenuButton(selectName));
        click(selectMenuOption(optionText));
    }

    private By yesNoLabel(String fieldsetDataIndex, boolean yes) {
        return By.xpath("//fieldset[@data-index=\"" + fieldsetDataIndex + "\"]//label[normalize-space()=\""
                + (yes ? "Si" : "No") + "\"]");
    }

    private void answerYesNo(String fieldsetDataIndex, String value) {
        boolean yes = "si".equalsIgnoreCase(value) || "sí".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value);
        click(yesNoLabel(fieldsetDataIndex, yes));
    }

    private By continuarButton(String stepDataIndex) {
        return By.cssSelector("div[data-index=\"" + stepDataIndex + "\"] button[data-index=\"continueButton\"]");
    }

    private By submitButton(String stepDataIndex) {
        return By.cssSelector("div[data-index=\"" + stepDataIndex + "\"] button[data-index=\"submitButton\"]");
    }

    private By stepContainer(String stepDataIndex) {
        return By.cssSelector("div[data-index=\"" + stepDataIndex + "\"]");
    }

    /**
     * All 4 steps' markup exists in the DOM simultaneously (Knockout toggles visibility),
     * so clicking "Continuar" returning doesn't mean the next step has actually rendered
     * yet. Waiting here for the next step's container to become visible avoids a race
     * where the following step's fields are queried before the transition completes.
     */
    /**
     * The staging environment has repeatedly shown transient hiccups this session (503s on
     * submit, dropped connections) with no client-side error surfaced when they hit a step
     * transition - all field values/checked-state were confirmed correct via live JS
     * inspection when this was diagnosed, so a stuck transition is treated as a transient
     * backend issue and retried rather than failed immediately.
     */
    private void clickContinuarAndWaitForStep(String currentStepDataIndex, String nextStepDataIndex) {
        final int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            click(continuarButton(currentStepDataIndex));
            try {
                waitForVisible(stepContainer(nextStepDataIndex));
                return;
            } catch (org.openqa.selenium.TimeoutException e) {
                log.warn("Step transition {} -> {} not visible after Continuar click (attempt {}/{})",
                        currentStepDataIndex, nextStepDataIndex, attempt, maxAttempts);
                if (attempt == maxAttempts) {
                    throw e;
                }
            }
        }
    }

    public void fillPersonalInfo(Map<String, String> data) {
        log.info("Filling personal info step");
        type(byName("personalInformationStep[personalInformation][name]"), data.get(KEY_NOMBRE));
        type(byName("personalInformationStep[personalInformation][lastName]"), data.get(KEY_APELLIDO));
        type(byName("personalInformationStep[personalInformation][email]"), data.get(KEY_CORREO));
        chooseFromSelectMenu("personalInformationStep[personalInformation][roleInCompany]", data.get(KEY_ROL));
        type(byName("personalInformationStep[personalInformation][phone]"), data.get(KEY_TELEFONO));
        chooseFromSelectMenu("personalInformationStep[personalInformation][country]", data.get(KEY_PAIS));
        clickContinuarAndWaitForStep("personalInformationStep", "companyInformationStep");
    }

    public void fillCompanyInfo(Map<String, String> data) {
        log.info("Filling company info step");
        type(byName("companyInformationStep[companyInformation][tradeName]"), data.get(KEY_NOMBRE_COMERCIAL));
        type(byName("companyInformationStep[companyInformation][companyName]"), data.get(KEY_RAZON_SOCIAL));
        type(byName("companyInformationStep[companyInformation][rfc]"), data.get(KEY_RFC));
        chooseFromSelectMenu("companyInformationStep[companyInformation][companyType]", data.get(KEY_TIPO_EMPRESA));
        type(byName("companyInformationStep[taxAddress][street]"), data.get(KEY_CALLE));
        // Postal code lookup auto-fills municipality/region and swaps "Colonia" for a dropdown.
        type(byName("companyInformationStep[taxAddress][postalCode]"), data.get(KEY_CODIGO_POSTAL));
        chooseFromSelectMenu("companyInformationStep[taxAddress][colonySelect]", data.get(KEY_COLONIA));
        type(byName("companyInformationStep[taxAddress][municipality]"), data.get(KEY_MUNICIPIO));
        type(byName("companyInformationStep[taxAddress][numExterior]"), data.get(KEY_NUMERO_EXTERIOR));
        chooseFromSelectMenu("companyInformationStep[taxAddress][region]", data.get(KEY_ESTADO));
        clickContinuarAndWaitForStep("companyInformationStep", "storeInformationStep");
    }

    public void fillStoreInfo(Map<String, String> data) {
        log.info("Filling store info step");
        answerYesNo("ownEcommerce", data.get(KEY_ECOMMERCE_PROPIO));
        if (data.get(KEY_ECOMMERCE_URL) != null) {
            type(byName("storeInformationStep[storeInformation][ecommerceUrl]"), data.get(KEY_ECOMMERCE_URL));
        }
        answerYesNo("trademark", data.get(KEY_MARCA_IMPI_ACTIVA));
        type(byName("storeInformationStep[storeInformation][sellingBrands][0]"), data.get(KEY_MARCA_1));

        click(By.cssSelector("fieldset[data-index='previousSellingOptions'] input[type='checkbox'][value=\""
                + data.get(KEY_SITIO_VENTA) + "\"]"));

        click(By.xpath("//fieldset[@data-index='sellingCategories']//h4[normalize-space()=\""
                + data.get(KEY_CATEGORIA) + "\"]"));
        click(By.cssSelector("fieldset[data-index='sellingCategories'] input[type='checkbox'][value=\""
                + data.get(KEY_SUBCATEGORIA_VALUE) + "\"]"));

        // One row is added per checked selling site, in check order; this flow checks a single site (index 0).
        type(byName("storeInformationStep[previousSales][0][value]"), data.get(KEY_PIEZAS_SITIO_VENTA));
        type(byName("storeInformationStep[previousSales][0][averageProfitPerMonth]"), data.get(KEY_TICKET_PROMEDIO));
        type(byName("storeInformationStep[previousSales][0][catalogLink]"), data.get(KEY_LINK_CATALOGO));

        answerYesNo("thirdpartyShipping", data.get(KEY_PAQUETERIAS_TERCERAS));
        answerYesNo("expressDelivery", data.get(KEY_DESPACHO_24_48));
        answerYesNo("warehouseShipping", data.get(KEY_ENVIA_INVENTARIO));
        clickContinuarAndWaitForStep("storeInformationStep", "productInformationStep");
    }

    public void fillProductInfoAndSubmit(Map<String, String> data) {
        log.info("Filling product info step and submitting");
        answerYesNo("guarantee", data.get(KEY_OFRECE_GARANTIA));
        answerYesNo("returns", data.get(KEY_ACEPTA_DEVOLUCIONES));
        answerYesNo("invoice", data.get(KEY_GENERA_FACTURAS));
        click(byName("productInformationStep[productInformation][acceptPolicies]"));
        clickSubmitAndWaitForConfirmation();
    }

    // Same transient-backend tolerance as clickContinuarAndWaitForStep - see its javadoc.
    private void clickSubmitAndWaitForConfirmation() {
        final int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            click(submitButton("productInformationStep"));
            try {
                waitForVisible(CONFIRMATION_MESSAGE);
                return;
            } catch (org.openqa.selenium.TimeoutException e) {
                log.warn("Success confirmation not visible after submit click (attempt {}/{})", attempt, maxAttempts);
                if (attempt == maxAttempts) {
                    throw e;
                }
            }
        }
    }

    /**
     * The staging site occasionally replaces the whole wizard with a terminal
     * "&iexcl;Oh no! No pudimos enviar la informaci&oacute;n" error screen on submit (a
     * separate failure mode from the "stuck transition" one clickSubmitAndWaitForConfirmation
     * already retries in place) - once that error screen renders, the submit button is gone
     * from the DOM, so a same-page re-click throws instead of retrying. Recover by reloading
     * the page (which resets the Knockout wizard to a blank first step) and re-filling the
     * whole thing from scratch, up to a few times.
     *
     * dataSupplier (rather than a plain Map) is called again on each retry, not reused,
     * because a failure here can mean the response was lost rather than the request - the
     * first attempt's POST may have actually reached the backend and created the seller,
     * in which case resubmitting the identical email/RFC/trade name would be rejected as
     * a duplicate on every retry, permanently masking the real cause behind the same
     * generic error (observed live: 3/3 identical-data retries all failed the same way).
     * Fresh unique values make each attempt unambiguously a new seller. Returns the data
     * map that was actually (successfully) submitted, since it may differ from the first
     * call's if a retry occurred.
     */
    public Map<String, String> completeRegistration(Supplier<Map<String, String>> dataSupplier) {
        final int maxAttempts = 3;
        Map<String, String> data = dataSupplier.get();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                fillPersonalInfo(data);
                fillCompanyInfo(data);
                fillStoreInfo(data);
                fillProductInfoAndSubmit(data);
                return data;
            } catch (org.openqa.selenium.TimeoutException e) {
                log.warn("Registration attempt {}/{} failed ({}); reloading and retrying with fresh unique data",
                        attempt, maxAttempts, e.getMessage());
                if (attempt == maxAttempts) {
                    throw e;
                }
                driver.navigate().refresh();
                data = dataSupplier.get();
            }
        }
        throw new IllegalStateException("unreachable");
    }

    public boolean isRegistrationSuccessful() {
        return isDisplayed(CONFIRMATION_MESSAGE);
    }

    public String getConfirmationText() {
        return getText(CONFIRMATION_MESSAGE);
    }
}
