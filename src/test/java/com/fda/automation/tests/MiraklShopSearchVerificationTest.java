package com.fda.automation.tests;

import com.fda.automation.base.BaseTest;
import com.fda.automation.config.ConfigManager;
import com.fda.automation.pages.MiraklLoginPage;
import com.fda.automation.pages.MiraklShopSearchPage;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.time.Duration;

/**
 * Standalone verification of Mirakl login + shop search (TC_SOB_001 stage 8) against a shop
 * that already completed stages 1-7 in an earlier full run, instead of re-running the whole
 * seller onboarding flow (which would create a new seller just to re-test this one stage).
 *
 * Requires OUTLOOK_USERNAME/PASSWORD and MIRAKL_USERNAME/PASSWORD env vars, plus
 * -Dmirakl.verify.tradeName=<trade name already approved in an earlier run>.
 */
public class MiraklShopSearchVerificationTest extends BaseTest {

    @Test(groups = {"smoke"}, description = "Verify Mirakl login + shop search against an already-approved shop")
    public void verifyExistingShopIsSearchable() {
        String tradeName = System.getProperty("mirakl.verify.tradeName");
        Assert.assertNotNull(tradeName, "-Dmirakl.verify.tradeName=<trade name> is required");

        String miraklUsername = requireEnv(ConfigManager.getInstance().getMiraklUsername(), "MIRAKL_USERNAME");
        String miraklPassword = requireEnv(ConfigManager.getInstance().getMiraklPassword(), "MIRAKL_PASSWORD");
        String outlookUsername = requireEnv(ConfigManager.getInstance().getOutlookUsername(), "OUTLOOK_USERNAME");
        String outlookPassword = requireEnv(ConfigManager.getInstance().getOutlookPassword(), "OUTLOOK_PASSWORD");

        MiraklShopSearchPage shopSearch = new MiraklLoginPage(getDriver()).open()
                .login(miraklUsername, miraklPassword, outlookUsername, outlookPassword);
        shopSearch.openAllShopAccounts();

        boolean found = shopSearch.waitForShopInResults(tradeName, Duration.ofMinutes(3));
        Assert.assertTrue(found, "Expected shop \"" + tradeName + "\" to appear in Mirakl's Todas las cuentas tienda search results");
        log.info("Mirakl shop search verification passed for trade name {}", tradeName);

        String shopId = shopSearch.openShopAndCaptureId(tradeName);
        log.info("Captured Mirakl shop id: {}", shopId);
    }

    private String requireEnv(String value, String envVarName) {
        Assert.assertNotNull(value, envVarName + " environment variable must be set to run this test");
        return value;
    }
}
