package com.fms.consumer.service;

import com.fms.consumer.integration.WebSocketClientPool;
import com.fms.consumer.model.ConsumptionSession;
import com.fms.consumer.model.Vehicle;
import com.fms.consumer.model.VehicleStatus;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
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
 * Property-based tests for multi-client connection creation
 * in {@link ConsumptionOrchestrator}.
 *
 * <p><b>Validates: Requirements 7.2</b></p>
 *
 * <p>Property 18: Multi-Client Connection Creation —
 * "For any configured number of simulated clients, the system SHALL create
 * independent WebSocket connections for each client when multi-client mode is activated."</p>
 */
class MultiClientConnectionCreationPropertyTest {

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

        metricsCollector.reset();

        orchestrator = new ConsumptionOrchestrator(
                clientPool, metricsCollector, discoveryService, configService);
    }

    /**
     * For any configured number of clients (1-10) and any set of vehicles,
     * when startControlledMode is called, the total number of sessions created
     * SHALL equal vehicles.size() * clientCount.
     *
     * <p><b>Validates: Requirements 7.2</b></p>
     */
    @Property(tries = 100)
    void multiClientMode_createsSessionsEqualToVehiclesTimesClientCount(
            @ForAll @IntRange(min = 1, max = 10) int clientCount,
            @ForAll("vehicleSets") Set<Vehicle> vehicles) {

        orchestrator.configureMultiClient(clientCount);
        orchestrator.startControlledMode(vehicles);

        Map<String, ConsumptionSession> sessions = orchestrator.getActiveSessions();

        int expectedSessions = vehicles.size() * clientCount;
        assertEquals(expectedSessions, sessions.size(),
                "Expected vehicles(" + vehicles.size() + ") * clientCount(" + clientCount +
                        ") = " + expectedSessions + " sessions, but got " + sessions.size());
    }

    /**
     * For any configured number of clients (1-10) and any set of vehicles,
     * when startControlledMode is called, each vehicle SHALL have exactly
     * clientCount sessions (one per simulated client).
     *
     * <p><b>Validates: Requirements 7.2</b></p>
     */
    @Property(tries = 100)
    void multiClientMode_eachVehicleHasExactlyClientCountSessions(
            @ForAll @IntRange(min = 1, max = 10) int clientCount,
            @ForAll("vehicleSets") Set<Vehicle> vehicles) {

        orchestrator.configureMultiClient(clientCount);
        orchestrator.startControlledMode(vehicles);

        Map<String, ConsumptionSession> sessions = orchestrator.getActiveSessions();

        // Group sessions by vehicle ID
        Map<String, List<ConsumptionSession>> sessionsByVehicle = sessions.values().stream()
                .collect(Collectors.groupingBy(s -> s.getVehicle().getId()));

        // Each vehicle must have exactly clientCount sessions
        for (Vehicle vehicle : vehicles) {
            List<ConsumptionSession> vehicleSessions = sessionsByVehicle.get(vehicle.getId());
            assertNotNull(vehicleSessions,
                    "Vehicle " + vehicle.getId() + " should have sessions");
            assertEquals(clientCount, vehicleSessions.size(),
                    "Vehicle " + vehicle.getId() + " should have exactly " + clientCount +
                            " sessions, but had " + vehicleSessions.size());
        }
    }

    /**
     * For any configured number of clients (1-10) and any set of vehicles,
     * when startControlledMode is called, each session SHALL have a distinct
     * client ID, ensuring independent connections per client.
     *
     * <p><b>Validates: Requirements 7.2</b></p>
     */
    @Property(tries = 100)
    void multiClientMode_eachVehicleHasDistinctClientIds(
            @ForAll @IntRange(min = 1, max = 10) int clientCount,
            @ForAll("vehicleSets") Set<Vehicle> vehicles) {

        orchestrator.configureMultiClient(clientCount);
        orchestrator.startControlledMode(vehicles);

        Map<String, ConsumptionSession> sessions = orchestrator.getActiveSessions();

        // Group sessions by vehicle ID
        Map<String, List<ConsumptionSession>> sessionsByVehicle = sessions.values().stream()
                .collect(Collectors.groupingBy(s -> s.getVehicle().getId()));

        // For each vehicle, all client IDs must be distinct
        for (Vehicle vehicle : vehicles) {
            List<ConsumptionSession> vehicleSessions = sessionsByVehicle.get(vehicle.getId());
            assertNotNull(vehicleSessions,
                    "Vehicle " + vehicle.getId() + " should have sessions");

            Set<String> clientIds = vehicleSessions.stream()
                    .map(ConsumptionSession::getClientId)
                    .collect(Collectors.toSet());

            assertEquals(clientCount, clientIds.size(),
                    "Vehicle " + vehicle.getId() + " should have " + clientCount +
                            " distinct client IDs, but had " + clientIds.size() +
                            ". Client IDs: " + clientIds);
        }
    }

    /**
     * For any configured number of clients (1-10) and any set of vehicles,
     * when startControlledMode is called, the clientPool.createConnection SHALL
     * be called vehicles.size() * clientCount times (once per vehicle-client pair),
     * demonstrating independent WebSocket connections.
     *
     * <p><b>Validates: Requirements 7.2</b></p>
     */
    @Property(tries = 100)
    void multiClientMode_createsIndependentWebSocketConnectionPerVehicleClientPair(
            @ForAll @IntRange(min = 1, max = 10) int clientCount,
            @ForAll("vehicleSets") Set<Vehicle> vehicles) {

        orchestrator.configureMultiClient(clientCount);
        orchestrator.startControlledMode(vehicles);

        int expectedConnections = vehicles.size() * clientCount;

        // Verify the total number of createConnection calls
        verify(clientPool, times(expectedConnections))
                .createConnection(any(Vehicle.class), anyString());

        // Verify each vehicle-client pair gets its own connection call
        for (Vehicle vehicle : vehicles) {
            for (int i = 1; i <= clientCount; i++) {
                String expectedClientId = "client_" + i;
                verify(clientPool).createConnection(eq(vehicle), eq(expectedClientId));
            }
        }
    }

    /**
     * For any configured number of clients (1-10) and any set of vehicles,
     * all created sessions SHALL be active (indicating live independent connections).
     *
     * <p><b>Validates: Requirements 7.2</b></p>
     */
    @Property(tries = 100)
    void multiClientMode_allSessionsAreActive(
            @ForAll @IntRange(min = 1, max = 10) int clientCount,
            @ForAll("vehicleSets") Set<Vehicle> vehicles) {

        orchestrator.configureMultiClient(clientCount);
        orchestrator.startControlledMode(vehicles);

        Map<String, ConsumptionSession> sessions = orchestrator.getActiveSessions();

        for (ConsumptionSession session : sessions.values()) {
            assertTrue(session.isActive(),
                    "Session " + session.getSessionId() + " for vehicle " +
                            session.getVehicle().getId() + " (client: " + session.getClientId() +
                            ") must be active");
        }
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

    private Arbitrary<String> vehicleIds() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(4)
                .ofMaxLength(12)
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
                "fleet-management"
        );
    }
}
