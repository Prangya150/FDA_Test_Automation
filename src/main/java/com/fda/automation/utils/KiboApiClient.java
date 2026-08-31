package com.fda.automation.utils;

import com.fda.automation.config.ConfigManager;
import io.restassured.response.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

import static io.restassured.RestAssured.given;

/**
 * Thin REST client for the Kibo commerce API used by TC_SOB_001's final check (step 111):
 * after a seller's shop is approved and appears in Mirakl, its corresponding Kibo location
 * should also exist. Pure HTTP - no Selenium/browser involved.
 *
 * getLocation() takes a location id; callers (see SellerOnboardingEndToEndTest's
 * step9_kiboLocationRespondsOk) pass the shop id captured from the Mirakl shop link
 * (MiraklShopSearchPage.openShopAndCaptureId - /mmp/operator/shop/{id}) straight through as
 * that location id. This assumes the Mirakl shop id and the Kibo location id are the same
 * value - that assumption is not proven by this code and should be confirmed on the first
 * real run (if it's wrong, this will surface as a 404/non-200 rather than silently passing).
 */
public class KiboApiClient {

    private static final Logger log = LogManager.getLogger(KiboApiClient.class);

    private final String baseUrl;
    private final String site;

    public KiboApiClient() {
        ConfigManager cfg = ConfigManager.getInstance();
        this.baseUrl = cfg.getKiboBaseUrl();
        this.site = cfg.getKiboSite();
    }

    /**
     * Exchanges KIBO_CLIENT_ID/KIBO_CLIENT_SECRET for a fresh bearer token via the
     * client-credentials OAuth grant. Tokens are short-lived, so this is called fresh
     * per test run rather than reusing a hardcoded one.
     */
    public String fetchAccessToken() {
        ConfigManager cfg = ConfigManager.getInstance();
        String clientId = cfg.getKiboClientId();
        String clientSecret = cfg.getKiboClientSecret();
        if (clientId == null || clientSecret == null) {
            throw new IllegalStateException(
                    "KIBO_CLIENT_ID/KIBO_CLIENT_SECRET environment variables must be set to call the Kibo API");
        }

        log.info("Fetching Kibo OAuth token from {}", baseUrl);
        Response response = given()
                .baseUri(baseUrl)
                .header("Content-Type", "application/json")
                .header("x-vol-site", site)
                .body(Map.of(
                        "grant_type", "client_credentials",
                        "client_id", clientId,
                        "client_secret", clientSecret))
                .post("/api/platform/applications/authtickets/oauth");

        response.then().statusCode(200);
        String token = response.jsonPath().getString("access_token");
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Kibo OAuth response did not contain an access_token: " + response.asString());
        }
        return token;
    }

    /**
     * GETs a Kibo commerce location by id. See the class javadoc for the known gap around
     * mapping a specific seller to a specific location id.
     */
    public Response getLocation(String accessToken, String locationId) {
        log.info("Fetching Kibo location {}", locationId);
        return given()
                .baseUri(baseUrl)
                .header("Content-Type", "application/json")
                .header("x-vol-site", site)
                .header("Authorization", "Bearer " + accessToken)
                .get("/api/commerce/admin/locations/" + locationId);
    }

    /**
     * Same as getLocation(), but if the token has expired mid-run (a 401), fetches one
     * fresh token and retries once rather than failing outright.
     */
    public Response getLocationRetryingOn401(String accessToken, String locationId) {
        Response response = getLocation(accessToken, locationId);
        if (response.statusCode() == 401) {
            log.info("Kibo location {} returned 401 (likely an expired token); fetching a fresh token and retrying", locationId);
            response = getLocation(fetchAccessToken(), locationId);
        }
        return response;
    }
}
