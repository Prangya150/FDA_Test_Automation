package com.fda.automation.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ConfigManager {
    private static final Properties props = new Properties();
    private static ConfigManager instance;

    private ConfigManager() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("config.properties")) {
            if (is == null) throw new RuntimeException("config.properties not found on classpath");
            props.load(is);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config.properties", e);
        }
    }

    public static synchronized ConfigManager getInstance() {
        if (instance == null) {
            instance = new ConfigManager();
        }
        return instance;
    }

    public String get(String key) {
        String sysProp = System.getProperty(key);
        return sysProp != null ? sysProp : props.getProperty(key);
    }

    public String get(String key, String defaultValue) {
        String sysProp = System.getProperty(key);
        return sysProp != null ? sysProp : props.getProperty(key, defaultValue);
    }

    public String getBrowser() {
        return get("browser", "chrome");
    }

    public String getBaseUrl() {
        return get("base.url");
    }

    public int getExplicitWait() {
        return Integer.parseInt(get("explicit.wait", "10"));
    }

    public boolean isHeadless() {
        return Boolean.parseBoolean(get("headless", "false"));
    }

    // --- FDA (Magento storefront) ---
    public String getFdaBaseUrl() {
        return get("fda.base.url");
    }

    public String getFdaUsername() {
        return get("fda.username");
    }

    public String getFdaPassword() {
        return get("fda.password");
    }

    public String getFdaProductSku() {
        return get("fda.product.sku");
    }

    public String getFdaCardNumber() {
        return get("fda.card.number");
    }

    public String getFdaCardExpiry() {
        return get("fda.card.expiry");
    }

    public String getFdaCardCvv() {
        return get("fda.card.cvv");
    }

    public String getFdaOrderInitialStatus() {
        return get("fda.order.initial.status", "Pendiente");
    }

    // --- Mirakl (operator front office) ---
    public String getMiraklBaseUrl() {
        return get("mirakl.base.url");
    }

    public String getMiraklUsername() {
        return get("mirakl.username");
    }

    public String getMiraklPassword() {
        return get("mirakl.password");
    }

    public int getMiraklSyncTimeoutSeconds() {
        return Integer.parseInt(get("mirakl.sync.timeout.seconds", "180"));
    }

    public int getMiraklSyncPollIntervalSeconds() {
        return Integer.parseInt(get("mirakl.sync.poll.interval.seconds", "5"));
    }

    public String getInvoiceFilePath() {
        return get("invoice.file.path");
    }

    public String getTrackingCarrier() {
        return get("tracking.carrier", "DHL");
    }

    // --- Kibo Commerce API ---
    public String getKiboAuthUrl() {
        return get("kibo.auth.url");
    }

    public String getKiboOrdersUrl() {
        return get("kibo.orders.url");
    }

    public String getKiboShipmentsUrl() {
        return get("kibo.shipments.url");
    }

    public String getKiboClientId() {
        return get("kibo.client.id");
    }

    public String getKiboClientSecret() {
        return get("kibo.client.secret");
    }

    public String getKiboShipmentDeliveryTypeJsonPath() {
        return get("kibo.shipment.deliveryType.jsonPath", "items[0].deliveryType");
    }

    public int getKiboSyncTimeoutSeconds() {
        return Integer.parseInt(get("kibo.sync.timeout.seconds", "120"));
    }

    public int getKiboSyncPollIntervalSeconds() {
        return Integer.parseInt(get("kibo.sync.poll.interval.seconds", "5"));
    }
}
