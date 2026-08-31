package com.fda.automation.pages;

import com.fda.automation.base.BasePage;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

import java.util.Map;

/**
 * Seller-facing document submission form reached via the "Ingresa al link" email
 * (TC_SOB_001 steps 70-85): 8 file uploads, "Email de la tienda", 4 bank fields, a
 * terms-and-conditions checkbox, and "Enviar" with a confirm dialog.
 *
 * PLACEHOLDER LOCATORS: not verified live - this page is only reachable via a real
 * pre-approval email link, which requires the full chain (registration -> middleware
 * pre-approval -> email) to have actually run first; see MiddlewareLoginPage's javadoc
 * for the general disclosure. File inputs are targeted directly (the real, usually
 * visually-hidden &lt;input type="file"&gt;, not the "Subir Documento" trigger button) since
 * WebElement.sendKeys(path) types the path straight into that input and never opens an
 * OS file-picker dialog - the standard Selenium technique, works headless.
 */
public class DocumentSubmissionPage extends BasePage {

    public static final String KEY_TIENDA_EMAIL = "tiendaEmail";
    public static final String KEY_NUMERO_CUENTA_BANCARIA = "numeroCuentaBancaria";
    public static final String KEY_CLABE = "clabe";
    public static final String KEY_NOMBRE_BANCO = "nombreBanco";
    public static final String KEY_NOMBRE_TITULAR = "nombreTitular";

    private static final By TERMS_CHECKBOX =
            By.xpath("//input[@type='checkbox'][following-sibling::*[contains(.,'érminos') or contains(.,'ondiciones')] or parent::label[contains(.,'érminos')]]");
    private static final By ENVIAR_BUTTON =
            By.xpath("//button[normalize-space()='Enviar']");
    private static final By CONFIRM_OK_BUTTON =
            By.xpath("//button[normalize-space()='OK' or normalize-space()='Ok']");

    public DocumentSubmissionPage(WebDriver driver) {
        super(driver);
    }

    public DocumentSubmissionPage open(String documentSubmissionUrl) {
        log.info("Navigate to: {}", documentSubmissionUrl);
        driver.get(documentSubmissionUrl);
        return this;
    }

    private By fileInputFor(String label) {
        return By.xpath("//*[contains(normalize-space(.),\"" + label + "\")]/following::input[@type='file'][1]");
    }

    private By fieldInputFor(String label) {
        return By.xpath("//label[contains(normalize-space(.),\"" + label + "\")]/following::input[1]");
    }

    public void uploadDocumentoIdentidad(String filePath) {
        waitForPresent(fileInputFor("Documento de Identidad")).sendKeys(filePath);
    }

    public void uploadComprobanteDomicilio(String filePath) {
        waitForPresent(fileInputFor("Comprobante de Domicilio")).sendKeys(filePath);
    }

    public void uploadConstanciaSituacionFiscal(String filePath) {
        waitForPresent(fileInputFor("Constancia de situación fiscal")).sendKeys(filePath);
    }

    public void uploadActaConstitutiva(String filePath) {
        waitForPresent(fileInputFor("Acta Constitutiva")).sendKeys(filePath);
    }

    public void uploadPoderNotarial(String filePath) {
        waitForPresent(fileInputFor("Poder Notarial")).sendKeys(filePath);
    }

    public void uploadOpinionCumplimientoSat(String filePath) {
        waitForPresent(fileInputFor("Opinión de Cumplimiento SAT")).sendKeys(filePath);
    }

    public void uploadLicenciaComercial(String filePath) {
        waitForPresent(fileInputFor("Licencia comercial")).sendKeys(filePath);
    }

    public void uploadEstadoCuentaBancario(String filePath) {
        waitForPresent(fileInputFor("Número de Cuenta Bancaria")).sendKeys(filePath);
    }

    public void fillBankDetails(Map<String, String> data) {
        type(fieldInputFor("Email de la tienda"), data.get(KEY_TIENDA_EMAIL));
        type(fieldInputFor("Número de Cuenta Bancaria"), data.get(KEY_NUMERO_CUENTA_BANCARIA));
        type(fieldInputFor("CLABE"), data.get(KEY_CLABE));
        type(fieldInputFor("Nombre del Banco"), data.get(KEY_NOMBRE_BANCO));
        type(fieldInputFor("Nombre del Titular"), data.get(KEY_NOMBRE_TITULAR));
    }

    public void acceptTermsAndSubmit() {
        click(TERMS_CHECKBOX);
        click(ENVIAR_BUTTON);
        click(CONFIRM_OK_BUTTON);
    }

    /** Runs the whole steps 71-85 sequence given the 8 document file paths and bank data. */
    public void submitAllDocuments(Map<String, String> data,
                                    String identidadPath, String comprobanteDomicilioPath,
                                    String constanciaFiscalPath, String actaConstitutivaPath,
                                    String poderNotarialPath, String opinionSatPath,
                                    String licenciaComercialPath, String estadoCuentaPath) {
        log.info("Uploading seller documents and bank details");
        uploadDocumentoIdentidad(identidadPath);
        uploadComprobanteDomicilio(comprobanteDomicilioPath);
        uploadConstanciaSituacionFiscal(constanciaFiscalPath);
        uploadActaConstitutiva(actaConstitutivaPath);
        uploadPoderNotarial(poderNotarialPath);
        uploadOpinionCumplimientoSat(opinionSatPath);
        uploadLicenciaComercial(licenciaComercialPath);
        fillBankDetails(data);
        uploadEstadoCuentaBancario(estadoCuentaPath);
        acceptTermsAndSubmit();
        log.info("Document upload and bank-details submission completed");
    }
}
