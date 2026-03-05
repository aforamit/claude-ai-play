package com.accounting.receipt.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Loads configuration from application.properties.
 *
 * Priority order (highest wins):
 *   1. Environment variables (ANTHROPIC_API_KEY, etc.)
 *   2. External application.properties in the working directory
 *   3. application.properties bundled inside the JAR
 */
public class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);
    private static final String CONFIG_FILE = "application.properties";

    private final Properties props;

    private AppConfig(Properties props) {
        this.props = props;
    }

    public static AppConfig load() throws IOException {
        Properties props = new Properties();

        // 1. Try external file first (deployment-friendly override)
        Path external = Paths.get(CONFIG_FILE);
        if (Files.exists(external)) {
            try (InputStream is = Files.newInputStream(external)) {
                props.load(is);
                log.info("Config loaded from: {}", external.toAbsolutePath());
            }
        } else {
            // 2. Fall back to classpath (bundled in JAR)
            try (InputStream is = AppConfig.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
                if (is != null) {
                    props.load(is);
                    log.info("Config loaded from classpath ({})", CONFIG_FILE);
                }
            }
        }

        // 3. Override with environment variables where provided
        applyEnvOverride(props, "ANTHROPIC_API_KEY",      "ocr.claude.api-key");
        applyEnvOverride(props, "GMAIL_CREDENTIALS_PATH", "gmail.credentials.path");
        applyEnvOverride(props, "OUTPUT_CSV_PATH",        "output.csv.path");

        return new AppConfig(props);
    }

    private static void applyEnvOverride(Properties props, String envVar, String propKey) {
        String value = System.getenv(envVar);
        if (value != null && !value.isBlank()) {
            props.setProperty(propKey, value);
            log.debug("Config '{}' overridden by env var '{}'", propKey, envVar);
        }
    }

    /** Returns the property value, or null if not set. */
    public String get(String key) {
        return props.getProperty(key);
    }

    /** Returns the property value, or the given default if not set. */
    public String get(String key, String defaultValue) {
        return props.getProperty(key, defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        String value = props.getProperty(key);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            log.warn("Invalid int value for '{}': '{}', using default {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String value = props.getProperty(key);
        if (value == null) return defaultValue;
        return Boolean.parseBoolean(value.trim());
    }
}
