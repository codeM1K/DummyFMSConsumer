package com.fms.consumer.service;

import com.fms.consumer.integration.WebSocketClientPool;
import com.fms.consumer.model.ConsumptionMode;
import com.fms.consumer.model.ConsumptionSession;
import com.fms.consumer.model.Vehicle;
import com.fms.consumer.model.VehicleStatus;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for individual vehicle selection in Controlled Mode.
 *
 * <p><b>Validates: Requirements 5.1</b></p>
 *
 * <p>Property 11: Individual Vehicle Selection —
 * "For any vehicle in the discovered vehicle list, the user SHALL be able to select
 * that vehicle through the UI for controlled consumption."</p>
 *
 * <p>This test validates that for any set of vehicles passed to startControlledMode(),
 * the ConsumptionOrchestrator creates active sessions for all of them.</p>
 */
class IndividualVehicleSelectionPropertyTest {

    private WebSocketClientPool clientPool;
    private MetricsCollector metricsCollector;
    private DiscoveryService discoveryService;
    private ConfigurationService configService;
    private ConsumptionOrchestrator orchestrator;

    @BeforeTry
    void setUp() {
        clientPool = mock(WebSocketClientPool.class);
        metricsCollector = mock(MetricsCollector.class);
        discoveryService = mock(DiscoveryService.class);
        configService = mock(ConfigurationService.class);

        when(configService.getSimulationMaxClients()).thenReturn(100);
        when(clientPool.createConnection(any(Vehicle.class), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        orchestrator = new ConsumptionOrchestrator(
                clientPool, metricsCollector, discoveryService, configService);
    }

    /**
     * For any non-empty set of vehicles passed to startControlledMode(),
     * every vehicle SHALL have a corresponding entry in getActiveSessions().
     *
     * <p><b>Validates: Requirements 5.1</b></p>
     */
    @Property(tries = 100)
    void allSelectedVehicles_haveActiveSessionsAfterStartControlledMode(
            @ForAll("vehicleSets") Set<Vehicle> vehicles) {

        orchestrator.startControlledMode(vehicles);

        Map<String, ConsumptionSession> activeSessions = orchestrator.getActiveSessions();

        // Extract vehicle IDs from active sessions
        Set<String> sessionVehicleIds = activeSessions.values().stream()
                .map(session -> session.getVehicle().getId())
                .collect(Collectors.toSet());

        // Every input vehicle must have a corresponding session
        for (Vehicle vehicle : vehicles) {
            assertTrue(sessionVehicleIds.contains(vehicle.getId()),
                    "Vehicle '" + vehicle.getId() + "' should have an active session, " +
                            "but was not found in active sessions. Active vehicle IDs: " + sessionVehicleIds);
        }
    }

    /**
     * For any non-empty set of vehicles, the number of active sessions SHALL equal
     * the number of vehicles (with default client count of 1).
     *
     * <p><b>Validates: Requirements 5.1</b></p>
     */
    @Property(tries = 100)
    void sessionCount_equalsVehicleCount_withSingleClient(
            @ForAll("vehicleSets") Set<Vehicle> vehicles) {

        orchestrator.startControlledMode(vehicles);

        Map<String, ConsumptionSession> activeSessions = orchestrator.getActiveSessions();

        assertEquals(vehicles.size(), activeSessions.size(),
                "Number of active sessions should equal the number of selected vehicles. " +
                        "Vehicles: " + vehicles.size() + ", sessions: " + activeSessions.size());
    }

    /**
     * For any vehicle in a set passed to startControlledMode(), the corresponding
     * session SHALL be in CONTROLLED mode.
     *
     * <p><b>Validates: Requirements 5.1</b></p>
     */
    @Property(tries = 100)
    void allSessions_areInControlledMode(
            @ForAll("vehicleSets") Set<Vehicle> vehicles) {

        orchestrator.startControlledMode(vehicles);

        Map<String, ConsumptionSession> activeSessions = orchestrator.getActiveSessions();

        for (ConsumptionSession session : activeSessions.values()) {
            assertEquals(ConsumptionMode.CONTROLLED, session.getMode(),
                    "Session for vehicle '" + session.getVehicle().getId() +
                            "' should be in CONTROLLED mode but was: " + session.getMode());
        }
    }

    /**
     * For any single vehicle selected individually, it SHALL have exactly one
     * session entry in getActiveSessions().
     *
     * <p><b>Validates: Requirements 5.1</b></p>
     */
    @Property(tries = 100)
    void singleVehicleSelection_createsExactlyOneSession(
            @ForAll("singleVehicle") Vehicle vehicle) {

        Set<Vehicle> singleSet = Set.of(vehicle);

        orchestrator.startControlledMode(singleSet);

        Map<String, ConsumptionSession> activeSessions = orchestrator.getActiveSessions();

        assertEquals(1, activeSessions.size(),
                "Selecting a single vehicle should create exactly one session");

        ConsumptionSession session = activeSessions.values().iterator().next();
        assertEquals(vehicle.getId(), session.getVehicle().getId(),
                "The session should be for the selected vehicle");
    }

    /**
     * Generates non-empty sets of vehicles with unique IDs (1 to 20 vehicles).
     */
    @Provide
    Arbitrary<Set<Vehicle>> vehicleSets() {
        return Arbitraries.integers().between(1, 20).flatMap(size ->
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10)
                        .set().ofSize(size)
                        .map(ids -> ids.stream()
                                .map(id -> new Vehicle(id, "Vehicle-" + id, "realm-1", VehicleStatus.ACTIVE))
                                .collect(Collectors.toSet()))
        );
    }

    /**
     * Generates a single vehicle with a random ID.
     */
    @Provide
    Arbitrary<Vehicle> singleVehicle() {
        return Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10)
                .map(id -> new Vehicle(id, "Vehicle-" + id, "realm-1", VehicleStatus.ACTIVE));
    }
}
