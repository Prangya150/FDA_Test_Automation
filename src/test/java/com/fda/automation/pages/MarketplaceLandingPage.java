package com.fda.automation.pages;

import com.fda.automation.base.BasePage;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

/**
 * Corresponds to /marketplace-inicio/, reached via HomePage.goToSellerLanding().
 * Confirmed live on mcstaging.fahorro.com: "Comenzar a vender" has no id/class
 * (text-only match is the only option) and links to /marketplace/provider/form.
 */
public class MarketplaceLandingPage extends BasePage {

    private static final By COMENZAR_A_VENDER_BUTTON =
            By.xpath("//a[contains(normalize-space(.),'Comenzar a vender')] | //button[contains(normalize-space(.),'Comenzar a vender')]");

    public MarketplaceLandingPage(WebDriver driver) {
        super(driver);
    }

    public MarketplaceLandingPage open() {
        navigateTo("/marketplace-inicio/");
        return this;
    }

    public SellerRegistrationPage clickComenzarAVender() {
        click(COMENZAR_A_VENDER_BUTTON);
        return new SellerRegistrationPage(driver);
    }
}
