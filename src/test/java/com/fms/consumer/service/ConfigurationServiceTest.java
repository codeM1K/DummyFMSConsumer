package com.fms.consumer.service;

import com.fms.consumer.config.OpenRemoteProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ConfigurationService}.
 * Tests configuration reading, validation, and fallback to defaults.
 */
class ConfigurationServiceTest {

    private OpenRemoteProperties properties;
    private ConfigurationService service;

    @BeforeEach
    void setUp() {
        properties = new OpenRemoteProperties();
        service = new ConfigurationService(properties);
    }

    // --- API Endpoint Tests ---

    @Test
    void getApiEndpoint_returnsConfiguredEndpoint_whenValid() {
        properties.getApi().setEndpoint("https://custom.endpoint.com");
        assertEquals("https://custom.endpoint.com", service.getApiEndpoint());
    }

    @Test
    void getApiEndpoint_returnsDefault_whenNull() {
        properties.getApi().setEndpoint(null);
        assertEquals("https://fms.pcp.com.gr", service.getApiEndpoint());
    }

    @Test
    void getApiEndpoint_returnsDefault_whenBlank() {
        properties.getApi().setEndpoint("   ");
        assertEquals("https://fms.pcp.com.gr", service.getApiEndpoint());
    }

    @Test
    void getApiEndpoint_returnsDefault_whenInvalidUrl() {
        properties.getApi().setEndpoint("not-a-valid-url");
        assertEquals("https://fms.pcp.com.gr", service.getApiEndpoint());
    }

    @Test
    void getApiEndpoint_returnsDefault_whenDefaultsUsed() {
        // Default value in OpenRemoteProperties is already the expected default
        assertEquals("https://fms.pcp.com.gr", service.getApiEndpoint());
    }

    // --- Username Tests ---

    @Test
    void getUsername_returnsConfiguredUsername_whenValid() {
        properties.getApi().setUsername("custom-user");
        assertEquals("custom-user", service.getUsername());
    }

    @Test
    void getUsername_returnsDefault_whenNull() {
        properties.getApi().setUsername(null);
        assertEquals("alamanos-test", service.getUsername());
    }

    @Test
    void getUsername_returnsDefault_whenBlank() {
        properties.getApi().setUsername("  ");
        assertEquals("alamanos-test", service.getUsername());
    }

    @Test
    void getUsername_returnsDefault_whenDefaultsUsed() {
        assertEquals("alamanos-test", service.getUsername());
    }

    // --- Auth Token Tests ---

    @Test
    void getAuthToken_returnsConfiguredToken_whenValid() {
        properties.getApi().setToken("custom-token-value");
        assertEquals("custom-token-value", service.getAuthToken());
    }

    @Test
    void getAuthToken_returnsDefault_whenNull() {
        properties.getApi().setToken(null);
        assertEquals("hw33qKdc9iCfNvcHm6zaDE1v5bJjndVc", service.getAuthToken());
    }

    @Test
    void getAuthToken_returnsDefault_whenBlank() {
        properties.getApi().setToken("");
        assertEquals("hw33qKdc9iCfNvcHm6zaDE1v5bJjndVc", service.getAuthToken());
    }

    // --- Refresh Interval Tests ---

    @Test
    void getRealmRefreshInterval_returnsConfiguredValue() {
        properties.getRefresh().setRealms(120);
        assertEquals(120, service.getRealmRefreshInterval());
    }

    @Test
    void getRealmRefreshInterval_returnsDefault_whenZero() {
        properties.getRefresh().setRealms(0);
        assertEquals(60, service.getRealmRefreshInterval());
    }

    @Test
    void getRealmRefreshInterval_returnsDefault_whenNegative() {
        properties.getRefresh().setRealms(-5);
        assertEquals(60, service.getRealmRefreshInterval());
    }

    @Test
    void getVehicleRefreshInterval_returnsConfiguredValue() {
        properties.getRefresh().setVehicles(90);
        assertEquals(90, service.getVehicleRefreshInterval());
    }

    @Test
    void getVehicleRefreshInterval_returnsDefault_whenInvalid() {
        properties.getRefresh().setVehicles(0);
        assertEquals(60, service.getVehicleRefreshInterval());
    }

    @Test
    void getMetricsRefreshInterval_returnsConfiguredValue() {
        properties.getRefresh().setMetrics(5);
        assertEquals(5, service.getMetricsRefreshInterval());
    }

    @Test
    void getMetricsRefreshInterval_returnsDefault_whenInvalid() {
        properties.getRefresh().setMetrics(-1);
        assertEquals(1, service.getMetricsRefreshInterval());
    }

    // --- Connection Timeout Tests ---

    @Test
    void getConnectionTimeout_returnsConfiguredValue() {
        properties.getConnection().setTimeout(10000);
        assertEquals(10000, service.getConnectionTimeout());
    }

    @Test
    void getConnectionTimeout_returnsDefault_whenInvalid() {
        properties.getConnection().setTimeout(0);
        assertEquals(5000, service.getConnectionTimeout());
    }

    @Test
    void getConnectionEstablishmentTimeout_returnsConfiguredValue() {
        properties.getConnection().setEstablishment(3000);
        assertEquals(3000, service.getConnectionEstablishmentTimeout());
    }

    @Test
    void getConnectionEstablishmentTimeout_returnsDefault_whenInvalid() {
        properties.getConnection().setEstablishment(-100);
        assertEquals(2000, service.getConnectionEstablishmentTimeout());
    }

    // --- Retry Configuration Tests ---

    @Test
    void getRetryMaxAttempts_returnsConfiguredValue() {
        properties.getRetry().setMaxAttempts(5);
        assertEquals(5, service.getRetryMaxAttempts());
    }

    @Test
    void getRetryMaxAttempts_returnsDefault_whenInvalid() {
        properties.getRetry().setMaxAttempts(0);
        assertEquals(3, service.getRetryMaxAttempts());
    }

    @Test
    void getRetryInitialDelay_returnsConfiguredValue() {
        properties.getRetry().setInitialDelay(2000);
        assertEquals(2000, service.getRetryInitialDelay());
    }

    @Test
    void getRetryInitialDelay_returnsDefault_whenInvalid() {
        properties.getRetry().setInitialDelay(-1);
        assertEquals(1000, service.getRetryInitialDelay());
    }

    @Test
    void getRetryMaxDelay_returnsConfiguredValue() {
        properties.getRetry().setMaxDelay(60000);
        assertEquals(60000, service.getRetryMaxDelay());
    }

    @Test
    void getRetryMaxDelay_returnsDefault_whenInvalid() {
        properties.getRetry().setMaxDelay(0);
        assertEquals(30000, service.getRetryMaxDelay());
    }

    // --- Simulation Configuration Tests ---

    @Test
    void getSimulationDefaultClients_returnsConfiguredValue() {
        properties.getSimulation().setDefaultClients(5);
        assertEquals(5, service.getSimulationDefaultClients());
    }

    @Test
    void getSimulationDefaultClients_returnsDefault_whenInvalid() {
        properties.getSimulation().setDefaultClients(0);
        assertEquals(1, service.getSimulationDefaultClients());
    }

    @Test
    void getSimulationMaxClients_returnsConfiguredValue() {
        properties.getSimulation().setMaxClients(200);
        assertEquals(200, service.getSimulationMaxClients());
    }

    @Test
    void getSimulationMaxClients_returnsDefault_whenInvalid() {
        properties.getSimulation().setMaxClients(-10);
        assertEquals(100, service.getSimulationMaxClients());
    }

    // --- Hot Reload Test ---

    @Test
    void reloadConfiguration_updatesValuesFromProperties() {
        // Initial value
        assertEquals("https://fms.pcp.com.gr", service.getApiEndpoint());

        // Simulate external configuration change
        properties.getApi().setEndpoint("https://new.endpoint.com");

        // After reload, new value should be returned
        service.reloadConfiguration();
        assertEquals("https://new.endpoint.com", service.getApiEndpoint());
    }

    @Test
    void reloadConfiguration_fallsBackToDefault_whenNewValueInvalid() {
        // Simulate external configuration change to invalid value
        properties.getApi().setEndpoint("invalid-url");

        service.reloadConfiguration();
        assertEquals("https://fms.pcp.com.gr", service.getApiEndpoint());
    }
}
