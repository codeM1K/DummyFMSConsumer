package com.fms.consumer.service;

import com.fms.consumer.integration.WebSocketClientPool;
import com.fms.consumer.model.ConsumptionMode;
import com.fms.consumer.model.ConsumptionSession;
import com.fms.consumer.model.Vehicle;
import com.fms.consumer.model.VehicleStatus;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.AfterProperty;
import net.jqwik.api.lifecycle.BeforeProperty;
import net.jqwik.api.lifecycle.BeforeTry;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for session resumption after network outage
 * in {@link ConsumptionOrchestrator}.
 *
 * <p><b>Validates: Requirements 12.3</b></p>
 *
 * <p>Property 36: Session Resumption After Outage —
 * "For any set of active consumption sessions when network connectivity is restored
 * after an outage, the system SHALL automatically resume all previously active sessions."</p>
 */
class SessionResumptionAfterOutagePropertyTest {

    private WebSocketClientPool clientPool;
    private MetricsCollector metricsCollector;
    private DiscoveryService discoveryService;
    private ConfigurationService configService;
    private ConsumptionOrchestrator orchestrator;

    @BeforeProperty
    void setUp() {
        metricsCollector = new MetricsCollector();
    }

    @AfterProperty
    void tearDown() {
        if (metricsCollector != null) {
            metricsCollector.shutdown();
        }
    }

    @BeforeTry
    void setUpTry() {
        clientPool = mock(WebSocketClientPool.class);
        discoveryService = mock(DiscoveryService.class);
        configService = mock(ConfigurationService.class);

        when(configService.getSimulationMaxClients()).thenReturn(100);
        when(configService.getRetryInitialDelay()).thenReturn(1000);
        when(configService.getRetryMaxDelay()).thenReturn(30000);
        when(configService.getRetryMaxAttempts()).thenReturn(3);

        metricsCollector.reset();

        orchestrator = new ConsumptionOrchestrator(
                clientPool, metricsCollector, discoveryService, configService);
    }

    /**
     * For any set of vehicles with active consumption sessions, when suspendAllSessions()
     * is called (simulating outage) followed by resumeAllSessions() (simulating recovery),
     * all previously active sessions SHALL be resumed.
     *
     * <p><b>Validates: Requirements 12.3</b></p>
     */
    @Property(tries = 100)
    void allActiveSessionsResumedAfterOutage(
            @ForAll("vehicleSets") Set<Vehicle> vehicles,
            @ForAll("clientCounts") int clientCount) {

        // Setup: start controlled mode to create active sessions
        orchestrator.configureMultiClient(clientCount);
        orchestrator.startControlledMode(vehicles);

        // Capture the session keys that were active before the outage
        Map<String, ConsumptionSession> sessionsBeforeOutage = new HashMap<>(orchestrator.getActiveSessions());
        int sessionCountBeforeOutage = sessionsBeforeOutage.size();
        assertEquals(vehicles.size() * clientCount, sessionCountBeforeOutage,
                "Expected " + (vehicles.size() * clientCount) + " active sessions before outage");

        // Simulate network outage: suspend all sessions
        orchestrator.suspendAllSessions();

        // After suspension, sessions should be in suspended state
        Map<String, ConsumptionSession> suspendedSessions = orchestrator.getSuspendedSessions();
        assertEquals(sessionCountBeforeOutage, suspendedSessions.size(),
                "All sessions should be suspended during outage");

        // Reset mock invocation count to track only resume-related calls
        reset(clientPool);

        // Simulate network recovery: resume all sessions
        orchestrator.resumeAllSessions();

        // Verify: all previously active sessions are resumed
        Map<String, ConsumptionSession> sessionsAfterResume = orchestrator.getActiveSessions();
        assertEquals(sessionCountBeforeOutage, sessionsAfterResume.size(),
                "All previously active sessions should be resumed after recovery");

        // Verify each original session key is present after resumption
        for (String sessionKey : sessionsBeforeOutage.keySet()) {
            assertTrue(sessionsAfterResume.containsKey(sessionKey),
                    "Session '" + sessionKey + "' should be resumed after outage recovery. " +
                            "Available: " + sessionsAfterResume.keySet());
        }

        // Verify WebSocket connections are re-established for each session
        for (Vehicle vehicle : vehicles) {
            for (int i = 1; i <= clientCount; i++) {
                verify(clientPool).createConnection(eq(vehicle), eq("client_" + i));
            }
        }

        // Total connection re-establishments should match total sessions
        verify(clientPool, times(sessionCountBeforeOutage))
                .createConnection(any(Vehicle.class), anyString());
    }

    /**
     * For any set of active sessions, after suspend and resume, the suspended sessions
     * map SHALL be empty (all moved back to active).
     *
     * <p><b>Validates: Requirements 12.3</b></p>
     */
    @Property(tries = 100)
    void suspendedSessionsClearedAfterResumption(
            @ForAll("vehicleSets") Set<Vehicle> vehicles,
            @ForAll("clientCounts") int clientCount) {

        orchestrator.configureMultiClient(clientCount);
        orchestrator.startControlledMode(vehicles);

        // Simulate outage and recovery
        orchestrator.suspendAllSessions();
        orchestrator.resumeAllSessions();

        // Suspended sessions should be empty after resumption
        Map<String, ConsumptionSession> suspended = orchestrator.getSuspendedSessions();
        assertTrue(suspended.isEmpty(),
                "Suspended sessions should be empty after resumption, but found: " + suspended.keySet());
    }

    /**
     * For any set of active sessions, after suspend and resume, each resumed session
     * SHALL be marked as active again.
     *
     * <p><b>Validates: Requirements 12.3</b></p>
     */
    @Property(tries = 100)
    void resumedSessionsAreMarkedActive(
            @ForAll("vehicleSets") Set<Vehicle> vehicles,
            @ForAll("clientCounts") int clientCount) {

        orchestrator.configureMultiClient(clientCount);
        orchestrator.startControlledMode(vehicles);

        // Simulate outage and recovery
        orchestrator.suspendAllSessions();
        orchestrator.resumeAllSessions();

        // All resumed sessions should be marked active
        Map<String, ConsumptionSession> sessions = orchestrator.getActiveSessions();
        for (Map.Entry<String, ConsumptionSession> entry : sessions.entrySet()) {
            assertTrue(entry.getValue().isActive(),
                    "Session '" + entry.getKey() + "' should be active after resumption");
        }
    }

    /**
     * For any set of active sessions, connections SHALL be closed during suspension
     * (simulating outage disconnection) and re-created during resumption.
     *
     * <p><b>Validates: Requirements 12.3</b></p>
     */
    @Property(tries = 100)
    void connectionsClosedDuringSuspensionAndRecreatedOnResumption(
            @ForAll("vehicleSets") Set<Vehicle> vehicles,
            @ForAll("clientCounts") int clientCount) {

        orchestrator.configureMultiClient(clientCount);
        orchestrator.startControlledMode(vehicles);

        int totalSessions = vehicles.size() * clientCount;

        // Reset to only track suspend/resume interactions
        reset(clientPool);

        // Suspend: connections should be closed
        orchestrator.suspendAllSessions();

        // Verify connections were closed during suspension
        verify(clientPool, times(totalSessions))
                .closeConnection(anyString(), anyString());

        // Resume: connections should be re-created
        orchestrator.resumeAllSessions();

        // Verify connections were re-created during resumption
        verify(clientPool, times(totalSessions))
                .createConnection(any(Vehicle.class), anyString());
    }

    // --- Generators ---

    /**
     * Generates non-empty sets of vehicles with unique IDs (1 to 5 vehicles).
     */
    @Provide
    Arbitrary<Set<Vehicle>> vehicleSets() {
        Arbitrary<Vehicle> vehicleArbitrary = Combinators.combine(
                vehicleIds(),
                vehicleNames(),
                realmIds()
        ).as((id, name, realmId) -> new Vehicle(id, name, realmId, VehicleStatus.ACTIVE));

        return vehicleArbitrary.set().ofMinSize(1).ofMaxSize(5);
    }

    /**
     * Generates client counts between 1 and 4 to test single and multi-client scenarios.
     */
    @Provide
    Arbitrary<Integer> clientCounts() {
        return Arbitraries.integers().between(1, 4);
    }

    private Arbitrary<String> vehicleIds() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(4)
                .ofMaxLength(10)
                .map(s -> "v-" + s);
    }

    private Arbitrary<String> vehicleNames() {
        return Arbitraries.of(
                "Fleet Truck 1", "Fleet Truck 2", "Fleet Truck 3",
                "City Bus Alpha", "City Bus Beta", "City Bus Gamma",
                "Delivery Van A", "Delivery Van B",
                "Heavy Loader X", "Light Cargo Y"
        );
    }

    private Arbitrary<String> realmIds() {
        return Arbitraries.of(
                "realm-1", "realm-2", "realm-3",
                "production", "staging",
                "fleet-management", "eu-west-1"
        );
    }
}
