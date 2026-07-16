package com.fms.consumer.service;

import com.fms.consumer.config.OpenRemoteProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Service responsible for managing application configuration.
 * Reads configuration from application.properties via {@link OpenRemoteProperties},
 * validates values, and falls back to defaults when configuration is invalid.
 * Supports hot reload: the underlying {@link OpenRemoteProperties} bean is refreshed
 * by Spring when configuration changes, and {@link #reloadConfiguration()} can be
 * called programmatically to trigger re-validation and logging.
 */
@Service
public class ConfigurationService {

    private static final Logger log = LoggerFactory.getLogger(ConfigurationService.class);

    private static final String DEFAULT_ENDPOINT = "https://fms.pcp.com.gr";
    private static final String DEFAULT_USERNAME = "alamanos-test";
    private static final String DEFAULT_TOKEN = "hw33qKdc9iCfNvcHm6zaDE1v5bJjndVc";

    private static final int DEFAULT_REFRESH_REALMS = 60;
    private static final int DEFAULT_REFRESH_VEHICLES = 60;
    private static final int DEFAULT_REFRESH_METRICS = 1;

    private static final int DEFAULT_CONNECTION_TIMEOUT = 5000;
    private static final int DEFAULT_CONNECTION_ESTABLISHMENT = 2000;

    private static final int DEFAULT_RETRY_MAX_ATTEMPTS = 3;
    private static final int DEFAULT_RETRY_INITIAL_DELAY = 1000;
    private static final int DEFAULT_RETRY_MAX_DELAY = 30000;

    private static final int DEFAULT_SIMULATION_DEFAULT_CLIENTS = 1;
    private static final int DEFAULT_SIMULATION_MAX_CLIENTS = 100;

    private final OpenRemoteProperties properties;

    public ConfigurationService(OpenRemoteProperties properties) {
        this.properties = properties;
        log.info("ConfigurationService initialized");
        logCurrentConfiguration();
    }

    /**
     * Returns the API endpoint URL. Falls back to default if the configured value is invalid.
     */
    public String getApiEndpoint() {
        String endpoint = properties.getApi().getEndpoint();
        if (isValidUrl(endpoint)) {
            return endpoint;
        }
        log.error("Invalid API endpoint configured: '{}'. Falling back to default: {}", endpoint, DEFAULT_ENDPOINT);
        return DEFAULT_ENDPOINT;
    }

    /**
     * Returns the authentication username. Falls back to default if the configured value is empty.
     */
    public String getUsername() {
        String username = properties.getApi().getUsername();
        if (isNotBlank(username)) {
            return username;
        }
        log.error("Username is blank or null. Falling back to default: {}", DEFAULT_USERNAME);
        return DEFAULT_USERNAME;
    }

    /**
     * Returns the authentication token. Falls back to default if the configured value is empty.
     */
    public String getAuthToken() {
        String token = properties.getApi().getToken();
        if (isNotBlank(token)) {
            return token;
        }
        log.error("Authentication token is blank or null. Falling back to default.");
        return DEFAULT_TOKEN;
    }

    /**
     * Returns the realm refresh interval in seconds. Falls back to default if invalid.
     */
    public int getRealmRefreshInterval() {
        int value = properties.getRefresh().getRealms();
        if (isPositive(value)) {
            return value;
        }
        log.error("Invalid realm refresh interval: {}. Falling back to default: {}", value, DEFAULT_REFRESH_REALMS);
        return DEFAULT_REFRESH_REALMS;
    }

    /**
     * Returns the vehicle refresh interval in seconds. Falls back to default if invalid.
     */
    public int getVehicleRefreshInterval() {
        int value = properties.getRefresh().getVehicles();
        if (isPositive(value)) {
            return value;
        }
        log.error("Invalid vehicle refresh interval: {}. Falling back to default: {}", value, DEFAULT_REFRESH_VEHICLES);
        return DEFAULT_REFRESH_VEHICLES;
    }

    /**
     * Returns the metrics refresh interval in seconds. Falls back to default if invalid.
     */
    public int getMetricsRefreshInterval() {
        int value = properties.getRefresh().getMetrics();
        if (isPositive(value)) {
            return value;
        }
        log.error("Invalid metrics refresh interval: {}. Falling back to default: {}", value, DEFAULT_REFRESH_METRICS);
        return DEFAULT_REFRESH_METRICS;
    }

    /**
     * Returns the HTTP connection timeout in milliseconds. Falls back to default if invalid.
     */
    public int getConnectionTimeout() {
        int value = properties.getConnection().getTimeout();
        if (isPositive(value)) {
            return value;
        }
        log.error("Invalid connection timeout: {}. Falling back to default: {}", value, DEFAULT_CONNECTION_TIMEOUT);
        return DEFAULT_CONNECTION_TIMEOUT;
    }

    /**
     * Returns the WebSocket connection establishment timeout in milliseconds. Falls back to default if invalid.
     */
    public int getConnectionEstablishmentTimeout() {
        int value = properties.getConnection().getEstablishment();
        if (isPositive(value)) {
            return value;
        }
        log.error("Invalid connection establishment timeout: {}. Falling back to default: {}", value, DEFAULT_CONNECTION_ESTABLISHMENT);
        return DEFAULT_CONNECTION_ESTABLISHMENT;
    }

    /**
     * Returns the maximum number of retry attempts. Falls back to default if invalid.
     */
    public int getRetryMaxAttempts() {
        int value = properties.getRetry().getMaxAttempts();
        if (isPositive(value)) {
            return value;
        }
        log.error("Invalid retry max attempts: {}. Falling back to default: {}", value, DEFAULT_RETRY_MAX_ATTEMPTS);
        return DEFAULT_RETRY_MAX_ATTEMPTS;
    }

    /**
     * Returns the initial retry delay in milliseconds. Falls back to default if invalid.
     */
    public int getRetryInitialDelay() {
        int value = properties.getRetry().getInitialDelay();
        if (isPositive(value)) {
            return value;
        }
        log.error("Invalid retry initial delay: {}. Falling back to default: {}", value, DEFAULT_RETRY_INITIAL_DELAY);
        return DEFAULT_RETRY_INITIAL_DELAY;
    }

    /**
     * Returns the maximum retry delay in milliseconds. Falls back to default if invalid.
     */
    public int getRetryMaxDelay() {
        int value = properties.getRetry().getMaxDelay();
        if (isPositive(value)) {
            return value;
        }
        log.error("Invalid retry max delay: {}. Falling back to default: {}", value, DEFAULT_RETRY_MAX_DELAY);
        return DEFAULT_RETRY_MAX_DELAY;
    }

    /**
     * Returns the default number of simulated clients. Falls back to default if invalid.
     */
    public int getSimulationDefaultClients() {
        int value = properties.getSimulation().getDefaultClients();
        if (isPositive(value)) {
            return value;
        }
        log.error("Invalid simulation default clients: {}. Falling back to default: {}", value, DEFAULT_SIMULATION_DEFAULT_CLIENTS);
        return DEFAULT_SIMULATION_DEFAULT_CLIENTS;
    }

    /**
     * Returns the maximum number of simulated clients. Falls back to default if invalid.
     */
    public int getSimulationMaxClients() {
        int value = properties.getSimulation().getMaxClients();
        if (isPositive(value)) {
            return value;
        }
        log.error("Invalid simulation max clients: {}. Falling back to default: {}", value, DEFAULT_SIMULATION_MAX_CLIENTS);
        return DEFAULT_SIMULATION_MAX_CLIENTS;
    }

    /**
     * Triggers a reload of the configuration by re-reading the underlying properties.
     * In a Spring Cloud context, this is handled by the @RefreshScope annotation.
     * This method serves as an explicit reload trigger for programmatic use.
     */
    public void reloadConfiguration() {
        log.info("Configuration reload triggered");
        logCurrentConfiguration();
    }

    private void logCurrentConfiguration() {
        log.info("Current configuration - endpoint: {}, username: {}, realmRefresh: {}s, vehicleRefresh: {}s, metricsRefresh: {}s",
                getApiEndpoint(), getUsername(),
                getRealmRefreshInterval(), getVehicleRefreshInterval(), getMetricsRefreshInterval());
        log.info("Current configuration - connectionTimeout: {}ms, establishment: {}ms, retryMaxAttempts: {}, retryInitialDelay: {}ms, retryMaxDelay: {}ms",
                getConnectionTimeout(), getConnectionEstablishmentTimeout(),
                getRetryMaxAttempts(), getRetryInitialDelay(), getRetryMaxDelay());
        log.info("Current configuration - simulationDefaultClients: {}, simulationMaxClients: {}",
                getSimulationDefaultClients(), getSimulationMaxClients());
    }

    /**
     * Validates whether the given string is a valid URL.
     */
    private boolean isValidUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            new URL(url);
            return true;
        } catch (MalformedURLException e) {
            return false;
        }
    }

    /**
     * Checks that a string is not null and not blank.
     */
    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Checks that an integer is positive (greater than zero).
     */
    private boolean isPositive(int value) {
        return value > 0;
    }
}
