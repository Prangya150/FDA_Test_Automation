package com.fda.automation.pages;

import com.fda.automation.base.BasePage;
import com.fda.automation.config.ConfigManager;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

/**
 * Mirakl middleware operator login (TC_SOB_001 steps 51-55): "Correo electrónico" /
 * "Contraseña" fields (label-anchored xpath, confirmed live) and the "Iniciar sesión"
 * button.
 *
 * Confirmed live: this is a MUI (Material UI) form. The email/password label-anchored
 * xpaths worked as originally written. The login button's visible all-caps "INICIAR
 * SESIÓN" is CSS text-transform only - its real DOM text is mixed-case "Iniciar sesión"
 * (MuiButton-containedPrimary), which is what normalize-space() actually sees, so the
 * original all-caps literal never matched and the click timed out.
 */
public class MiddlewareLoginPage extends BasePage {

    private static final By EMAIL_INPUT =
            By.xpath("//label[contains(normalize-space(.),'Correo electrónico')]/following::input[1]");
    private static final By PASSWORD_INPUT =
            By.xpath("//label[contains(normalize-space(.),'Contraseña')]/following::input[1]");
    private static final By LOGIN_BUTTON =
            By.xpath("//button[normalize-space()='Iniciar sesión']");

    public MiddlewareLoginPage(WebDriver driver) {
        super(driver);
    }

    public MiddlewareLoginPage open() {
        String url = ConfigManager.getInstance().getMiddlewareUrl();
        log.info("Navigate to: {}", url);
        driver.get(url);
        return this;
    }

    public MiddlewareSellerListPage login(String username, String password) {
        log.info("Logging into Mirakl middleware as {}", username);
        type(EMAIL_INPUT, username);
        type(PASSWORD_INPUT, password);
        click(LOGIN_BUTTON);
        return new MiddlewareSellerListPage(driver);
    }
}
