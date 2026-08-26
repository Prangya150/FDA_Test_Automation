package com.fda.automation.utils;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Persists identifiers produced by Stage 1 (seller registration) to disk so that
 * later stages (middleware pre-approval, document submission, Mirakl/Kibo checks),
 * which run as separate test executions, can look up which seller to act on.
 */
public class SellerContextStore {

    public static final String KEY_EMAIL = "seller.email";
    public static final String KEY_TRADE_NAME = "seller.tradeName";

    private static final Path STORE_PATH = Paths.get("target", "seller-context.properties");

    private SellerContextStore() {
    }

    public static void save(String email, String tradeName) {
        Properties props = new Properties();
        props.setProperty(KEY_EMAIL, email);
        props.setProperty(KEY_TRADE_NAME, tradeName);
        try {
            Files.createDirectories(STORE_PATH.getParent());
            try (FileOutputStream out = new FileOutputStream(STORE_PATH.toFile())) {
                props.store(out, "Seller identifiers produced by SellerOnboardingTest Stage 1");
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to persist seller context to " + STORE_PATH, e);
        }
    }

    public static Properties load() {
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(STORE_PATH.toFile())) {
            props.load(in);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read seller context from " + STORE_PATH
                    + " - has Stage 1 (SellerOnboardingTest) run yet?", e);
        }
        return props;
    }
}
