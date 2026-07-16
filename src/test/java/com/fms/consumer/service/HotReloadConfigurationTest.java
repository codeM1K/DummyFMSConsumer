package com.fms.consumer.service;

import com.fms.consumer.config.OpenRemoteProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for hot configuration reload in {@link ConfigurationService}.
 * Validates Requirements 13.4 (configuration updates without restart) and
 * 13.5 (fallback to defaults on invalid configuration).
 */
class HotReloadConfigurationTest {

    private OpenRemoteProperties properties;
    private ConfigurationService service;

    @BeforeEach
    void setUp() {
        properties = new OpenRemoteProperties();
        service = new ConfigurationService(properties);
    }

    // --- Scenario 1: After changing properties and calling reloadConfiguration(), new values are returned ---

    @Test
    @DisplayName("After changing API endpoint and reloading, new value is returned")
    void reloadConfiguration_returnsNewEndpoint_afterPropertyChange() {
        // Verify initial default
        assertEquals("https://fms.pcp.com.gr", service.getApiEndpoint());

        // Simulate external config change
        properties.getApi().setEndpoint("https://new-api.example.com");
        service.reloadConfiguration();

        assertEquals("https://new-api.example.com", service.getApiEndpoint());
    }

    @Test
    @DisplayName("After changing username and reloading, new value is returned")
    void reloadConfiguration_returnsNewUsername_afterPropertyChange() {
        assertEquals("", service.getUsername());

        properties.getApi().setUsername("new-user");
        service.reloadConfiguration();

        assertEquals("new-user", service.getUsername());
    }

    @Test
    @DisplayName("After changing auth token and reloading, new value is returned")
    void reloadConfiguration_returnsNewToken_afterPropertyChange() {
        assertEquals("", service.getAuthToken());

        properties.getApi().setToken("new-secret-token-12345");
        service.reloadConfiguration();

        assertEquals("new-secret-token-12345", service.getAuthToken());
    }

    @Test
    @DisplayName("After changing refresh intervals and reloading, new values are returned")
    void reloadConfiguration_returnsNewRefreshIntervals_afterPropertyChange() {
        properties.getRefresh().setRealms(120);
        properties.getRefresh().setVehicles(90);
        properties.getRefresh().setMetrics(5);
        service.reloadConfiguration();

        assertEquals(120, service.getRealmRefreshInterval());
        assertEquals(90, service.getVehicleRefreshInterval());
        assertEquals(5, service.getMetricsRefreshInterval());
    }

    @Test
    @DisplayName("After changing connection timeouts and reloading, new values are returned")
    void reloadConfiguration_returnsNewTimeouts_afterPropertyChange() {
        properties.getConnection().setTimeout(10000);
        properties.getConnection().setEstablishment(4000);
        service.reloadConfiguration();

        assertEquals(10000, service.getConnectionTimeout());
        assertEquals(4000, service.getConnectionEstablishmentTimeout());
    }

    // --- Scenario 2: After changing to invalid values and calling reloadConfiguration(), defaults are returned ---

    @Test
    @DisplayName("After setting invalid endpoint and reloading, default is returned")
    void reloadConfiguration_returnsDefault_whenEndpointInvalid() {
        properties.getApi().setEndpoint("not-a-url");
        service.reloadConfiguration();

        assertEquals("https://fms.pcp.com.gr", service.getApiEndpoint());
    }

    @Test
    @DisplayName("After setting blank username and reloading, default is returned")
    void reloadConfiguration_returnsDefault_whenUsernameBlank() {
        properties.getApi().setUsername("   ");
        service.reloadConfiguration();

        assertEquals("", service.getUsername());
    }

    @Test
    @DisplayName("After setting null token and reloading, default is returned")
    void reloadConfiguration_returnsDefault_whenTokenNull() {
        properties.getApi().setToken(null);
        service.reloadConfiguration();

        assertEquals("", service.getAuthToken());
    }

    @Test
    @DisplayName("After setting zero refresh interval and reloading, default is returned")
    void reloadConfiguration_returnsDefault_whenRefreshIntervalZero() {
        properties.getRefresh().setRealms(0);
        service.reloadConfiguration();

        assertEquals(60, service.getRealmRefreshInterval());
    }

    @Test
    @DisplayName("After setting negative timeout and reloading, default is returned")
    void reloadConfiguration_returnsDefault_whenTimeoutNegative() {
        properties.getConnection().setTimeout(-500);
        service.reloadConfiguration();

        assertEquals(5000, service.getConnectionTimeout());
    }

    // --- Scenario 3: Multiple consecutive reloads work correctly ---

    @Test
    @DisplayName("Multiple consecutive reloads each pick up the latest property values")
    void reloadConfiguration_multipleConsecutiveReloads_eachReturnsLatestValue() {
        // First reload with one endpoint
        properties.getApi().setEndpoint("https://first.example.com");
        service.reloadConfiguration();
        assertEquals("https://first.example.com", service.getApiEndpoint());

        // Second reload with different endpoint
        properties.getApi().setEndpoint("https://second.example.com");
        service.reloadConfiguration();
        assertEquals("https://second.example.com", service.getApiEndpoint());

        // Third reload with yet another endpoint
        properties.getApi().setEndpoint("https://third.example.com");
        service.reloadConfiguration();
        assertEquals("https://third.example.com", service.getApiEndpoint());
    }

    @Test
    @DisplayName("Multiple reloads with alternating valid and invalid values work correctly")
    void reloadConfiguration_multipleReloads_alternatingValidInvalid() {
        // Valid value
        properties.getRefresh().setRealms(120);
        service.reloadConfiguration();
        assertEquals(120, service.getRealmRefreshInterval());

        // Invalid value - falls back to default
        properties.getRefresh().setRealms(-1);
        service.reloadConfiguration();
        assertEquals(60, service.getRealmRefreshInterval());

        // Valid value again
        properties.getRefresh().setRealms(300);
        service.reloadConfiguration();
        assertEquals(300, service.getRealmRefreshInterval());
    }

    // --- Scenario 4: Changing one property doesn't affect others ---

    @Test
    @DisplayName("Changing API endpoint does not affect username or token")
    void reloadConfiguration_changingEndpoint_doesNotAffectOtherProperties() {
        properties.getApi().setEndpoint("https://changed.example.com");
        service.reloadConfiguration();

        assertEquals("https://changed.example.com", service.getApiEndpoint());
        assertEquals("", service.getUsername());
        assertEquals("", service.getAuthToken());
    }

    @Test
    @DisplayName("Changing refresh intervals does not affect connection timeouts")
    void reloadConfiguration_changingRefreshIntervals_doesNotAffectTimeouts() {
        properties.getRefresh().setRealms(200);
        properties.getRefresh().setVehicles(150);
        service.reloadConfiguration();

        assertEquals(200, service.getRealmRefreshInterval());
        assertEquals(150, service.getVehicleRefreshInterval());
        // Connection timeouts remain at defaults
        assertEquals(5000, service.getConnectionTimeout());
        assertEquals(2000, service.getConnectionEstablishmentTimeout());
    }

    @Test
    @DisplayName("Changing retry config does not affect simulation config")
    void reloadConfiguration_changingRetryConfig_doesNotAffectSimulation() {
        properties.getRetry().setMaxAttempts(10);
        properties.getRetry().setInitialDelay(5000);
        service.reloadConfiguration();

        assertEquals(10, service.getRetryMaxAttempts());
        assertEquals(5000, service.getRetryInitialDelay());
        // Simulation values remain at defaults
        assertEquals(1, service.getSimulationDefaultClients());
        assertEquals(100, service.getSimulationMaxClients());
    }

    // --- Scenario 5: Reload from valid to invalid falls back to default ---

    @Test
    @DisplayName("Changing from valid endpoint to invalid falls back to default")
    void reloadConfiguration_validToInvalidEndpoint_fallsBackToDefault() {
        // Set valid value first
        properties.getApi().setEndpoint("https://valid.example.com");
        service.reloadConfiguration();
        assertEquals("https://valid.example.com", service.getApiEndpoint());

        // Change to invalid value
        properties.getApi().setEndpoint("://broken");
        service.reloadConfiguration();
        assertEquals("https://fms.pcp.com.gr", service.getApiEndpoint());
    }

    @Test
    @DisplayName("Changing from valid username to blank falls back to default")
    void reloadConfiguration_validToBlankUsername_fallsBackToDefault() {
        properties.getApi().setUsername("custom-user");
        service.reloadConfiguration();
        assertEquals("custom-user", service.getUsername());

        properties.getApi().setUsername("");
        service.reloadConfiguration();
        assertEquals("", service.getUsername());
    }

    @Test
    @DisplayName("Changing from valid retry max delay to zero falls back to default")
    void reloadConfiguration_validToInvalidRetryMaxDelay_fallsBackToDefault() {
        properties.getRetry().setMaxDelay(60000);
        service.reloadConfiguration();
        assertEquals(60000, service.getRetryMaxDelay());

        properties.getRetry().setMaxDelay(0);
        service.reloadConfiguration();
        assertEquals(30000, service.getRetryMaxDelay());
    }

    // --- Scenario 6: Reload from invalid to valid returns the new valid value ---

    @Test
    @DisplayName("Changing from invalid endpoint to valid returns the new value")
    void reloadConfiguration_invalidToValidEndpoint_returnsNewValue() {
        // Set invalid first
        properties.getApi().setEndpoint("garbage");
        service.reloadConfiguration();
        assertEquals("https://fms.pcp.com.gr", service.getApiEndpoint());

        // Set valid value
        properties.getApi().setEndpoint("https://restored.example.com");
        service.reloadConfiguration();
        assertEquals("https://restored.example.com", service.getApiEndpoint());
    }

    @Test
    @DisplayName("Changing from invalid (null) token to valid returns the new value")
    void reloadConfiguration_invalidToValidToken_returnsNewValue() {
        properties.getApi().setToken(null);
        service.reloadConfiguration();
        assertEquals("", service.getAuthToken());

        properties.getApi().setToken("restored-token-abc");
        service.reloadConfiguration();
        assertEquals("restored-token-abc", service.getAuthToken());
    }

    @Test
    @DisplayName("Changing from negative timeout to valid returns the new value")
    void reloadConfiguration_invalidToValidTimeout_returnsNewValue() {
        properties.getConnection().setTimeout(-999);
        service.reloadConfiguration();
        assertEquals(5000, service.getConnectionTimeout());

        properties.getConnection().setTimeout(8000);
        service.reloadConfiguration();
        assertEquals(8000, service.getConnectionTimeout());
    }

    @Test
    @DisplayName("Changing from zero simulation clients to valid returns the new value")
    void reloadConfiguration_invalidToValidSimulationClients_returnsNewValue() {
        properties.getSimulation().setMaxClients(0);
        service.reloadConfiguration();
        assertEquals(100, service.getSimulationMaxClients());

        properties.getSimulation().setMaxClients(50);
        service.reloadConfiguration();
        assertEquals(50, service.getSimulationMaxClients());
    }
}
