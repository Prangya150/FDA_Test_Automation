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
     * Outlook/Office 365 test-account credentials, read from environment variables only
     * (never from config.properties) so they never end up committed to the repo.
     */
    public String getOutlookUsername() {
        return System.getenv("OUTLOOK_USERNAME");
    }

    public String getOutlookPassword() {
        return System.getenv("OUTLOOK_PASSWORD");
    }
}
