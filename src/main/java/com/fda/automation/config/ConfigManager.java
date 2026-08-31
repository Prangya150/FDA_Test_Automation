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

    /**
     * Milliseconds to pause after each click()/type() action - purely so a human watching
     * the browser live can follow along. 0 (no pause) unless overridden with -Dslowmo.ms.
     */
    public int getSlowMotionMillis() {
        return Integer.parseInt(get("slowmo.ms", "0"));
    }

    /**
     * Mirakl middleware (webhook-sit.fahorro.com.mx) seller-onboarding login URL.
     */
    public String getMiddlewareUrl() {
        return get("middleware.url", "https://webhook-sit.fahorro.com.mx/fda/api/v2/seller-onboarding/login");
    }

    /**
     * Mirakl marketplace back-office URL.
     */
    public String getMiraklUrl() {
        return get("mirakl.url", "https://farmaciasdelahorromx2-dev.mirakl.net/");
    }

    /**
     * Outlook/Office 365 test-account credentials, read from environment variables only
     * (never from config.properties) so they never end up committed to the repo.
     */
    public String getOutlookUsername() {
        return System.getenv("OUTLOOK_USERNAME");
    }

    public String getOutlookPassword() {
        return System.getenv("OUTLOOK_PASSWORD");
    }

    /**
     * Mirakl middleware operator credentials, environment variables only - see
     * getOutlookUsername/Password() javadoc for why.
     */
    public String getMiddlewareUsername() {
        return System.getenv("MIDDLEWARE_USERNAME");
    }

    public String getMiddlewarePassword() {
        return System.getenv("MIDDLEWARE_PASSWORD");
    }

    /**
     * Mirakl marketplace back-office operator credentials, environment variables only.
     */
    public String getMiraklUsername() {
        return System.getenv("MIRAKL_USERNAME");
    }

    public String getMiraklPassword() {
        return System.getenv("MIRAKL_PASSWORD");
    }

    /**
     * Kibo commerce API OAuth client-credentials, environment variables only.
     */
    public String getKiboClientId() {
        return System.getenv("KIBO_CLIENT_ID");
    }

    public String getKiboClientSecret() {
        return System.getenv("KIBO_CLIENT_SECRET");
    }

    /**
     * Kibo commerce API base URL and x-vol-site tenant header (not secrets, so these are
     * ordinary config.properties keys, unlike the client id/secret above).
     */
    public String getKiboBaseUrl() {
        return get("kibo.baseUrl", "https://t1000490-s1000992.sb.usc1.gcp.kibocommerce.com");
    }

    public String getKiboSite() {
        return get("kibo.site", "1000992");
    }
}
