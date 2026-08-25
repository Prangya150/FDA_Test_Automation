package com.fda.automation.api.kibo;

import io.restassured.response.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

import static io.restassured.RestAssured.given;

/**
 * Kibo Commerce OAuth client-credentials authentication.
 * A fresh access token is requested for every test execution; nothing is cached or hardcoded.
 */
public class KiboAuthService {

    private static final Logger log = LogManager.getLogger(KiboAuthService.class);

    private final String authUrl;

    public KiboAuthService(String authUrl) {
        this.authUrl = authUrl;
    }

    /**
     * Exchanges the configured client id/secret for a bearer access token.
     * Neither the secret nor the resulting token is ever logged.
     */
    public String generateAccessToken(String clientId, String clientSecret) {
        Map<String, String> body = Map.of(
                "grant_type", "client_credentials",
                "client_id", clientId,
                "client_secret", clientSecret
        );

        log.info("Requesting Kibo access token from {}", authUrl);
        Response response = given()
                .contentType("application/json")
                .body(body)
                .when()
                .post(authUrl);

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Kibo auth failed with HTTP " + response.statusCode()
                    + ": " + response.getBody().asPrettyString());
        }

        String accessToken = response.jsonPath().getString("access_token");
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException("Kibo auth response did not contain an access_token");
        }

        log.info("Kibo access token obtained successfully");
        return accessToken;
    }
}
