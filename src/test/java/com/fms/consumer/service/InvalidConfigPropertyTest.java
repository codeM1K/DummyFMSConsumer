package com.fms.consumer.service;

import com.fms.consumer.config.OpenRemoteProperties;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based test for invalid configuration handling.
 *
 * <p><b>Property 41: Invalid Configuration Handling</b></p>
 * <p><b>Validates: Requirements 13.5</b></p>
 *
 * <p>Property statement: "For any invalid configuration value, the system SHALL log the error
 * with details and fall back to using the default value for that configuration parameter."</p>
 */
class InvalidConfigPropertyTest {

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

    // --- Invalid Endpoint Property ---

    @Property
    void invalidEndpoint_fallsBackToDefault(@ForAll("invalidEndpoints") String invalidEndpoint) {
        OpenRemoteProperties properties = new OpenRemoteProperties();
        properties.getApi().setEndpoint(invalidEndpoint);

        ConfigurationService service = new ConfigurationService(properties);

        assertEquals(DEFAULT_ENDPOINT, service.getApiEndpoint(),
                "Invalid endpoint '" + invalidEndpoint + "' should fall back to default");
    }

    @Provide
    Arbitrary<String> invalidEndpoints() {
        return Arbitraries.oneOf(
                Arbitraries.just(null),
                Arbitraries.just(""),
                Arbitraries.just("   "),
                Arbitraries.just("\t"),
                Arbitraries.just("\n"),
                Arbitraries.just("not-a-url"),
                Arbitraries.just("ftp-missing-colon//example.com"),
                Arbitraries.just("just some words"),
                Arbitraries.just("://missing-scheme"),
                Arbitraries.just("http//missing-colon.com"),
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(50)
                        .filter(s -> !s.startsWith("http"))
        );
    }

    // --- Invalid Username Property ---

    @Property
    void invalidUsername_fallsBackToDefault(@ForAll("invalidUsernames") String invalidUsername) {
        OpenRemoteProperties properties = new OpenRemoteProperties();
        properties.getApi().setUsername(invalidUsername);

        ConfigurationService service = new ConfigurationService(properties);

        assertEquals(DEFAULT_USERNAME, service.getUsername(),
                "Invalid username '" + invalidUsername + "' should fall back to default");
    }

    @Provide
    Arbitrary<String> invalidUsernames() {
        return Arbitraries.oneOf(
                Arbitraries.just(null),
                Arbitraries.just(""),
                Arbitraries.just("   "),
                Arbitraries.just("\t"),
                Arbitraries.just("\n"),
                Arbitraries.just("  \t  \n  ")
        );
    }

    // --- Invalid Token Property ---

    @Property
    void invalidToken_fallsBackToDefault(@ForAll("invalidTokens") String invalidToken) {
        OpenRemoteProperties properties = new OpenRemoteProperties();
        properties.getApi().setToken(invalidToken);

        ConfigurationService service = new ConfigurationService(properties);

        assertEquals(DEFAULT_TOKEN, service.getAuthToken(),
                "Invalid token '" + invalidToken + "' should fall back to default");
    }

    @Provide
    Arbitrary<String> invalidTokens() {
        return Arbitraries.oneOf(
                Arbitraries.just(null),
                Arbitraries.just(""),
                Arbitraries.just("   "),
                Arbitraries.just("\t"),
                Arbitraries.just("\n"),
                Arbitraries.just("  \t  \n  ")
        );
    }

    // --- Non-positive Numeric Values Property ---

    @Property
    void nonPositiveRealmRefresh_fallsBackToDefault(@ForAll("nonPositiveIntegers") int invalidValue) {
        OpenRemoteProperties properties = new OpenRemoteProperties();
        properties.getRefresh().setRealms(invalidValue);

        ConfigurationService service = new ConfigurationService(properties);

        assertEquals(DEFAULT_REFRESH_REALMS, service.getRealmRefreshInterval(),
                "Non-positive realm refresh " + invalidValue + " should fall back to default");
    }

    @Property
    void nonPositiveVehicleRefresh_fallsBackToDefault(@ForAll("nonPositiveIntegers") int invalidValue) {
        OpenRemoteProperties properties = new OpenRemoteProperties();
        properties.getRefresh().setVehicles(invalidValue);

        ConfigurationService service = new ConfigurationService(properties);

        assertEquals(DEFAULT_REFRESH_VEHICLES, service.getVehicleRefreshInterval(),
                "Non-positive vehicle refresh " + invalidValue + " should fall back to default");
    }

    @Property
    void nonPositiveMetricsRefresh_fallsBackToDefault(@ForAll("nonPositiveIntegers") int invalidValue) {
        OpenRemoteProperties properties = new OpenRemoteProperties();
        properties.getRefresh().setMetrics(invalidValue);

        ConfigurationService service = new ConfigurationService(properties);

        assertEquals(DEFAULT_REFRESH_METRICS, service.getMetricsRefreshInterval(),
                "Non-positive metrics refresh " + invalidValue + " should fall back to default");
    }

    @Property
    void nonPositiveConnectionTimeout_fallsBackToDefault(@ForAll("nonPositiveIntegers") int invalidValue) {
        OpenRemoteProperties properties = new OpenRemoteProperties();
        properties.getConnection().setTimeout(invalidValue);

        ConfigurationService service = new ConfigurationService(properties);

        assertEquals(DEFAULT_CONNECTION_TIMEOUT, service.getConnectionTimeout(),
                "Non-positive connection timeout " + invalidValue + " should fall back to default");
    }

    @Property
    void nonPositiveConnectionEstablishment_fallsBackToDefault(@ForAll("nonPositiveIntegers") int invalidValue) {
        OpenRemoteProperties properties = new OpenRemoteProperties();
        properties.getConnection().setEstablishment(invalidValue);

        ConfigurationService service = new ConfigurationService(properties);

        assertEquals(DEFAULT_CONNECTION_ESTABLISHMENT, service.getConnectionEstablishmentTimeout(),
                "Non-positive connection establishment " + invalidValue + " should fall back to default");
    }

    @Property
    void nonPositiveRetryMaxAttempts_fallsBackToDefault(@ForAll("nonPositiveIntegers") int invalidValue) {
        OpenRemoteProperties properties = new OpenRemoteProperties();
        properties.getRetry().setMaxAttempts(invalidValue);

        ConfigurationService service = new ConfigurationService(properties);

        assertEquals(DEFAULT_RETRY_MAX_ATTEMPTS, service.getRetryMaxAttempts(),
                "Non-positive retry max attempts " + invalidValue + " should fall back to default");
    }

    @Property
    void nonPositiveRetryInitialDelay_fallsBackToDefault(@ForAll("nonPositiveIntegers") int invalidValue) {
        OpenRemoteProperties properties = new OpenRemoteProperties();
        properties.getRetry().setInitialDelay(invalidValue);

        ConfigurationService service = new ConfigurationService(properties);

        assertEquals(DEFAULT_RETRY_INITIAL_DELAY, service.getRetryInitialDelay(),
                "Non-positive retry initial delay " + invalidValue + " should fall back to default");
    }

    @Property
    void nonPositiveRetryMaxDelay_fallsBackToDefault(@ForAll("nonPositiveIntegers") int invalidValue) {
        OpenRemoteProperties properties = new OpenRemoteProperties();
        properties.getRetry().setMaxDelay(invalidValue);

        ConfigurationService service = new ConfigurationService(properties);

        assertEquals(DEFAULT_RETRY_MAX_DELAY, service.getRetryMaxDelay(),
                "Non-positive retry max delay " + invalidValue + " should fall back to default");
    }

    @Property
    void nonPositiveSimulationDefaultClients_fallsBackToDefault(@ForAll("nonPositiveIntegers") int invalidValue) {
        OpenRemoteProperties properties = new OpenRemoteProperties();
        properties.getSimulation().setDefaultClients(invalidValue);

        ConfigurationService service = new ConfigurationService(properties);

        assertEquals(DEFAULT_SIMULATION_DEFAULT_CLIENTS, service.getSimulationDefaultClients(),
                "Non-positive simulation default clients " + invalidValue + " should fall back to default");
    }

    @Property
    void nonPositiveSimulationMaxClients_fallsBackToDefault(@ForAll("nonPositiveIntegers") int invalidValue) {
        OpenRemoteProperties properties = new OpenRemoteProperties();
        properties.getSimulation().setMaxClients(invalidValue);

        ConfigurationService service = new ConfigurationService(properties);

        assertEquals(DEFAULT_SIMULATION_MAX_CLIENTS, service.getSimulationMaxClients(),
                "Non-positive simulation max clients " + invalidValue + " should fall back to default");
    }

    @Provide
    Arbitrary<Integer> nonPositiveIntegers() {
        return Arbitraries.oneOf(
                Arbitraries.just(0),
                Arbitraries.just(-1),
                Arbitraries.integers().between(Integer.MIN_VALUE, 0)
        );
    }
}
