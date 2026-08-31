package com.fda.automation.pages;

import com.fda.automation.base.BasePage;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

/**
 * Mirakl middleware seller detail/review screen, reached via MiddlewareSellerListPage's
 * "VISTA" button. Shared by two passes of TC_SOB_001:
 * - Pre-approval (steps 57-60): review Información personal/Empresa/Tienda/Producto, one
 *   "Continuar" per section, ending in "PREAPROBACIÓN".
 * - Final approval (steps 88-105): same 4 sections, but this pass also downloads each
 *   of the 8 submitted documents and ends in "APROBAR" + a confirm dialog.
 *
 * Confirmed live: the 4-section review is a client-side-only "view mode" (fields are
 * disabled inputs, URL is .../preview/{id}?view=true) - "Continuar" just advances between
 * sections without hitting the backend at all. The actual state-changing action only
 * happens after clicking PREAPROBACIÓN/APROBAR, which opens a "Confirmación" dialog
 * ("¿Deseas continuar con esta acción?", OK/Cancelar) that must also be confirmed - the
 * original reviewAndPreApprove() clicked PREAPROBACIÓN and stopped without confirming
 * that dialog, so the click sequence completed with no exception but never actually
 * changed the seller's Estado away from "1 - Solicitud Recibida", silently leaving every
 * seller unapproved (verified live: 9 sellers from earlier runs all still showed that
 * status). downloadDocumentsAndApprove() already confirmed this same dialog after APROBAR.
 *
 * Even with that dialog confirmed, two later live-verified runs still occasionally left
 * the seller's Estado unchanged despite both clicks completing with no exception - a
 * manual replay of the identical click sequence with no extra delay succeeded every time,
 * pointing at a narrow timing race in the automated path rather than a locator problem.
 * A successful confirm redirects away from this /preview/{id} page back to the seller
 * list, so confirmTerminalAction() uses that as a real success signal and retries the
 * whole action-button + confirm-dialog sequence if the redirect doesn't happen.
 *
 * NOT implemented: granular per-field format assertions the manual test calls for (e.g.
 * "verify Número telefónico displays a valid phone format", steps 91-93/99-102) and
 * verifying each downloaded document actually completes within 3 minutes (steps 89-90/94/
 * 96-98/103) - this automates the workflow (navigate the review, trigger each download,
 * take the terminal action) rather than those content-level checks. Add them incrementally
 * once the real markup is confirmed.
 */
public class MiddlewareSellerDetailPage extends BasePage {

    private static final By CONTINUAR_BUTTON =
            By.xpath("//button[normalize-space()='Continuar'] | //button[contains(normalize-space(.),'Continuar')]");
    private static final By PREAPROBACION_BUTTON =
            By.xpath("//button[contains(normalize-space(.),'PREAPROBACIÓN') or contains(normalize-space(.),'PREAPROBACION')]");
    private static final By APROBAR_BUTTON =
            By.xpath("//button[normalize-space()='APROBAR']");
    private static final By CONFIRM_OK_BUTTON =
            By.xpath("//button[normalize-space()='OK' or normalize-space()='Ok']");

    // Confirmed live: the middleware review page's own label for this document is
    // "Poder Notaria" (missing the trailing "l" - a typo in the live app itself, distinct
    // from the FDA upload form's correctly-spelled "Poder Notarial" label that
    // DocumentSubmissionPage.uploadPoderNotarial() targets).
    private static final String[] DOCUMENT_LABELS = {
            "Documento de Identidad",
            "Comprobante de Domicilio",
            "Constancia de situación fiscal",
            "Acta Constitutiva",
            "Poder Notaria",
            "Opinión de Cumplimiento SAT",
            "Licencia comercial",
            "Estado de Cuenta Bancario"
    };

    public MiddlewareSellerDetailPage(WebDriver driver) {
        super(driver);
    }

    private By downloadButtonFor(String documentLabel) {
        return By.xpath("//*[contains(normalize-space(.),\"" + documentLabel + "\")]"
                + "/following::button[contains(normalize-space(.),'Descargar Documento')][1]");
    }

    /** Steps 57-60: review the 4 sections and pre-approve. */
    public void reviewAndPreApprove() {
        log.info("Reviewing seller info and pre-approving");
        click(CONTINUAR_BUTTON); // personal info -> company info
        click(CONTINUAR_BUTTON); // company info -> store info
        click(CONTINUAR_BUTTON); // store info -> product info
        confirmTerminalAction(PREAPROBACION_BUTTON);
    }

    /** Steps 88-105: download each submitted document, then final-approve. */
    public void downloadDocumentsAndApprove() {
        log.info("Downloading submitted documents and approving");
        for (String label : DOCUMENT_LABELS) {
            click(downloadButtonFor(label));
        }
        confirmTerminalAction(APROBAR_BUTTON);
    }

    /** See the class javadoc for why this verifies-and-retries rather than just clicking. */
    private void confirmTerminalAction(By actionButton) {
        String detailUrl = driver.getCurrentUrl();
        final int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            click(actionButton);
            click(CONFIRM_OK_BUTTON);
            try {
                wait.until(d -> !d.getCurrentUrl().equals(detailUrl));
                return;
            } catch (org.openqa.selenium.TimeoutException e) {
                log.warn("{} + confirm did not navigate away from the review page (attempt {}/{}); retrying",
                        actionButton, attempt, maxAttempts);
                if (attempt == maxAttempts) {
                    throw e;
                }
            }
        }
    }
}
