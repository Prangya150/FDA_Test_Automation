package com.fda.automation.api.kibo;

import io.restassured.response.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static io.restassured.RestAssured.given;

/**
 * Retrieves shipment details for a Kibo order and extracts the delivery type (expected "FBS").
 */
public class KiboShipmentService {

    private static final Logger log = LogManager.getLogger(KiboShipmentService.class);

    private final String shipmentsUrl;

    public KiboShipmentService(String shipmentsUrl) {
        this.shipmentsUrl = shipmentsUrl;
    }

    /**
     * @param deliveryTypeJsonPath configurable JsonPath into the shipment response
     *                             (see {@code kibo.shipment.deliveryType.jsonPath} in config.properties;
     *                             TODO: confirm the real field name against a live response and adjust the config value)
     */
    public String getDeliveryType(String accessToken, String kiboOrderId, String deliveryTypeJsonPath) {
        Response response = given()
                .header("Authorization", "Bearer " + accessToken)
                .queryParam("pageSize", 200)
                .queryParam("filter", "orderId==" + kiboOrderId)
                .when()
                .get(shipmentsUrl);

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Kibo Get Shipment Details failed with HTTP " + response.statusCode()
                    + ": " + response.getBody().asPrettyString());
        }

        String deliveryType = response.jsonPath().getString(deliveryTypeJsonPath);
        log.info("Kibo shipment lookup for order {} returned deliveryType={}", kiboOrderId, deliveryType);
        return deliveryType;
    }
}
