package com.jawwy.automation.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

public final class FrameworkConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(FrameworkConfig.class);
    private static final String DEFAULT_ENV = "local";
    private static volatile FrameworkConfig instance;

    private final Properties properties;

    private FrameworkConfig(Properties properties) {
        this.properties = properties;
    }

    public static FrameworkConfig getInstance() {
        if (instance == null) {
            synchronized (FrameworkConfig.class) {
                if (instance == null) {
                    instance = load();
                }
            }
        }
        return instance;
    }

    public static synchronized void reload() {
        instance = load();
    }

    private static FrameworkConfig load() {
        Properties properties = new Properties();
        loadFromClasspath(properties, "config/application.properties");

        String env = resolveEnv(properties);
        loadFromClasspath(properties, "config/application-" + env + ".properties");
        applyEnvironmentVariables(properties);
        applySystemProperties(properties);
        properties.setProperty("env.name", env);
        validateResolvedConfiguration(properties);

        LOGGER.info("Loaded configuration for environment '{}'", env);
        return new FrameworkConfig(properties);
    }

    private static String resolveEnv(Properties properties) {
        String systemValue = System.getProperty("env");
        if (isNotBlank(systemValue)) {
            return systemValue.trim();
        }

        String environmentValue = System.getenv("JAWWY_ENV");
        if (isNotBlank(environmentValue)) {
            return environmentValue.trim();
        }

        return properties.getProperty("env.default", DEFAULT_ENV).trim();
    }

    private static void loadFromClasspath(Properties properties, String path) {
        try (InputStream inputStream = FrameworkConfig.class.getClassLoader().getResourceAsStream(path)) {
            if (inputStream == null) {
                return;
            }
            properties.load(inputStream);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load config file: " + path, exception);
        }
    }

    private static void applyEnvironmentVariables(Properties properties) {
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put("JAWWY_API_BASE_URL", "api.base-url");
        mappings.put("JAWWY_UI_BASE_URL", "ui.base-url");
        mappings.put("JAWWY_UI_USERNAME", "ui.username");
        mappings.put("JAWWY_UI_PASSWORD", "ui.password");
        mappings.put("JAWWY_DB_URL", "db.url");
        mappings.put("JAWWY_DB_USER", "db.user");
        mappings.put("JAWWY_DB_PASSWORD", "db.password");
        mappings.put("JAWWY_BROWSER_HEADLESS", "browser.headless");

        for (Map.Entry<String, String> entry : mappings.entrySet()) {
            String value = System.getenv(entry.getKey());
            if (isNotBlank(value)) {
                properties.setProperty(entry.getValue(), value.trim());
            }
        }
    }

    private static void applySystemProperties(Properties properties) {
        for (String propertyName : System.getProperties().stringPropertyNames()) {
            String value = System.getProperty(propertyName);
            if (isNotBlank(value)) {
                properties.setProperty(propertyName, value.trim());
            }
        }
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static void validateResolvedConfiguration(Properties properties) {
        String envName = properties.getProperty("env.name", DEFAULT_ENV).trim();
        validateUrl("api.base-url", properties.getProperty("api.base-url"), envName);
        validateUrl("ui.base-url", properties.getProperty("ui.base-url"), envName);
        validateDbUrl(properties.getProperty("db.url"), envName);
    }

    private static void validateUrl(String key, String value, String envName) {
        if (!isNotBlank(value)) {
            return;
        }

        URI uri = parseUri(key, value);
        String host = uri.getHost();
        if (!isNotBlank(host)) {
            throw new IllegalStateException("Invalid value for " + key + ": " + value);
        }

        validateHostPlaceholder(key, host, envName);

        if ("local".equalsIgnoreCase(envName) && isLoopbackHost(host)) {
            int port = resolvePort(uri);
            if (!isTcpReachable(host, port, 1000)) {
                throw new IllegalStateException(
                        "Environment 'local' is configured to use " + value + ", but nothing is listening on "
                                + host + ":" + port + ". Start the local EOC service first, or run with -Denv=sit / -Denv=uat, "
                                + "or override JAWWY_API_BASE_URL and JAWWY_UI_BASE_URL.");
            }
        }
    }

    private static void validateDbUrl(String dbUrl, String envName) {
        if (!isNotBlank(dbUrl)) {
            return;
        }

        String normalized = dbUrl.trim().toLowerCase();
        if ("local".equalsIgnoreCase(envName) && normalized.contains("//localhost:")) {
            LOGGER.warn("Environment 'local' is using a localhost Oracle URL: {}", dbUrl);
        }

        if (normalized.contains("//sit-db-host:") || normalized.contains("//uat-db-host:")) {
            throw new IllegalStateException(
                    "The configured db.url still contains a placeholder host (" + dbUrl + "). "
                            + "Replace it with the real database host or provide JAWWY_DB_URL.");
        }
    }

    private static URI parseUri(String key, String value) {
        try {
            return new URI(value.trim());
        } catch (URISyntaxException exception) {
            throw new IllegalStateException("Invalid URI configured for " + key + ": " + value, exception);
        }
    }

    private static void validateHostPlaceholder(String key, String host, String envName) {
        String normalizedHost = host.trim().toLowerCase();
        if (normalizedHost.equals("sit-host") || normalizedHost.equals("uat-host")) {
            throw new IllegalStateException(
                    "Environment '" + envName + "' is using a placeholder host for " + key + ": " + host + ". "
                            + "Replace it with the real service host or override " + toEnvVarName(key) + ".");
        }
    }

    private static String toEnvVarName(String propertyKey) {
        return propertyKey.toUpperCase().replace('.', '_').replace('-', '_');
    }

    private static boolean isLoopbackHost(String host) {
        String normalizedHost = host.trim().toLowerCase();
        return "localhost".equals(normalizedHost) || "127.0.0.1".equals(normalizedHost) || "::1".equals(normalizedHost);
    }

    private static int resolvePort(URI uri) {
        if (uri.getPort() > 0) {
            return uri.getPort();
        }
        if ("https".equalsIgnoreCase(uri.getScheme())) {
            return 443;
        }
        if ("http".equalsIgnoreCase(uri.getScheme())) {
            return 80;
        }
        throw new IllegalStateException("Unable to determine port for URI: " + uri);
    }

    private static boolean isTcpReachable(String host, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (IOException exception) {
            LOGGER.warn("Connectivity check failed for {}:{}", host, port);
            return false;
        }
    }

    private String getRequired(String key) {
        String value = properties.getProperty(key);
        if (!isNotBlank(value)) {
            throw new IllegalStateException("Missing required configuration key: " + key);
        }
        return value.trim();
    }

    public String environmentName() {
        return getRequired("env.name");
    }

    public String applicationContext() {
        return getRequired("api.app-context");
    }

    public String apiBaseUrl() {
        return getRequired("api.base-url");
    }

    public String uiBaseUrl() {
        return getRequired("ui.base-url");
    }

    public String uiUsername() {
        return getRequired("ui.username");
    }

    public String uiPassword() {
        return getRequired("ui.password");
    }

    public String dbUrl() {
        return getRequired("db.url");
    }

    public String dbUser() {
        return getRequired("db.user");
    }

    public String dbPassword() {
        return getRequired("db.password");
    }

    public boolean browserHeadless() {
        return Boolean.parseBoolean(getRequired("browser.headless"));
    }

    public long browserWarmupDelayMs() {
        return getLong("browser.warmup.delay-ms");
    }

    public long lmdInitialDelayMs() {
        return getLong("workflow.lmd.initial-delay-ms");
    }

    public int lmdRetries() {
        return getInt("workflow.lmd.retries");
    }

    public long lmdRetryIntervalMs() {
        return getLong("workflow.lmd.retry-interval-ms");
    }

    public int biometricsRetries() {
        return getInt("workflow.biometrics.retries");
    }

    public long biometricsRetryIntervalMs() {
        return getLong("workflow.biometrics.retry-interval-ms");
    }

    public int comptelRetries() {
        return getInt("workflow.comptel.retries");
    }

    public long comptelRetryIntervalMs() {
        return getLong("workflow.comptel.retry-interval-ms");
    }

    public long comptelTimeoutMs() {
        return getLong("workflow.comptel.timeout-ms");
    }

    public long provisioningInitialDelayMs() {
        return getLong("workflow.provisioning.initial-delay-ms");
    }

    public long provisioningTimeoutMs() {
        return getLong("workflow.provisioning.timeout-ms");
    }

    public long provisioningRetryIntervalMs() {
        return getLong("workflow.provisioning.retry-interval-ms");
    }

    public long manualTaskTimeoutMs() {
        return getLong("workflow.manual-task.timeout-ms");
    }

    public long manualTaskPollIntervalMs() {
        return getLong("workflow.manual-task.poll-interval-ms");
    }

    public long uiPostLoginDelayMs() {
        return getLong("ui.post-login-delay-ms");
    }

    public long uiTaskSearchDelayMs() {
        return getLong("ui.task-search-delay-ms");
    }

    public long uiActionDelayMs() {
        return getLong("ui.action-delay-ms");
    }

    private int getInt(String key) {
        return Integer.parseInt(getRequired(key));
    }

    private long getLong(String key) {
        return Long.parseLong(getRequired(key));
    }
}
