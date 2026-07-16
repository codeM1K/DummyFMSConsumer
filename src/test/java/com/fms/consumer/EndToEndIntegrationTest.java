package com.fms.consumer;

import com.fms.consumer.config.OpenRemoteProperties;
import com.fms.consumer.integration.LocationDataHandler;
import com.fms.consumer.integration.OpenRemoteRestClient;
import com.fms.consumer.integration.WebSocketClientPool;
import com.fms.consumer.model.*;
import com.fms.consumer.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * End-to-end integration tests verifying the complete component wiring
 * from authentication through discovery to consumption and metrics.
 * <p>
 * These tests manually wire up the components (bypassing Spring context) to verify
 * the dependency chain works correctly.
 * <p>
 * Requirements: All requirements (19.1, 19.2)
 */
class EndToEndIntegrationTest {

    private ConfigurationService configurationService;
    private MetricsCollector metricsCollector;
    private LocationDataHandler locationDataHandler;
    private AuthenticationService authenticationService;
    private DiscoveryService discoveryService;
    private ConsumptionOrchestrator consumptionOrchestrator;
    private WebSocketClientPool webSocketClientPool;
    private OpenRemoteRestClient restClient;

    @BeforeEach
    void setUp() {
        // Create real instances with mocked external dependencies
        OpenRemoteProperties properties = createTestProperties();
        configurationService = new ConfigurationService(properties);
        metricsCollector = new MetricsCollector();
        locationDataHandler = new LocationDataHandler();

        // Mock the REST client (we don't want to call real API)
        restClient = mock(OpenRemoteRestClient.class);

        authenticationService = new AuthenticationService(configurationService, restClient);
        discoveryService = new DiscoveryService(authenticationService, restClient, configurationService);
        webSocketClientPool = new WebSocketClientPool(locationDataHandler, configurationService);
        consumptionOrchestrator = new ConsumptionOrchestrator(
                webSocketClientPool, metricsCollector, discoveryService, configurationService);
    }

    // --- Task 19.1: Verify all components are wired together ---

    @Test
    void authenticationServiceIntegration_isWiredToRestClient() {
        assertNotNull(authenticationService);
        assertFalse(authenticationService.isAuthenticated());
    }

    @Test
    void discoveryServiceIntegration_isWiredToAuthAndRestClient() {
        assertNotNull(discoveryService);
        List<Realm> cachedRealms = discoveryService.getCachedRealms();
        assertNotNull(cachedRealms);
        assertTrue(cachedRealms.isEmpty(), "Initially no realms should be cached");
    }

    @Test
    void consumptionOrchestratorIntegration_isWiredToPoolAndMetrics() {
        assertNotNull(consumptionOrchestrator);
        assertEquals(ConsumptionMode.IDLE, consumptionOrchestrator.getCurrentMode());
        assertEquals(1, consumptionOrchestrator.getClientCount());
        assertTrue(consumptionOrchestrator.getActiveSessions().isEmpty());
    }

    @Test
    void metricsCollectorIntegration_providesInitialMetrics() {
        assertNotNull(metricsCollector);
        ConsumptionMetrics metrics = metricsCollector.getAggregateMetrics();
        assertNotNull(metrics);
        assertEquals(0, metrics.getActiveConnections());
        assertEquals(0, metrics.getActiveVehicles());
        assertEquals(0, metrics.getActiveRealms());
    }

    @Test
    void locationDataHandlerIntegration_canParseAndNotify() {
        assertNotNull(locationDataHandler);
        LocationData data = locationDataHandler.parse(
                "{\"vehicleId\":\"v1\",\"latitude\":38.5,\"longitude\":23.5,\"timestamp\":1700000000000}");
        assertNotNull(data);
        assertEquals("v1", data.getVehicleId());
        assertEquals(38.5, data.getLatitude(), 0.001);
        assertEquals(23.5, data.getLongitude(), 0.001);
    }

    @Test
    void configurationServiceIntegration_readsAllProperties() {
        assertNotNull(configurationService);
        assertEquals("https://fms.pcp.com.gr", configurationService.getApiEndpoint());
        assertEquals("alamanos-test", configurationService.getUsername());
        assertEquals("hw33qKdc9iCfNvcHm6zaDE1v5bJjndVc", configurationService.getAuthToken());
        assertEquals(60, configurationService.getRealmRefreshInterval());
        assertEquals(60, configurationService.getVehicleRefreshInterval());
        assertEquals(1, configurationService.getMetricsRefreshInterval());
        assertEquals(5000, configurationService.getConnectionTimeout());
        assertEquals(2000, configurationService.getConnectionEstablishmentTimeout());
        assertEquals(3, configurationService.getRetryMaxAttempts());
        assertEquals(1000, configurationService.getRetryInitialDelay());
        assertEquals(30000, configurationService.getRetryMaxDelay());
        assertEquals(1, configurationService.getSimulationDefaultClients());
        assertEquals(100, configurationService.getSimulationMaxClients());
    }

    @Test
    void webSocketClientPoolIntegration_exists() {
        assertNotNull(webSocketClientPool);
        assertEquals(0, webSocketClientPool.getTotalConnectionCount());
    }

    // --- Task 19.2: End-to-end flow tests ---

    @Test
    void randomModeFlowTest_startsAndStopsCorrectly() {
        // With no cached vehicles, starting random mode should not throw
        consumptionOrchestrator.startRandomMode();
        // Since no vehicles are available, mode stays IDLE (no vehicles selected)
        // The orchestrator handles empty vehicle lists gracefully
        consumptionOrchestrator.stopRandomMode();
        assertEquals(ConsumptionMode.IDLE, consumptionOrchestrator.getCurrentMode());
    }

    @Test
    void controlledModeFlowTest_handlesEmptySelection() {
        consumptionOrchestrator.startControlledMode(Collections.emptySet());
        assertEquals(ConsumptionMode.IDLE, consumptionOrchestrator.getCurrentMode());
    }

    @Test
    void multiClientConfigurationFlow_worksCorrectly() {
        assertEquals(1, consumptionOrchestrator.getClientCount());

        consumptionOrchestrator.configureMultiClient(5);
        assertEquals(5, consumptionOrchestrator.getClientCount());

        consumptionOrchestrator.configureMultiClient(1);
        assertEquals(1, consumptionOrchestrator.getClientCount());
    }

    @Test
    void multiClientConfigurationFlow_rejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class,
                () -> consumptionOrchestrator.configureMultiClient(0));
        assertThrows(IllegalArgumentException.class,
                () -> consumptionOrchestrator.configureMultiClient(101));
    }

    @Test
    void metricsCollectorFlow_recordsAndReportsCorrectly() {
        metricsCollector.reset();

        metricsCollector.recordConnectionChange(1);
        metricsCollector.recordVehicleActive("vehicle-1");
        metricsCollector.recordRealmActive("realm-1");
        metricsCollector.recordLocationUpdate("vehicle-1", "client_1");

        ConsumptionMetrics metrics = metricsCollector.getAggregateMetrics();
        assertEquals(1, metrics.getActiveConnections());
        assertEquals(1, metrics.getActiveVehicles());
        assertEquals(1, metrics.getActiveRealms());
        assertTrue(metricsCollector.getTotalUpdatesReceived() >= 1);

        metricsCollector.reset();
    }

    @Test
    void errorRecoveryFlow_suspendAndResume() {
        consumptionOrchestrator.suspendAllSessions();
        assertTrue(consumptionOrchestrator.getSuspendedSessions().isEmpty());

        consumptionOrchestrator.resumeAllSessions();
        assertTrue(consumptionOrchestrator.getActiveSessions().isEmpty());
    }

    @Test
    void locationDataHandlerFlow_parsesAndNotifiesListeners() {
        List<LocationData> received = new ArrayList<>();
        locationDataHandler.addListener(received::add);

        String json = "{\"vehicleId\":\"test-vehicle\",\"latitude\":39.0,\"longitude\":22.0,\"timestamp\":1700000000000}";
        LocationData parsed = locationDataHandler.parse(json);
        assertNotNull(parsed);
        locationDataHandler.notifyListeners(parsed);

        assertEquals(1, received.size());
        assertEquals("test-vehicle", received.get(0).getVehicleId());
    }

    @Test
    void configReloadFlow_doesNotThrow() {
        assertDoesNotThrow(() -> configurationService.reloadConfiguration());
    }

    @Test
    void fullAuthenticationToDiscoveryFlow() {
        // Mock successful authentication
        com.fms.consumer.integration.dto.AuthResponse authResponse =
                new com.fms.consumer.integration.dto.AuthResponse("test-token", true, null);
        when(restClient.authenticate(anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(authResponse));

        // Authenticate
        AuthenticationResult result = authenticationService.authenticate().join();
        assertTrue(result.isSuccess());
        assertEquals("test-token", result.getSessionToken());
        assertTrue(authenticationService.isAuthenticated());
    }

    @Test
    void fullDiscoveryFlow() {
        // Set up auth token first
        com.fms.consumer.integration.dto.AuthResponse authResponse =
                new com.fms.consumer.integration.dto.AuthResponse("test-token", true, null);
        when(restClient.authenticate(anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(authResponse));
        authenticationService.authenticate().join();

        // Mock realm discovery
        List<com.fms.consumer.integration.dto.RealmDTO> realmDTOs = List.of(
                new com.fms.consumer.integration.dto.RealmDTO("realm-1", "Test Realm"));
        when(restClient.getRealms("test-token"))
                .thenReturn(CompletableFuture.completedFuture(realmDTOs));

        // Discover realms
        List<Realm> realms = discoveryService.discoverRealms().join();
        assertEquals(1, realms.size());
        assertEquals("realm-1", realms.get(0).getId());
    }

    // --- Helper methods ---

    private OpenRemoteProperties createTestProperties() {
        OpenRemoteProperties props = new OpenRemoteProperties();

        OpenRemoteProperties.Api api = new OpenRemoteProperties.Api();
        api.setEndpoint("https://fms.pcp.com.gr");
        api.setUsername("alamanos-test");
        api.setToken("hw33qKdc9iCfNvcHm6zaDE1v5bJjndVc");
        props.setApi(api);

        OpenRemoteProperties.Refresh refresh = new OpenRemoteProperties.Refresh();
        refresh.setRealms(60);
        refresh.setVehicles(60);
        refresh.setMetrics(1);
        props.setRefresh(refresh);

        OpenRemoteProperties.Connection connection = new OpenRemoteProperties.Connection();
        connection.setTimeout(5000);
        connection.setEstablishment(2000);
        props.setConnection(connection);

        OpenRemoteProperties.Retry retry = new OpenRemoteProperties.Retry();
        retry.setMaxAttempts(3);
        retry.setInitialDelay(1000);
        retry.setMaxDelay(30000);
        props.setRetry(retry);

        OpenRemoteProperties.Simulation simulation = new OpenRemoteProperties.Simulation();
        simulation.setDefaultClients(1);
        simulation.setMaxClients(100);
        props.setSimulation(simulation);

        return props;
    }
}
