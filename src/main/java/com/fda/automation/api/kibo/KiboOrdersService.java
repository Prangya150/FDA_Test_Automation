package com.fda.automation.api.kibo;

import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;

/**
 * Looks up the Kibo internal order id that corresponds to an FDA order id (matched via Kibo's
 * externalId field).
 */
public class KiboOrdersService {

    private static final Logger log = LogManager.getLogger(KiboOrdersService.class);
    // CONFIRMED live on 2026-08-20: Kibo's externalId is the FDA order number plus a "WEB" suffix
    // (e.g. FDA order 4000289069 -> externalId "4000289069WEB"), not the bare order number - the
    // same class of suffix mismatch already found for Mirakl's own order id search.
    private static final String KIBO_EXTERNAL_ID_SUFFIX = "WEB";

    private final String ordersUrl;

    public KiboOrdersService(String ordersUrl) {
        this.ordersUrl = ordersUrl;
    }

    /**
     * Fetches the order list and returns the Kibo "id" whose "externalId" equals {@code fdaOrderId}.
     * Returns {@code null} if no matching order is found yet (caller decides whether to retry).
     */
    public String findKiboOrderIdByExternalId(String accessToken, String fdaOrderId) {
        Response response = given()
                .header("Authorization", "Bearer " + accessToken)
                .when()
                .get(ordersUrl);

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Kibo Get All Orders failed with HTTP " + response.statusCode()
                    + ": " + response.getBody().asPrettyString());
        }

        JsonPath json = response.jsonPath();
        List<Map<String, Object>> items = json.getList("items");
        if (items == null) {
            log.warn("Kibo Get All Orders response contained no 'items' array");
            return null;
        }

        String expectedExternalId = fdaOrderId + KIBO_EXTERNAL_ID_SUFFIX;
        for (Map<String, Object> item : items) {
            Object externalId = item.get("externalId");
            if (externalId != null && expectedExternalId.equals(String.valueOf(externalId))) {
                Object id = item.get("id");
                log.info("Matched Kibo order for FDA order {}", fdaOrderId);
                return id == null ? null : String.valueOf(id);
            }
        }

        log.debug("No Kibo order found yet for FDA order {} ({} items checked)", fdaOrderId, items.size());
        return null;
    }
}
