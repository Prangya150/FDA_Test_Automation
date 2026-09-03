package com.fda.automation.api.kibo;

import io.restassured.response.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    private Response fetchShipments(String accessToken, String kiboOrderId) {
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
        return response;
    }

    /**
     * @param deliveryTypeJsonPath configurable JsonPath into the shipment response
     *                             (see {@code kibo.shipment.deliveryType.jsonPath} in config.properties;
     *                             TODO: confirm the real field name against a live response and adjust the config value)
     */
    public String getDeliveryType(String accessToken, String kiboOrderId, String deliveryTypeJsonPath) {
        Response response = fetchShipments(accessToken, kiboOrderId);
        String deliveryType = response.jsonPath().getString(deliveryTypeJsonPath);
        log.info("Kibo shipment lookup for order {} returned deliveryType={}", kiboOrderId, deliveryType);
        return deliveryType;
    }

    /**
     * CORRECTED on 2026-09-01 after a live TC_FBS_005 run kept reporting exactly 1 delivery type no
     * matter how long it polled: an order fulfilled by two different 3P sellers is not split into
     * one Kibo shipment per seller. Kibo returns a single shipment (see
     * {@code kibo.shipment.deliveryType.jsonPath}, confirmed live on 2026-08-20 as
     * "_embedded.shipments[0].items[0].data.deliveryType") whose "items" array holds one line item
     * per seller, each with its own "data.deliveryType". The earlier version of this method read
     * only {@code items[0]} of every shipment, so with a single shipment holding both sellers'
     * items it always returned just the first one. This walks every item of every shipment instead.
     */
    public List<String> getAllDeliveryTypes(String accessToken, String kiboOrderId) {
        Response response = fetchShipments(accessToken, kiboOrderId);
        List<Map<String, Object>> shipments = response.jsonPath().getList("_embedded.shipments");

        List<String> deliveryTypes = new ArrayList<>();
        for (Map<String, Object> shipment : shipments) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) shipment.get("items");
            if (items == null) {
                continue;
            }
            for (Map<String, Object> item : items) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) item.get("data");
                if (data != null && data.get("deliveryType") != null) {
                    deliveryTypes.add(String.valueOf(data.get("deliveryType")));
                }
            }
        }

        log.info("Kibo shipment lookup for order {} returned {} shipment(s), {} delivery type(s)={}",
                kiboOrderId, shipments.size(), deliveryTypes.size(), deliveryTypes);
        return deliveryTypes;
    }
}
